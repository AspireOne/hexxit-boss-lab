# The Hexbound Sovereign

An aerial boss for the local Hexxit lab. Kill it through ordinary combat. Its attacks lock before they execute, so changing direction after a warning matters. It remains vulnerable throughout the fight.

## Play

Run `.\lab.ps1 play`, open **Kratom - Boss Lab**, and choose Easy, Normal, or Hard. Find open ground with plenty of airspace, then use `/bosslab sovereign`. It appears ahead of you and begins pursuing after three seconds.

- `/bosslab status`: health, phase, action, and loaded lab entity count.
- `/bosslab clear`: immediately remove loaded lab bosses and practice targets in this dimension, without rewards.
- `/bosslab spawn`: summon the original harmless practice target.

Summoning saves a character checkpoint for [quick rematches](../practice.md) with `/bosslab retry`. Only one lab boss, Sovereign or [Warden](warden.md), can be loaded per dimension. It does not spawn naturally. The original instance and saves are never used for deployment.

## Fight

The Sovereign has **3,600 health** and three phases, starting at full health, 2,400 health, and 1,200 health. Later phases pursue faster and shorten attack preparation. Recovery windows remain available; the EXPOSED label identifies a good time to close in.

| Attack | Warning and response | Raw damage |
| --- | --- | --- |
| Hex Lance | Purple corridor: move sideways after it locks. Range up to 64 blocks. | 48 magic |
| Ruin Rush | Pink corridor: leave the route before its charge. Range up to 40 blocks. | 100 physical |
| Crown Break | Orange sphere: move beyond its 18-block radius or behind solid cover. | 64 magic |

Its warnings share the Warden's [locked style](warden.md#reading-its-state): they fill with heat as the attack approaches and flash as it releases. The sphere also marks where it meets the ground.

Lance and blast bypass ordinary armour defence. Applicable Protection enchantments, potion effects, and Forge damage events still apply. Rush uses normal physical damage handling. Phoenix Feathers retain their resurrection behaviour. Damage numbers are before mitigation and Minecraft difficulty scaling.

The boss alternates lance and rush, with a blast on every third attack when you are within 20 blocks. Fast lateral movement within that range also prompts a blast at the next attack choice, so continuously circling and shooting is dangerous. It never chains two blasts back-to-back. Crown Break still gives its full warning (36 ticks in phase one, 32 in phase three), followed by a recovery window. Break out radially, change altitude beyond the sphere, or take cover.

During pursuit it closes to 4.5 blocks and flanks at close range rather than remaining stationary. Attack preparation and recovery remain stationary so warning geometry stays reliable and melee openings remain available. It predicts recent movement before locking its direction. No attack redirects after its warning starts. A charge can hit you only once.

It floats through terrain and cannot be trapped by knockback. Solid cover blocks its attacks; it never breaks terrain. It ignores creative or otherwise invulnerable players. Leaving its 128-block engagement range for 15 seconds of loaded simulation restores its health. Peaceful removes it without rewards.

A player kill awards one Nether Star and 150 experience through normal loot handling. Other installed mods may contribute their own drops. Saving preserves its health; reopening the world cancels an unfinished attack and grants a new preparation period.

## Implementation

Production behaviour and rendering live in the `sovereign` feature package. Combat is server-owned; health, action, timer, and locked geometry synchronize through the legacy DataWatcher. Combat constants are the tuning surface; there is no separate encounter framework or configuration UI. It is an early prototype that predates the [encounter design rules](../encounter-design.md): its warnings lock from the start rather than tracking, and it still shows text labels.

## Verification

`.\lab.ps1 smoke-boss` runs the combat scenario across two launches. It exercises real sword and Crystal Bow attacks; lance, rush, and blast hits; dodges and cover; enchanted armour and Phoenix Feather resurrection; phase thresholds; disengagement recovery; client synchronization; save and reload; loot; Peaceful; and administrative cleanup. Circling regressions exercise clockwise close movement, counterclockwise wider movement, a vertical orbit, and escaping after the blast warning, with unconstrained boss pursuit. Continued circles are hit; deliberate escape is not. See [Testing](../testing.md) for how scenarios run.
