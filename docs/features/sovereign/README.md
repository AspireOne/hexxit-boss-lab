# The Hexbound Sovereign

An aerial boss for the local Hexxit lab. Kill it through ordinary combat. Its attacks lock before they execute, so changing direction after a warning matters. It remains vulnerable throughout the fight.

## Play

Run `.\lab.ps1 play`, open **Kratom - Boss Lab**, and choose Easy, Normal, or Hard. Find open ground with plenty of airspace, then use `/bosslab sovereign`. It appears ahead of you and begins pursuing after three seconds.

- `/bosslab status`: health, phase, action, and loaded lab entity count.
- `/bosslab clear`: immediately remove loaded lab bosses and practice targets in this dimension, without rewards.
- `/bosslab spawn`: summon the original harmless practice target.

Only one Sovereign can be summoned per loaded dimension. It does not spawn naturally. The original instance and saves are never used for deployment.

## Fight

The Sovereign has **1,200 health** and three phases, starting at full health, 800 health, and 400 health. Later phases pursue faster and shorten attack preparation. Recovery windows remain available; the EXPOSED label identifies a good time to close in.

| Attack | Warning and response | Raw damage |
| --- | --- | --- |
| Hex Lance | Purple corridor: move sideways after it locks. Range up to 64 blocks. | 48 magic |
| Ruin Rush | Pink corridor: leave the route before its charge. Range up to 40 blocks. | 100 physical |
| Crown Break | Orange sphere: move beyond its 12-block radius or behind solid cover. | 36 magic |

Lance and blast bypass ordinary armour defence. Applicable Protection enchantments, potion effects, and Forge damage events still apply. Rush uses normal physical damage handling. Phoenix Feathers retain their resurrection behaviour. Damage numbers are before mitigation and Minecraft difficulty scaling.

The boss alternates lance and rush, with a close-range blast on every third attack when you are within 14 blocks. It predicts recent movement before locking its direction. No attack redirects after its warning starts. A charge can hit you only once.

It floats through terrain and cannot be trapped by knockback. Solid cover blocks its attacks; it never breaks terrain. It ignores creative or otherwise invulnerable players. Leaving its 128-block engagement range for 15 seconds of loaded simulation restores its health. Peaceful removes it without rewards.

A player kill awards one Nether Star and 150 experience through normal loot handling. Other installed mods may contribute their own drops. Saving preserves its health; reopening the world cancels an unfinished attack and grants a new preparation period.

## Implementation and verification

Production behaviour and rendering live in the `sovereign` feature package. Combat is server-owned; health, action, timer, and locked geometry synchronize through the legacy DataWatcher. Combat constants are the tuning surface; there is no separate encounter framework or configuration UI.

Run `.\lab.ps1 smoke` for the original foundation scenario and `.\lab.ps1 smoke-boss` for combat integration. Close Minecraft first and leave the game untouched while the harness runs.

The combat scenario takes a complete snapshot of the current lab save before launching. It exercises real sword and Crystal Bow attacks; lance, rush, and blast hits; dodges and cover; enchanted armour and Phoenix Feather resurrection; phase thresholds; disengagement recovery; client synchronization; save/reload; loot; Peaceful; and administrative cleanup. Geometry probes cancel damage at the Forge boundary after observing delivery. Equipment probes allow damage through the real mod pipeline.

Both successful and failed runs restore the exact pre-test save once Minecraft has exited. Local evidence under `.lab/runs/<run>/` includes screenshots, phase results, logs, `world-before`, and the exercised `world-after`. Restoration is confirmed by `world-restored.json`; a passing `summary.json` additionally requires successful assertions. Snapshots contain the whole save and can use substantial disk space.

If Minecraft cannot exit, the runner refuses to replace the live save. Close that lab session, preserve the run directory, and recover from its `world-before` snapshot using `restore_smoke_world` in `scripts/lab.py` after validating the lab and stopped-process guard. Do not use `reset-world` to recover current progress: that command restores the older foundation baseline.

Minecraft 1.5.2's ray tracer mutates its input vector. Production code copies vectors at that boundary so cover checks cannot corrupt locked warnings or collision geometry. Combat remains server-owned, with tests against the actual pack rather than a simulated Minecraft API.

Balance remains subject to the first human fight. Automated damage measurements establish equipment compatibility, not how difficult or enjoyable the fight feels.
