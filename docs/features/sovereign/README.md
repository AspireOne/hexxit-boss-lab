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

The implementation plan calls for the existing foundation smoke plus a separate `.\lab.ps1 smoke-boss` scenario: hit/dodge/cover behaviour, actual equipment and resurrection, phase transitions, synchronization, persistence, death, and cleanup. Runtime evidence belongs in `.lab/runs/`. See the verification record once runtime acceptance is complete. Balance remains subject to the first human fight.
