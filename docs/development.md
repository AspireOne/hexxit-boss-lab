# Development

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
| `.\lab.ps1 gallery` | Photograph both bosses' poses, warnings, and effects in one game session, then restore the exact pre-test lab save. |
| `.\lab.ps1 smoke-practice` | Test in-game retry and real death/respawn recovery, then restore the pre-test lab save. |
| `.\lab.ps1 reset-world` | Snapshot current lab progress and restore the original lab baseline. |
| `.\lab.ps1 rollback` | Restore the previous deployed mod artifact. This does not restore the world. |

Close Minecraft before setup, deployment, rollback, or reset. Process checks refuse these operations when Minecraft is running, inspection fails, or a Java process's command line cannot be read. Gradle's Java process is allowed. Commands serialize local operations through `.lab/operation.lock`.

The PowerShell entry point delegates filesystem and process orchestration to Python's standard library. Gradle owns compilation and build checks. The lab workflow has no Python package dependencies; only the [texture painter](bosses/warden.md#rendering) needs Pillow.

How the smoke runs and other scenarios work, and how to write new ones, is in [Testing](testing.md).

## Toolchain and layout

- Windows, PowerShell 7 (`pwsh`), Python 3.13+, Git, and an existing logged-in Prism installation.
- Gradle **7.6.4**, downloaded through the checked-in wrapper with its SHA-256 verified.
- Voldeloom **2.4**, Minecraft **1.5.2**, Forge **7.8.1.737**.
- JDK **11** compilation with `--release 6`; the compiler rejects newer Java APIs and language features. Gradle can run on JDK 11 or 17.
- Game runtime: the original Prism instance's existing Java 8 configuration.
- SpotBugs **4.8.6** / Gradle plugin **5.2.5**, Spotless **6.25.0**, Google Java Format **1.17.0**.

Initial builds need network access to retrieve dependencies. Subsequent builds reuse Gradle caches. Do not update these legacy dependencies during unrelated changes.

Production code lives under `src/main/java/dev/hexxitlab`, one feature package per boss. Shared strike geometry is in `BossCombat` and warning drawing in `Telegraphs`. Start new encounter behaviour in a feature package and reuse the existing registration and logging entry point. Add abstractions only when a second concrete use needs them.

The separate `src/harness` source set contains the runtime test mod. Its JAR is installed only for smoke runs; the normal distribution must contain no harness classes. The compile-only development JARs ending in `-dev.jar` are not suitable for installation. Voldeloom remaps release artifacts to the names used by Forge at runtime.

Each release embeds `hexxitlab-build.properties` with the project version, full Git revision, and dirty-worktree flag. Build artifacts use stable entry ordering and timestamps. Uncommitted builds remain usable and are explicitly identified as dirty.

Gradle writes release and harness manifests under `build/artifacts/`. The workflow takes artifact paths from these outputs, verifies their SHA-256, and checks the release's embedded identity. Old versioned JARs left in `build/libs/` are not selected by filename.

### Legacy integration requirements

These were found by running in the real pack and are easy to break:

- `@NetworkMod` is required for FML's custom entity spawn packets in this Forge version.
- Harness remapping must include the production entity hierarchy, so inherited Minecraft members receive their release names. Production classes are stripped from the final harness JAR afterwards.
- Minecraft 1.5.2's ray tracer mutates its input vector. Copy vectors at that boundary (`BossCombat.trace`), or cover checks corrupt locked warnings.
- `Vec3.subtract(b)` returns `b - this`, the reverse of what its name suggests.
- The `DataWatcher` has no float type; `BossCombat` stores floats as their integer bits.
- A mod entity that moves on its first server tick is drawn offset by that step on clients. FML's spawn packet carries the moved position, and the tracker also sends the step as a relative move. The offset lasts until the tracker's forced resync, about every 20 seconds, so bosses stand still on their first tick.
- `IntegratedServer` copies the client's difficulty option every tick, so a world's difficulty cannot be set directly. On Hard, mob damage arrives ×1.5 before any Forge event observes it.

## Instance and save boundaries

Setup copies **Hexxit 1.0.11 OptiFine** to **Hexxit Boss Lab** under Prism's instances directory. It copies its mods, coremods, configuration, resources, jar patches, and Java settings. Writable game files are independent copies. Screenshots, logs, and unrelated saves are excluded.

The source save is **Kratom - Inventory restored 2026-10-15**, whose internal `LastPlayed` timestamp was newest when selected. The copy's directory and internal display name are **Kratom - Boss Lab**. The NBT edit changes only `Data.LevelName`; inventory and all other saved fields retain their original bytes.

Every `apply` turns off Damage Indicators' health portrait in the lab instance's configuration. When the portrait's last target leaves the world, it returns each frame without popping the OpenGL stacks it pushed. Full stacks never drain, so every entity drawn afterwards jitters until the game restarts. The original instance keeps the portrait and the bug.

The instance marker and `.lab/instance.json` identify the managed destination. Operations reject unrecognized destinations and linked paths. The lab sets `-Dhexxitlab.enabled=true` to enable its commands. The development harness additionally requires the instance marker and an explicit smoke request.

Local state stays outside Git:

- `.lab/baseline/`: the initial test world, with a hash manifest checked before restoration.
- `.lab/snapshots/`: previous lab worlds retained by `reset-world`.
- `.lab/artifacts/`: installed release JARs indexed by SHA-256, plus current/previous deployment identities.
- `.lab/runs/`: runtime results, screenshots, and mod/configuration fingerprints.

Reset restores the whole save, including its player inventory. It never refreshes the baseline from the live source. Deployment and world reset are separate operations.

Deployment records a pending replacement before changing the installed JAR. If the metadata update fails, it restores the previous JAR; an interrupted operation is recovered on the next `apply` or `rollback`. If storage prevents recovery, resolve that failure and retry with Minecraft stopped. Preserve `deployment-pending.json` until recovery finishes. An installed JAR that unexpectedly differs from recorded metadata is rejected rather than overwritten.

If setup or reset is interrupted, inspect its staging directory rather than deleting it blindly. If `.lab/operation.lock` remains after a terminated command, inspect its recorded process ID, confirm that process has ended, then remove only that lock. Never remove an active operation's lock.

Avoid capturing or printing Prism account launch arguments, which can contain credentials.

## Checks and commits

The pre-commit hook automatically formats Java, restages the files already selected for the commit, and runs `check`. It refuses partially staged files so formatting cannot silently pull unrelated hunks into a commit. Stage complete files and use conventional commits at working milestones.

Checks cover:

- Legacy Java API and language compatibility, with compiler warnings treated as failures.
- SpotBugs findings in both production and harness code. Two exact exclusions document Forge's injected proxy field and legacy command comparison semantics.
- Java 6 class-file compatibility for shipping code.
- Destination rejection, artifact preservation on copy/build failure, retained rollback artifacts, world restoration boundaries, and precise NBT renaming.

There is no hosted CI: the game, the modpack, and the lab instance exist only on the developer's machine. Deployment independently runs the same checks, even if a Git hook was bypassed. Static checks do not establish runtime compatibility; run the [scenarios](testing.md) when registration, rendering, persistence, or Forge interactions change.
