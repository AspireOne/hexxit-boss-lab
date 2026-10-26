# Testing

Bosses are tested in the real game: the actual Hexxit pack, launched through Prism, driven by a separate harness mod. Static checks prove compatibility with legacy Java, not that anything works in Minecraft. The commands are listed in [Development](development.md#everyday-workflow).

## Running scenarios

1. Inspect `git status`, the lab marker, and whether Minecraft is running. Never stop an unrelated game session; if the developer is playing in the lab, wait.
2. Run `.\lab.ps1 check` while iterating. Commit hooks own automatic formatting.
3. Run the scenarios that cover the change. Each takes about 3 to 4 minutes and launches the game. Other desktop work can continue. The harness never grabs the mouse, and the runner moves the game's windows off-screen for the whole run. Windows composites every window, so the game still renders and its screenshots match on-screen runs. Prism's own windows are never moved, because Prism remembers their positions for normal use. For 90 seconds after each launch, the runner also returns foreground focus taken by Prism or Minecraft to the previous window, so typing never lands in the hidden game. Windows lets only the process that sent the latest input change the foreground, so when it refuses, the runner taps Alt, which Minecraft does not use, and tries again. To see a run, look at its screenshots. Prism's low-memory warning blocks a launch until someone answers it.
4. Inspect the results in the reported `.lab/runs/<run>/` directory. A timeout, crash, missing result, wrong run identifier, or failed assertion means failure. Each scenario logs a `PASS` line per case.
5. For visual changes, look at the screenshots in the run directory, or run `.\lab.ps1 gallery`. A passing scenario does not prove that something looks right.

A boss's scenario is described in its guide: [Warden](bosses/warden.md#verification), [Sovereign](bosses/sovereign.md#verification), [practice](practice.md#verification).

## How the harness works

The harness enters the test world from the title screen. Only an explicitly requested smoke run auto-enters the world. Scenarios use tick conditions and bounded timeouts, rather than assuming that loading completes after a fixed delay.

The original target smoke checks a harmless practice target: spawning through the real command, client health synchronization, player damage, persistence across a restart, lethal damage, and cleanup that spares unrelated entities. It advances the lab world's normal simulation; use `reset-world` when a fresh baseline is needed.

Combat, practice, and gallery scenarios snapshot the whole current lab save before launching, and restore it exactly afterwards, whether they pass or fail. Combat scenarios pin the player to scripted positions in open air; the Warden's also builds a temporary stone platform for its grounded fight. Geometry probes cancel boss damage at the Forge boundary after recording it; equipment probes let damage through the real pipeline.

Shutdown order matters in this legacy pack: stop the integrated server while the client connection remains alive, disconnect after server save hooks finish, drain pending file writes (`ThreadedFileIOBase`), publish the result, then request normal Minecraft shutdown. Clearing the connection earlier raced Infernal Mobs during Dimensional Doors chunk activity, and calling `System.exit` inside the tick callback hung Java's Windows shutdown hooks. The runner also checks for crashes produced during saving and exit.

The harness also guards OpenGL state. Minecraft reports GL errors once per frame, which cannot tell the lab's renderers from the pack's. The harness wraps the lab's boss renderers and checks for errors before and after each one draws. It also fails the run if the matrix or attribute stacks are deeper at the start of any frame than at the first. A leaked stack fills up and never drains, and entities then visibly jitter, while positions and scenarios still look correct.

During a run the harness disables pause-on-focus-loss and sets the client difficulty to Normal, restoring both preferences at shutdown. Without that, a developer who plays on Hard gets ×1.5 boss damage in every measurement.

The copied character changes between play sessions. Scenarios locate equipment by item type anywhere in the inventory rather than by slot, and supply consumed pack items, such as Phoenix Feathers, from the item registry. A save quit on the death screen loads a dead character with an empty inventory; the harness clicks Respawn and waits 3 seconds, so a practice checkpoint can restore the loadout before the scenario starts.

Screenshots use Minecraft's own screenshot API, so they are repeatable without desktop automation. They are 854 × 480, the lab window's size, and some other mods draw HUD elements even with the GUI hidden.

## Recovering an interrupted run

If Minecraft cannot exit, the runner refuses to replace the live save. Close that lab session and preserve the run directory. Recover from its `world-before` snapshot using `restore_smoke_world` in `scripts/lab.py`, after validating the lab, the stopped-process guard, and `world-before-hashes.json`. Inspect any `.boss-restore` staging directory before retrying an interrupted restore, and keep both snapshots until recovery succeeds. Do not use `reset-world` to recover current progress: that restores the older baseline. Snapshots contain the whole save and can use substantial disk space.

## Writing scenarios

Lessons from scenarios that passed or failed for the wrong reason:

- **Reproduce the tactic, not an approximation.** A circling fixture must follow the moving boss and assert that the player stays inside the warned area; an orbit around a fixed world point can escape by accident and proves nothing.
- **Answer when the warning locks.** Strikes track the player until they lock, so a scripted dodge made earlier is simply followed. Use `Warden.isCommitted()` to time answers.
- **Check that the answer is geometrically valid.** After a missed Leap lands ahead on a strafing player's path, reversing moves the player straight along the following Chain Hook's line; only a sidestep dodges it. When a dodge fails, first ask whether the dodge was possible.
- **Prove exact maths with deterministic sources.** Pack weapons vary their damage per swing and can deliver damage outside the attack event. Use `DamageSource.causePlayerDamage` or a constructed arrow for exact values, and require only a clear effect from real weapons.
- **Hold the camera before capturing.** Camera rotation applies on the next frame; hold the framing for several frames. A camera placed beyond the platform falls, and the client keeps the previous shot's time of day until told otherwise. Capture each picture once, or a second capture overwrites brief effects such as afterglows.
- **Scripted movement is perfectly steady.** A real player's server positions arrive unevenly, so anything that reads player movement, such as leading, still needs a human fight.

Scenarios establish compatibility and scripted behaviour in the installed pack. They do not establish balance, how a fight feels, dedicated-server support, or pursuit across rough terrain, water, and caves.
