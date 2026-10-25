# Development foundation

## Everyday workflow

Run commands from the repository root in PowerShell:

| Command | Result |
| --- | --- |
| `.\lab.ps1 setup` | Install the local Git hook and create the isolated instance and world once. Repeating it preserves lab progress. |
| `.\lab.ps1 check` | Compile production and harness code, run SpotBugs and workflow regression tests, check formatting and production bytecode. |
| `.\lab.ps1 build` | Run checks and build the release JAR. |
| `.\lab.ps1 apply` | Build and install into the marked lab. Preserve the previous artifact. |
| `.\lab.ps1 play` | Apply and launch **Hexxit Boss Lab** through Prism. Select **Kratom - Boss Lab**. |
| `.\lab.ps1 smoke` | Apply, install the separate harness, and run two automated game sessions. |
| `.\lab.ps1 smoke-boss` | Run Sovereign combat integration in two game sessions, then restore the exact pre-test lab save. |
| `.\lab.ps1 smoke-warden` | Run Warden combat integration in two game sessions, then restore the exact pre-test lab save. |
| `.\lab.ps1 smoke-practice` | Test in-game retry and real death/respawn recovery, then restore the pre-test lab save. |
| `.\lab.ps1 reset-world` | Snapshot current lab progress and restore the original lab baseline. |
| `.\lab.ps1 rollback` | Restore the previous deployed mod artifact. This does not restore the world. |

Close Minecraft before setup, deployment, rollback, or reset. Process checks refuse these operations when Minecraft is running, inspection fails, or a Java process's command line cannot be read. Gradle's Java process is allowed. Commands serialize local operations through `.lab/operation.lock`.

The PowerShell entry point delegates filesystem and process orchestration to Python's standard library. Gradle owns compilation and build checks. There are no Python package dependencies.

## Toolchain and layout

- Windows, PowerShell 7 (`pwsh`), Python 3.13+, Git, and an existing logged-in Prism installation.
- Gradle **7.6.4**, downloaded through the checked-in wrapper with its SHA-256 verified.
- Voldeloom **2.4**, Minecraft **1.5.2**, Forge **7.8.1.737**.
- JDK **11** compilation with `--release 6`; the compiler rejects newer Java APIs and language features. Gradle can run on JDK 11 or 17.
- Game runtime: the original Prism instance's existing Java 8 configuration.
- SpotBugs **4.8.6** / Gradle plugin **5.2.5**, Spotless **6.25.0**, Google Java Format **1.17.0**.

Initial builds need network access to retrieve dependencies. Subsequent builds reuse Gradle caches. Do not update these legacy dependencies during unrelated changes.

Production code lives under `src/main/java/dev/hexxitlab`. Start new encounter behavior in a feature package and reuse the existing registration and logging entry point. Add abstractions only when a second concrete use needs them.

The separate `src/harness` source set contains the runtime test mod. Its JAR is installed only for smoke runs; the normal distribution must contain no harness classes. The compile-only development JARs ending in `-dev.jar` are not suitable for installation. Voldeloom remaps release artifacts to the names used by Forge at runtime.

Each release embeds `hexxitlab-build.properties` with the project version, full Git revision, and dirty-worktree flag. Build artifacts use stable entry ordering and timestamps. Uncommitted builds remain usable and are explicitly identified as dirty.

Gradle writes release and harness manifests under `build/artifacts/`. The workflow takes artifact paths from these outputs, verifies their SHA-256, and checks the release's embedded identity. Old versioned JARs left in `build/libs/` are not selected by filename.

## Instance and save boundaries

Setup copies **Hexxit 1.0.11 OptiFine** to **Hexxit Boss Lab** under Prism's instances directory. It copies its mods, coremods, configuration, resources, jar patches, and Java settings. Writable game files are independent copies. Screenshots, logs, and unrelated saves are excluded.

The source save is **Kratom - Inventory restored 2026-10-15**, whose internal `LastPlayed` timestamp was newest when selected. The copy's directory and internal display name are **Kratom - Boss Lab**. The NBT edit changes only `Data.LevelName`; inventory and all other saved fields retain their original bytes.

The instance marker and `.lab/instance.json` identify the managed destination. Operations reject unrecognized destinations and linked paths. The lab sets `-Dhexxitlab.enabled=true` to enable its commands. The development harness additionally requires the instance marker and an explicit smoke request.

Local state stays outside Git:

- `.lab/baseline/`: the initial test world, with a hash manifest checked before restoration.
- `.lab/snapshots/`: previous lab worlds retained by `reset-world`.
- `.lab/artifacts/`: installed release JARs indexed by SHA-256, plus current/previous deployment identities.
- `.lab/runs/`: runtime results and mod/configuration fingerprints.

Reset restores the whole save, including its player inventory. It never refreshes the baseline from the live source. Deployment and world reset are separate operations.

Deployment records a pending replacement before changing the installed JAR. If the metadata update fails, it restores the previous JAR; an interrupted operation is recovered on the next `apply` or `rollback`. If storage prevents recovery, resolve that failure and retry with Minecraft stopped. Preserve `deployment-pending.json` until recovery finishes. An installed JAR that unexpectedly differs from recorded metadata is rejected rather than overwritten.

If setup or reset is interrupted, inspect its staging directory rather than deleting it blindly. If `.lab/operation.lock` remains after a terminated command, inspect its recorded process ID, confirm that process has ended, then remove only that lock. Never remove an active operation's lock.

## Checks and commits

The pre-commit hook automatically formats Java, restages the files already selected for the commit, and runs `check`. It refuses partially staged files so formatting cannot silently pull unrelated hunks into a commit. Stage complete files and use conventional commits at working milestones.

Checks cover:

- Legacy Java API and language compatibility, with compiler warnings treated as failures.
- SpotBugs findings in both production and harness code. Two exact exclusions document Forge's injected proxy field and legacy command comparison semantics.
- Java 6 class-file compatibility for shipping code.
- Destination rejection, artifact preservation on copy/build failure, retained rollback artifacts, world restoration boundaries, and precise NBT renaming.

There is no hosted CI because the project is local-only. Deployment independently runs the same checks, even if a Git hook was bypassed. Runtime testing is required when registration, rendering, persistence, or Forge interactions change.

## Autonomous testing

1. Inspect `git status`, the lab marker, and whether Minecraft is running. Never stop an unrelated game session.
2. Run `.\lab.ps1 check` while iterating. Commit hooks own automatic formatting.
3. Run `.\lab.ps1 smoke` after changes affecting game integration. Do not interact with the game during the test; other work on the desktop can continue. The harness never grabs the mouse, and for 90 seconds after each launch the runner returns foreground focus taken by Prism or Minecraft to the previous window. Windows lets only the process that sent the latest input change the foreground, so when it refuses, the runner taps Alt, which Minecraft does not use, and tries again. Later focus changes are left alone, so clicking into the game to watch it stays possible.
4. Inspect both phase results in the reported `.lab/runs/<run>/` directory. A timeout, crash, missing result, wrong run identifier, or failed assertion means failure.
5. For visual changes, run `.\lab.ps1 play`, load **Kratom - Boss Lab**, and use `/bosslab spawn`. Inspect the target's model, texture, health label, and health changes after damage. Use `/bosslab clear` afterward and exit through Save and Quit.
6. Record the commands and observed result in the task report. A successful static check does not substitute for a runtime or visual check.

The harness enters the test world from the title screen. The create phase invokes the real spawn command, checks the server target and its client-side health, applies seven points of player-sourced damage, and confirms 33 health on both sides. It tags that entity for the current run, saves, and exits. The reload phase finds the same tag and health in the reopened world, checks lethal damage, and confirms cleanup leaves an unrelated sentinel entity alive. It removes its sentinel before saving and exiting.

Shutdown order matters in this legacy pack: stop the integrated server while the client connection remains alive, disconnect after server save hooks finish, drain pending file writes, publish the result, then request normal Minecraft shutdown. Keep that order when extending the harness. The runner also checks for crashes produced during saving and exit.

During a run the harness disables pause-on-focus-loss and sets the client difficulty to Normal, restoring both preferences at shutdown. The integrated server copies difficulty from client options every tick, so assigning a world's difficulty does not last; on Hard, mob damage arrives ×1.5 before any Forge event observes it. Scenarios locate equipment by item type rather than slot and supply consumed pack items from the item registry, because the copied character changes between play sessions.

The original target scenario retains the copied player's equipment and capabilities. It uses tick conditions and bounded timeouts, rather than assuming that loading completes after a fixed delay. Only an explicitly requested smoke run auto-enters the world. Target smoke advances the lab world's normal simulation; use `reset-world` when a fresh baseline is needed.

The harness also captures a screenshot through Minecraft's own screenshot API, after turning the player toward the damaged target. Inspect that PNG in the run directory to verify the model, texture, and health label. This is repeatable even when desktop automation is unavailable.

Use the Computer Use skill's `@oai/sky` Windows API for additional visual inspection when available. This session's native pipe was unavailable. Always identify the lab's actual Minecraft window before sending input. Avoid capturing or printing Prism account launch arguments, which can contain credentials.

The test establishes registration, synchronization, damage, persistence, and cleanup compatibility with this installed modpack. It does not establish boss balance, enjoyable combat, or dedicated-server support.

The [Sovereign](../sovereign/README.md) and [Warden](../warden/README.md) guides document each boss and its combat scenario; [practice rematches](../practice/README.md) have their own guide. Unlike the original target smoke, combat and practice smoke snapshot and restore the current lab save, including player inventory, capabilities, and progress.

See [the foundation verification record](verification.md) for the completed acceptance run and the legacy integration requirements it established.
