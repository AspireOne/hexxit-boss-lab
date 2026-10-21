# Foundation verification — 2026-10-21

Verified against the copied Hexxit 1.0.11 OptiFine instance: Minecraft 1.5.2, Forge 7.8.1.737, the original Java 8 runtime, and the complete installed modpack.

`lab.ps1 smoke` completed both game launches without intervention. Local evidence is retained under `.lab/runs/20261021-210511-65ddb46d/`: the mod/configuration fingerprints, phase results with revision metadata, game logs, screenshot, and passing summary.

Observed results:

- The release mod and separate harness loaded in the actual pack.
- `/bosslab spawn` produced a 40-health target, visible on the client.
- Seven points of player-sourced damage left 33 health on the server and client.
- The screenshot showed the humanoid texture and `Lab Target 33/40` label, alongside Damage Indicators.
- Reopening the world recovered the same run-tagged entity with 33 health.
- Lethal damage killed the target. `/bosslab clear` removed loaded targets while preserving an unrelated sentinel entity, which the harness then removed.
- Both phases saved, drained pending file writes, and exited normally. No new crash reports were produced.
- Compiler, SpotBugs, bytecode compatibility, and nine workflow regression tests passed.
- `reset-world` was exercised on the real lab; it restored the baseline and retained the previous lab state as a snapshot.
- Repeating `setup` preserved the instance and world. `rollback` restored the prior deployed JAR, verified by its retained SHA-256.

The runtime test used the staged implementation on top of commit `659a87f`; its embedded identity correctly reported a dirty worktree. The final milestone commit captures that implementation. Build identities distinguish subsequent artifacts without changing the tested game logic.

## Legacy integration details

The runtime test exposed three requirements that are now part of the foundation:

1. `@NetworkMod` is required for FML's custom entity spawn packets in this Forge version.
2. Harness remapping must include the production entity hierarchy so inherited Minecraft members receive their release names. Production classes are stripped from the final harness JAR afterward.
3. Stop the integrated server while the client connection remains available, then disconnect, drain `ThreadedFileIOBase`, publish results, and request normal Minecraft shutdown. Clearing the connection earlier raced Infernal Mobs during Dimensional Doors chunk activity. Calling `System.exit` inside the tick callback also hung Java's Windows shutdown hooks.

The Computer Use native pipe was unavailable. Visual verification used Minecraft's own screenshot API, which is included in the repeatable smoke workflow.
