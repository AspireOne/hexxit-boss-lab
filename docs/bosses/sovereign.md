# The Hexbound Sovereign

A floating hex-king that fights from the air with a beam, a charge, and a crown-shaped blast. Its warnings lock as soon as they appear, so get out of the warned area before the attack fires. It has no guard: it can be hurt throughout the fight, and most of all while it is exposed after each attack.

**Version:** v1 is sealed as the Git tag `sovereign-v1`. Everything after it is v2, still in development; this guide describes the current code. To play or compare v1, check out the tag.

## Play

Run `.\lab.ps1 play`, open **Kratom - Boss Lab**, and choose Easy, Normal, or Hard. Find open ground with plenty of airspace, then use `/bosslab sovereign`. It appears ahead of you and begins pursuing after three seconds.

- `/bosslab status`: health, phase, action, and loaded lab entity count.
- `/bosslab clear`: immediately remove loaded lab bosses and practice targets in this dimension, without rewards.
- `/bosslab spawn`: summon the original harmless practice target.

Summoning saves a character checkpoint for [quick rematches](../practice.md) with `/bosslab retry`. Only one lab boss, Sovereign or [Warden](warden.md), can be loaded per dimension. It does not spawn naturally. The original instance and saves are never used for deployment.

## Fight

The Sovereign has **3,600 health** and three phases, starting at full health, 2,400 health, and 1,200 health. Later phases pursue faster and shorten attack preparation. After every attack it is **exposed** for a moment: a good time to close in.

| Attack | Warning and response | Raw damage |
| --- | --- | --- |
| Hex Lance | Violet corridor: move sideways. Range up to 64 blocks. | 48 magic |
| Ruin Rush | Pink corridor: leave the route before its charge. Range up to 40 blocks. | 100 physical |
| Crown Break | Orange sphere: move beyond its 18-block radius or behind solid cover. | 64 magic |

Lance and blast bypass ordinary armour defence. Applicable Protection enchantments, potion effects, and Forge damage events still apply. Rush uses normal physical damage handling. Phoenix Feathers retain their resurrection behaviour. Damage numbers are before mitigation and Minecraft difficulty scaling.

The boss alternates lance and rush, with a blast on every third attack when you are within 20 blocks. Fast lateral movement within that range also prompts a blast at the next attack choice, so continuously circling and shooting is dangerous. It never chains two blasts back-to-back. Crown Break still gives its full warning (36 ticks in phase one, 32 in phase three), followed by a recovery window. Break out radially, change altitude beyond the sphere, or take cover.

During pursuit it closes to 4.5 blocks and flanks at close range rather than remaining stationary. Attack preparation and recovery remain stationary so warning geometry stays reliable and melee openings remain available. It predicts recent movement before locking its direction. No attack redirects after its warning starts. A charge can hit you only once.

It floats through terrain and cannot be trapped by knockback. Solid cover blocks its attacks; it never breaks terrain. It ignores creative or otherwise invulnerable players. Leaving its 128-block engagement range for 15 seconds of loaded simulation restores its health. Peaceful removes it without rewards.

A player kill awards one Nether Star and 150 experience through normal loot handling. Other installed mods may contribute their own drops. Saving preserves its health; reopening the world cancels an unfinished attack and grants a new preparation period.

## Reading its state

The Sovereign is a hooded, faceless king held together by hex magic. Its gauntlets, sceptre, crown, halo and crystal shards float free of its body, and where they are tells you what it is doing. Its runes glow in the colour of what comes next.

| State | What you see |
| --- | --- |
| Hunting | It hovers upright, sceptre beside its right hand, shards orbiting its waist, runes a cool violet. It leans into its flight and banks as it flanks. |
| Hex Lance | It turns to face the line of fire. The sceptre floats onto that line and spins up, both hands reach forward, and the shards ring the barrel. Light gathers in the sceptre's orb; its runes burn violet. |
| Ruin Rush | It rears back with its arms swept behind it, its robe flaring forward, and its shards form a spinning drill along the route. Its runes burn pink. |
| The charge | It flies head first behind its sceptre, the shards a drill at the tip, leaving a pink wake. |
| Crown Break | It rises and spreads its arms. The crown climbs above it, spins faster, and starts to part, while the halo widens. Its runes burn orange. |
| Exposed | It sinks low and slumps, hands hanging, crown askew, runes dark. Its chest plates swing open and bare a blazing core. Just before it recovers, it rises back into its hover. |
| Phase 2 | A second ring turns inside its halo, a fourth shard joins the orbit, and violet fissures begin to open across its armour and mask. |
| Phase 3 | The crown is broken into four drifting pieces, the mask is split open over burning eyes, the halo is missing tablets, and five shards orbit it. It twitches, and its runes run hot magenta. |
| Hit | It flinches and its runes flare, with sparks from its core. |
| Death | It rises and comes apart: the crown floats away, the hands and shards drift outwards, it flares white, and it bursts. |

Its warnings share the Warden's [locked style](warden.md#reading-its-state): they fill with heat as the attack approaches and flash as it releases. Each also carries its own mark inside the hit area: hexagonal rings spiral down a lance's line, chevrons race along a rush's route, and a turning hexagram lies on the ground under Crown Break, inside the ring where the sphere meets a standing body. A pulse of the attack's colour marks the moment each attack begins. The lance fires as a white-hot beam, the charge breaks and stops with rings of force, and Crown Break bursts in a white-hot sphere with a shockwave and a scorch below, throwing off gold.

## Implementation

Production behaviour and rendering live in the `sovereign` feature package. Combat is server-owned; health, action, timer, and locked geometry synchronize through the legacy DataWatcher. Combat constants are the tuning surface; there is no separate encounter framework or configuration UI. Its combat predates the [encounter design rules](../encounter-design.md): its warnings lock from the start rather than tracking, which makes it the main candidate for v2 work.

## Rendering

Rendering is client-only and reads the synchronized state; it never feeds back into combat. `SovereignModel` holds the rig and `SovereignPoses` the pose for each action, blended over a few ticks; a release snaps on from its attack's peak instead. During the Lance, Rush and charge it turns and tilts towards the locked aim rather than towards you, so its body lines up with the warning. The crown, halo and shards are drawn several times from one part each. It shares the Warden's [texture gotchas](warden.md#rendering): absolute texture paths, glow passes skipped during the red hurt flash, and full lightmap brightness for additive glows. Its fissures store their opening order in alpha, like the Warden's cracks, and an alpha test reveals them as it loses health.

Box UVs are packed for a 128 × 128 sheet and the textures are generated. After adding or resizing a box, run `python scripts/art/repack.py src/main/java/dev/hexxitlab/sovereign/SovereignModel.java`, then `python scripts/art/sovereign_paint.py` to repaint every layer. A new box also needs an entry in the painter's `MATERIAL` table. Its fixed seed makes repainting an unchanged model reproduce the committed images exactly.

## Verification

`.\lab.ps1 smoke-boss` runs the combat scenario across two launches. It exercises real sword and Crystal Bow attacks; lance, rush, and blast hits; dodges and cover; enchanted armour and Phoenix Feather resurrection; phase thresholds; disengagement recovery; client synchronization; save and reload; loot; Peaceful; and administrative cleanup. Circling regressions exercise clockwise close movement, counterclockwise wider movement, a vertical orbit, and escaping after the blast warning, with unconstrained boss pursuit. Continued circles are hit; deliberate escape is not. See [Testing](../testing.md) for how scenarios run.

`.\lab.ps1 gallery` also photographs the posed Sovereign: every action and phase from several angles by day and night, each warning early and late, the charge in flight, each release, hits, and death. Use it after visual changes.
