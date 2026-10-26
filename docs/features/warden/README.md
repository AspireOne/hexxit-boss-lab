# The Ashen Warden

A grounded, armoured duelist that commands a swarm of Cinders. Where the [Sovereign](../sovereign/README.md) tests dodging, the Warden tests positioning, target priority, and punishing commitment. Its front deflects your hits until it commits to a strike, so the fight is about baiting its attacks and getting around them. It follows the [encounter design rules](../../encounter-design.md).

## Play

Run `.\lab.ps1 play`, open **Kratom - Boss Lab**, and choose Easy, Normal, or Hard. Find a reasonably open area, then use `/bosslab warden`. It appears on the ground 12 blocks ahead, facing you, and engages after three seconds. `/bosslab status` and `/bosslab clear` work as for the Sovereign. Summoning saves a character checkpoint for [quick rematches](../practice/README.md); `/bosslab retry` summons a fresh Warden.

Only one lab boss can be loaded per dimension. The Warden does not spawn naturally.

## The guard

Melee hits that strike its front (within 70° of where it faces) deal a quarter of their damage, with a minimum of 1. Its shield stops arrows striking the front completely. An anvil clank confirms a guarded hit. Arrows are judged by where they strike, not where you stand. Shots fired from more than 22 blocks away, the reach of its Chain Hook, glance off from any side. The guard drops completely while it is **EXPOSED** or **STAGGERED**, and it shatters for good in phase three.

Hammering the raised shield provokes it. Three guarded melee hits, each within 2 seconds of the last, trigger a **Shield Bash**: an orange wedge 60° each side and 3.5 blocks long, locked from the start, 10/9/8 ticks by phase. It deals 36 raw damage and throws you back. It interrupts a strike that is still tracking, but never one that has locked. Dodging it earns a short recovery.

The intended rhythm: bait a strike, dodge it when its warning locks, then hit its back or punish the recovery. The bow pays only from behind or into an opening.

## Posture

Clean hits during its openings build posture: hits that are not guarded and land while a strike's warning is locked or during a recovery. At 240 posture it is **STAGGERED** for 4 seconds. Whatever it was winding up is cancelled, its guard is down, and it takes 1.5 times damage. Posture starts draining by 1 per tick after 5 seconds without a clean hit. Its label shows the current posture.

## Combos

The Warden attacks in combos. Each step is chosen from where you are at that moment. Its warning first appears faint and follows you, aimed at where you are heading. For the final moments it flares, pulses, and locks; a locked warning never moves. Moving steadily, such as circling it or backing away, walks you into the strike. Dodge when the warning flares: change direction, leave the area, or roll inside and around it.

| Step | Chosen when | Warning | Raw damage |
| --- | --- | --- | --- |
| Cleave | You are in its front half, within 6 blocks | Orange wedge, 50° each side, 5.5 blocks; it steps toward you while tracking | 30 |
| Backhand | You are behind it mid-combo | Orange wedge behind it, 100° each side, 5 blocks; locked from the start | 24 |
| Slam | Last step, and you are heading to within 6 blocks | Orange circle 3 blocks toward you, radius 3.5 | 50 |
| Leap | Last step and you are heading farther; or you are over 14 blocks away; or it has made no progress toward you for 2 seconds | Orange circle where you will be when it lands, radius 4 | 40 |
| Chain Hook | You are more than 3 blocks above it; or you are 8 to 14 blocks away (at most every 8 seconds) | Chain corridor, up to 22 blocks | 12, then reels you in for a Slam |

Every combo ends with Slam or Leap, followed by an **EXPOSED** recovery. A Leap that misses earns no recovery: on landing it throws the Chain Hook straight after you, if you are in reach and in sight. Teleporting back to where it took off puts you in the chain's line; sidestep the chain when it locks. A missed Chain Hook leaves it exposed briefly. Rolling behind a Cleave usually earns a Backhand next, and the Slam turns to find you, so do not linger. Its sweeps and slams stop at solid cover; it never breaks terrain.

| | Phase 1 | Phase 2 | Phase 3 |
| --- | --- | --- | --- |
| Health | 4,000 to 2,400 | 2,400 to 1,000 | Below 1,000 |
| Combo length | 2 | 3 | 4 |
| Cleave warning, of which locked (ticks) | 20, 12 | 17, 11 | 14, 9 |
| Slam warning, of which locked (ticks) | 24, 18 | 21, 16 | 18, 14 |
| Leap warning, of which locked before takeoff (ticks); the flight adds 16 | 18, 6 | 16, 5 | 12, 4 |
| Chain Hook warning, of which locked (ticks) | 20, 10 | 17, 9 | 14, 8 |
| Exposed after a finisher (ticks) | 50 | 40 | 30 |
| Walk speed (blocks per tick; walking is 0.22, sprinting 0.28) | 0.25 | 0.28 | 0.31 |

## Harrowings and the swarm

At 2,400 health it begins **Kindle**: it is warded from all damage for 2 seconds, then raises three Cinders. Through phase two it keeps up to four alive, raising another every 8 seconds.

Cinders orbit you and dive one at a time, conducted by the Warden. A diving Cinder swells and flares bright yellow, with a line toward the point it locked: where you were heading when it flared. Change direction. A dive deals 8 magic damage and burns for 3 seconds. Cinders have 10 health: one sword hit or arrow kills one.

At 1,000 health it begins **Reclaim**: it is warded again and recalls every Cinder. The red sphere around it is the coming **Ash Nova**. It starts at a radius of 7 and grows by 3 for each Cinder that reaches the Warden, so shoot them down while they fly back. When the swarm is gone, the nova detonates after 1.5 seconds for 60 magic damage. Leave the sphere or put solid cover between you. The guard then shatters: phase three has no guard but the shortest warnings, the longest combos, and a walk faster than your sprint.

## Damage and rules

Every Warden and Cinder hit removes Regeneration, so a golden apple heals only between hits rather than out-healing its blows. Forge 1.5.2 has no healing event to cancel, so removing the effect is the available counter. Blows bypass ordinary armour defence; Protection enchantments, potion effects, and Forge damage events still apply. The nova and Cinders deal magic damage. Raw numbers are before mitigation and Minecraft difficulty scaling: Hard multiplies mob damage by 1.5. Phoenix Feathers keep their resurrection behaviour.

It ignores creative and otherwise invulnerable players. Leaving its 128-block range for 15 seconds of loaded simulation restores its health and first phase, and dismisses its swarm. Peaceful removes it without rewards. A player kill awards one Nether Star and 200 experience through normal loot handling. Saving preserves its health and phase; reloading cancels an unfinished attack and gives a fresh 3-second delay. Cinders are never saved, so a reloaded phase-two Warden raises a new swarm.

## Implementation and verification

Behaviour and rendering live in the `warden` feature package. Combat is server-owned. Health, action, timer, phase, locked strike point and yaw, and nova radius synchronize through the legacy DataWatcher. Strike geometry and telegraph drawing are shared with the Sovereign through `BossCombat` and `Telegraphs`. Combat constants at the top of `Warden` and `Cinder` are the tuning surface.

Strikes lead with a smoothed estimate of the opponent's horizontal velocity, taken from server positions each tick. The lead is capped at 8 blocks. Jumps of more than 3 blocks in one tick, such as teleports and the Chain Hook's reel, reset the estimate instead of counting as movement. The Slam's ground point aims straight at the heading, while its body turns at the normal rate; a turn-limited Slam lagged behind circling players, so reversing walked back into it.

The Warden walks with vanilla collision, step-up, and gravity: its AI runs in `updateEntityActionState`, which vanilla calls on the server just before travel. Leaps are scripted flights through terrain onto the first solid surface below the locked target. Bosses set `ignoreFrustumCheck`, because their warnings extend far beyond their bodies and would otherwise vanish when you look away from them.

`.\lab.ps1 smoke-warden` runs the combat scenario across two launches on a temporary stone platform in daylight. It snapshots the current lab save and restores it afterwards. It checks the guard against deterministic melee and arrow damage plus a real pack weapon, and shots from beyond the hook's reach. It covers each combo branch with hits and dodges taken when the warning locks, the hook reel into a Slam, and phase-three combos and warnings. The [known exploits](../../encounter-design.md#known-exploits) each have a regression: orbiting and backing away get hit, while reversing direction at the lock escapes; teleporting to the Leap's takeoff point is hooked and slammed. Posture builds from recovery hits, and a break staggers it, with heavier blows and no arrow shield. Hammering the shield provokes a Shield Bash, and a blow removes an enchanted golden apple's Regeneration. The swarm cases cover Kindle warding, Cinder dive hits and dodges, Reclaim with a Cinder shot down in flight, and nova radius, hit, and escape; the shattered guard; disengagement; persistence; loot; the one-boss rule; retry; clear; and Peaceful. Geometry probes cancel boss damage at the Forge boundary after recording it. Screenshots of each warning are saved in the run directory.

See the [verification record](verification.md) for accepted runs. Balance remains subject to human fights.
