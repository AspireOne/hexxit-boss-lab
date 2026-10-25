# The Ashen Warden

A grounded, armoured duelist that commands a swarm of Cinders. Where the [Sovereign](../sovereign/README.md) tests dodging, the Warden tests positioning, target priority, and punishing commitment. Its front deflects your hits until it commits to a strike, so the fight is about baiting its attacks and getting around them.

## Play

Run `.\lab.ps1 play`, open **Kratom - Boss Lab**, and choose Easy, Normal, or Hard. Find a reasonably open area, then use `/bosslab warden`. It appears on the ground 12 blocks ahead, facing you, and engages after three seconds. `/bosslab status` and `/bosslab clear` work as for the Sovereign. Summoning saves a character checkpoint for [quick rematches](../practice/README.md); `/bosslab retry` summons a fresh Warden.

Only one lab boss can be loaded per dimension. The Warden does not spawn naturally.

## The guard

Hits that strike its front (within 70° of where it faces) deal a quarter of their damage, with a minimum of 1. An anvil clank confirms a guarded hit. Arrows are judged by where they strike, not where you stand. The guard drops completely during **EXPOSED** recoveries, and it shatters for good in phase three.

The intended rhythm: bait a strike, step out of its warning or roll inside and around it, then hit its back or punish the recovery.

## Combos

The Warden attacks in combos. Each step reads where you are at that moment, then locks its warning before the windup starts. No warning moves after it appears.

| Step | Chosen when | Warning | Raw damage |
| --- | --- | --- | --- |
| Cleave | You are in its front half, within 6 blocks | Orange wedge, 50° each side, 5.5 blocks | 30 |
| Backhand | You are behind it mid-combo | Orange wedge behind it, 100° each side, 5 blocks | 24 |
| Slam | Last step, you are within 8 blocks | Orange circle 3 blocks ahead, radius 3.5 | 50 |
| Leap | Last step and you are farther; or you are over 14 blocks away; or it has made no progress toward you for 2 seconds | Orange circle where you stood, radius 4 | 40 |
| Chain Hook | You are more than 3 blocks above it; or, from phase 2, 8 to 14 blocks away (at most every 10 seconds) | Chain corridor, up to 22 blocks | 12, then reels you in for a Slam |

Every combo ends with Slam or Leap, followed by an **EXPOSED** recovery. A missed Chain Hook also leaves it exposed briefly. Rolling behind a Cleave usually earns a Backhand next, so do not linger. Its sweeps and slams stop at solid cover; it never breaks terrain.

| | Phase 1 | Phase 2 | Phase 3 |
| --- | --- | --- | --- |
| Health | 4,000 to 2,400 | 2,400 to 1,000 | Below 1,000 |
| Combo length | 2 | 3 | 4 |
| Cleave / Slam warning (ticks) | 16 / 20 | 14 / 18 | 11 / 14 |
| Exposed after a finisher (ticks) | 50 | 40 | 30 |
| Walk speed (blocks per tick; sprinting is 0.28) | 0.22 | 0.26 | 0.30 |

## Harrowings and the swarm

At 2,400 health it begins **Kindle**: it is warded from all damage for 2 seconds, then raises three Cinders. Through phase two it keeps up to four alive, raising another every 8 seconds.

Cinders orbit you and dive one at a time, conducted by the Warden. A diving Cinder swells and flares bright yellow, with a line toward the point it locked. Step aside. A dive deals 8 magic damage and burns for 3 seconds. Cinders have 10 health: one sword hit or arrow kills one.

At 1,000 health it begins **Reclaim**: it is warded again and recalls every Cinder. The red sphere around it is the coming **Ash Nova**. It starts at a radius of 7 and grows by 3 for each Cinder that reaches the Warden, so shoot them down while they fly back. When the swarm is gone, the nova detonates after 1.5 seconds for 60 magic damage. Leave the sphere or put solid cover between you. The guard then shatters: phase three has no guard but the shortest warnings, the longest combos, and a walk faster than your sprint.

## Damage and rules

Blows bypass ordinary armour defence; Protection enchantments, potion effects, and Forge damage events still apply. The nova and Cinders deal magic damage. Raw numbers are before mitigation and Minecraft difficulty scaling: Hard multiplies mob damage by 1.5. Phoenix Feathers keep their resurrection behaviour.

It ignores creative and otherwise invulnerable players. Leaving its 128-block range for 15 seconds of loaded simulation restores its health and first phase, and dismisses its swarm. Peaceful removes it without rewards. A player kill awards one Nether Star and 200 experience through normal loot handling. Saving preserves its health and phase; reloading cancels an unfinished attack and gives a fresh 3-second delay. Cinders are never saved, so a reloaded phase-two Warden raises a new swarm.

## Implementation and verification

Behaviour and rendering live in the `warden` feature package. Combat is server-owned. Health, action, timer, phase, locked strike point and yaw, and nova radius synchronize through the legacy DataWatcher. Strike geometry and telegraph drawing are shared with the Sovereign through `BossCombat` and `Telegraphs`. Combat constants at the top of `Warden` and `Cinder` are the tuning surface.

The Warden walks with vanilla collision, step-up, and gravity: its AI runs in `updateEntityActionState`, which vanilla calls on the server just before travel. Leaps are scripted flights through terrain onto the first solid surface below the locked target. Bosses set `ignoreFrustumCheck`, because their warnings extend far beyond their bodies and would otherwise vanish when you look away from them.

`.\lab.ps1 smoke-warden` runs the combat scenario across two launches on a temporary stone platform in daylight. It snapshots the current lab save and restores it afterwards. It checks the guard against deterministic melee and arrow damage plus a real pack weapon. It covers each combo branch with hits and dodges, the hook reel into a Slam, phase-three combos and warnings, Kindle warding, Cinder dive hits and dodges, and Reclaim with a Cinder shot down in flight. It also covers nova radius, hit, and escape; the shattered guard; disengagement; persistence; loot; the one-boss rule; retry; clear; and Peaceful. Geometry probes cancel boss damage at the Forge boundary after recording it. Screenshots of each warning are saved in the run directory.

See the [verification record](verification.md) for accepted runs. Balance remains subject to human fights.
