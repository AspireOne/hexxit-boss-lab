# Warden runtime acceptance

Target: the marked local lab, Minecraft 1.5.2 / Forge 7.8.1.737, with its existing Java 8 runtime and installed Hexxit mods. Date: 2026-10-26, version 0.5.2. The source save had been quit on the death screen; the harness respawned the character and the practice checkpoint restored its loadout.

## Combat

`.\lab.ps1 smoke-warden` passed both launches, with evidence in `.lab/runs/20261026-000935-2509d5ca/`. The run summary passed, and `world-restored.json` confirms restoration of the exact starting save.

Twenty-three cases passed:

- **Guard:** deterministic melee damage from the front and back; frontal arrows stopped by the shield; arrows judged by where they strike; shots from beyond the hook's reach glance off; hits outside openings build no posture.
- **Posture:** a recovery hit builds posture; a full bar staggers the Warden, which then takes 1.5 times damage and no longer shields arrows.
- **Combos:** front combo; rolling behind a Cleave, then leaving before the turning Slam; Backhand chain; Leap hit; Leap dodge followed by a dodged Chain Hook; Chain Hook reel into Slam; Chain Hook dodge; phase-three four-step combo with 14-tick warnings.
- **Exploit regressions:** a walking orbit is hit by the Cleave and Slam, and reversing at each lock escapes both. A walking strafe is hit by the Leap; reversing at the lock escapes it, and sidestepping escapes the Chain Hook that follows. Teleporting to the Leap's takeoff point is hooked and slammed. Backing away from the Chain Hook stays in its line and is reeled into a Slam.
- **Swarm:** Kindle warding and three raised Cinders; Cinder dive hit and dodge; Reclaim with one Cinder shot in flight, nova radius, hit, and shattered guard; nova escape.
- **Lifecycle:** disengagement recovery; persistence across restart, Nether Star loot, the one-boss rule, retry of the checkpoint's boss kind, clear, and Peaceful.

Earlier failures each showed a real flaw:

- **The Slam lagged a circling player** (`20261025-225635-7055ac43`). Its ground point turned with the body and trailed behind a circling player, so reversing walked back into it. It now aims straight at the heading.
- **A test answer was geometrically wrong.** A missed Leap lands ahead on a strafing player's path. Reversing then moves the player straight along the following Chain Hook's line, so only a sidestep dodges it.
- **The test needed a living character.** It now respawns a character saved dead and finds the weapon anywhere in the inventory.

Pack weapons vary per swing and can deliver damage outside the attack event, so exact guard maths is proven with deterministic sources and the real weapon only has to be clearly blunted from the front.

Screenshots of the Cleave, Slam, Leap, Chain Hook, Reclaim, Exposed, and Cinder warnings were inspected on 0.5.1. They show the model and boss bar and legible locked warnings on stone.

## Regression

On 0.5.2's harness, `.\lab.ps1 smoke-boss` passed (`20261026-001310-1679ac0e`) and `.\lab.ps1 smoke-practice` passed (`20261026-001626-46f08fd3`). On 0.5.0, `.\lab.ps1 smoke` passed (`20261025-205519-85431842`).

## Limits

This establishes compatibility and scripted combat behaviour in the installed pack. Fight feel, balance, and duration need a human attempt, including how often posture breaks in real play. Scripted movement is perfectly steady; a real player's server positions arrive less evenly, so how well leading reads real movement needs play. Pursuit across rough terrain, water, and caves is only exercised by real play: the scenario uses a flat platform and fixes the Warden in place until its first lock.
