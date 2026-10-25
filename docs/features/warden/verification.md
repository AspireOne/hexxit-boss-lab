# Warden runtime acceptance

Target: the marked local lab, Minecraft 1.5.2 / Forge 7.8.1.737, with its existing Java 8 runtime and installed Hexxit mods. Date: 2026-10-25, version 0.5.1.

## Combat

`.\lab.ps1 smoke-warden` passed both launches, with evidence in `.lab/runs/20261025-225901-586436a7/`. The run summary passed, and `world-restored.json` confirms restoration of the exact starting save.

Twenty-one cases passed:

- **Guard:** deterministic melee and arrow damage from the front and back; arrows judged by where they strike; shots from beyond the hook's reach glance off.
- **Combos:** front combo; rolling behind a Cleave, then leaving before the turning Slam; Backhand chain; Leap hit and dodge; Chain Hook reel into Slam; Chain Hook dodge; phase-three four-step combo with 14-tick warnings.
- **Exploit regressions:** a walking orbit is hit by the Cleave and Slam, and reversing at each lock escapes both; a walking strafe is hit by the Leap, and reversing at the lock escapes it; backing away from the Chain Hook stays in its line and is reeled into a Slam.
- **Swarm:** Kindle warding and three raised Cinders; Cinder dive hit and dodge; Reclaim with one Cinder shot in flight, nova radius, hit, and shattered guard; nova escape.
- **Lifecycle:** disengagement recovery; persistence across restart, Nether Star loot, the one-boss rule, retry of the checkpoint's boss kind, clear, and Peaceful.

The first attempt (`20261025-225635-7055ac43`) failed the orbit reversal: the Slam's ground point turned with the body and lagged behind the circling player, so reversing walked back into it. The Slam now aims its ground point straight at the player's heading.

Pack weapons vary per swing and can deliver damage outside the attack event, so exact guard maths is proven with deterministic sources and the real weapon only has to be clearly blunted from the front.

Screenshots of the Cleave, Slam, Leap, Chain Hook, Reclaim, Exposed, and Cinder warnings were inspected. They show the model and boss bar and legible locked warnings on stone. Earlier inspection found two rendering defects, both fixed. First, ground warnings vanished when the Warden's body was off-screen; bosses now ignore the frustum check. Second, the fills were too faint on grey terrain.

## Regression

On 0.5.1, `.\lab.ps1 smoke-boss` passed (`20261025-230301-2bfa5c5c`); it also confirmed that the focus guard's Alt fallback returns focus. On 0.5.0, `.\lab.ps1 smoke` passed (`20261025-205519-85431842`) and `.\lab.ps1 smoke-practice` passed (`20261025-205659-987b4bc0`).

## Limits

This establishes compatibility and scripted combat behaviour in the installed pack. Fight feel, balance, and duration need a human attempt. Scripted movement is perfectly steady; a real player's server positions arrive less evenly, so how well leading reads real movement needs play. Pursuit across rough terrain, water, and caves is only exercised by real play: the scenario uses a flat platform and fixes the Warden in place until its first lock.
