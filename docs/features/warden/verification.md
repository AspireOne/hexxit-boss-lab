# Warden runtime acceptance

Target: the marked local lab, Minecraft 1.5.2 / Forge 7.8.1.737, with its existing Java 8 runtime and installed Hexxit mods. Date: 2026-10-25, version 0.5.0.

## Combat

`.\lab.ps1 smoke-warden` passed both launches, with evidence in `.lab/runs/20261025-204812-2fcbf7f8/`. The run summary passed, and `world-restored.json` confirms restoration of the exact starting save. The 0.5.0 version bump that followed changed no behaviour.

Sixteen cases passed:

- **Guard:** deterministic melee and arrow damage from the front and back; arrows judged by where they strike.
- **Combos:** front combo; rolling behind a Cleave; Backhand chain; Leap hit and dodge; Chain Hook reel into Slam; Chain Hook dodge; phase-three four-step combo with 11-tick warnings.
- **Swarm:** Kindle warding and three raised Cinders; Cinder dive hit and dodge; Reclaim with one Cinder shot in flight, nova radius, hit, and shattered guard; nova escape.
- **Lifecycle:** disengagement recovery; persistence across restart, Nether Star loot, the one-boss rule, retry of the checkpoint's boss kind, clear, and Peaceful.

The copied character's strongest hotbar weapon took 24 health from behind and 5 from the front. Earlier runs observed 16/5, 17/5, and 25/4. Pack weapons vary per swing and can deliver damage outside the attack event, so exact guard maths is proven with deterministic sources and the real weapon only has to be clearly blunted.

Screenshots of the Cleave, Slam, Leap, Chain Hook, Reclaim, Exposed, and Cinder warnings were inspected. They show the model and boss bar, legible ground wedges and circles on stone, the hook corridor from above, the nova sphere with Cinders returning, and a flaring Cinder with its dive line. Inspection found two rendering defects, both fixed before this run. First, ground warnings vanished when the Warden's body was off-screen; bosses now ignore the frustum check. Second, the fills were too faint on grey terrain.

## Regression

On the same day, `.\lab.ps1 smoke-boss` passed after the shared-geometry refactor and the frustum change (`20261025-205115-d5ae1a18`). On 0.5.0, `.\lab.ps1 smoke` passed (`20261025-205519-85431842`) and `.\lab.ps1 smoke-practice` passed (`20261025-205659-987b4bc0`).

## Limits

This establishes compatibility and scripted combat behaviour in the installed pack. Fight feel, balance, and duration need a human attempt. Pursuit across rough terrain, water, and caves is only exercised by real play: the scenario uses a flat platform and fixes the Warden in place until its first lock.
