# Encounter design

Read this before designing or tuning a boss. It records who the bosses are built for and the rules learned from playtests. Add new facts here when they change what a fair, dangerous fight means.

## The player

Bosses are built for one experienced player with an endgame Hexxit character. The goal is a fight that this character can lose, not one that only a weaker character finds hard. The lab plays on **Hard**, which multiplies mob damage by 1.5.

| Equipment | Effect | What it means for bosses |
| --- | --- | --- |
| Hexxit Gear Scale Armour, Protection IV | Full-set Resistance, Strength, and Fire Resistance; Protection reduces most damage, including armour-bypassing blows | Fire and burning do nothing. Raw damage numbers must assume heavy mitigation. |
| A stack of enchanted golden apples | Regeneration IV for 30 seconds, plus Resistance and Fire Resistance for 5 minutes | Healing is almost unlimited. Chip damage alone never wins; pressure must outpace eating. |
| Phoenix Feathers: 3 carried, more stored at home | Prevent a death and regenerate health quickly; each use consumes a feather | A lost fight usually costs feathers, not the character. A dangerous fight should consume them. |
| Giant Diamond Sword, Sharpness V | Very high melee damage, boosted by Strength | Damage varies per swing and can bypass the Forge attack event. |
| Crystal Bow, Power V, Flame I | Strong long-range damage | Flame does nothing to fire-immune bosses. Ranged damage must not be free. |
| Electric Wand | Fast free flight anywhere | The player can always outrun, outclimb, or hover above a boss. |
| Cloak of Distortion | Instant teleport to the crosshair, even far away; costs an ender pearl | The player can dodge anything instantly and escape any position. |
| Dimensional sword | When charged, opens a door to a pocket dimension | The player can leave the fight completely to recover. |
| Golden Feather | Absorbs fall damage, using durability | Falls and knock-ups do nothing. |
| TNT and assorted potions | Situational | So far of little use against bosses. |

The saved character also carries a Twilight Cloak from Xeno's Reliquary. The character changes between play sessions, so automated tests locate equipment by type; see the [development foundation](features/development-foundation/README.md).

## Rules

Every boss must hold to these. They come from fights against this character.

1. **No risk-free strategy.** If one simple pattern, repeated, wins without real danger, the fight is broken, however slow that pattern is.
2. **Steady movement never dodges by itself.** Strikes aim where the player is heading, not where they stand, and lock only for their final moments. Circling, backing away, or strafing walks into them. Dodging means reacting when the warning locks: change direction, leave, or teleport.
3. **Every distance and height has a threat.** Close, medium, far, above, and behind cover each need an answer. Flight and teleport mean "out of reach" is always available, so being there must not be safe.
4. **The boss can answer from wherever it can be hurt.** If a boss cannot reach a position, it must refuse damage from there.
5. **Payoffs must be earned and felt.** A fight that is only dodge-then-poke feels flat even when it is fair. Reward good play with a visible climax, such as a posture break, and make the boss react to hits.
6. **Every hit is fair.** Each strike has a visible warning. A locked warning never moves. The reaction window after locking is at least 8 ticks (0.4 seconds).
7. **Long escapes reset the fight.** Leaving the fight to heal, for example through the pocket dimension, must not keep the boss's lost health.

## Known exploits

Check every new boss against these patterns, and add a combat-scenario regression for each one it must answer:

- **Orbiting:** walking a circle around a melee boss, hitting it, while every strike lands where the player was.
- **Backpedal kiting:** walking backwards out of melee range while shooting, staying faster than the boss and outside the circles that land where the player stood.
- **Sniping:** shooting from beyond every attack's reach, on foot or in flight.
- **Ranged attrition:** keeping distance and shooting whenever it is safe. Even when every attack can reach the player, a bow that deals steady damage from the front turns the fight into a slow, safe grind.
- **Out-healing:** standing in front of the boss and attacking while Regeneration IV from an enchanted golden apple heals about 6.7 health per second. Against about 4 health per second of mitigated boss damage, that is endless. A boss must counter healing or exceed it in bursts.
- **Teleporting into its blind spot:** for example, teleporting to where a leaping boss took off while it is still in the air, then shooting it during the recovery. Any move that ends with the boss committed and the player safe needs a follow-up threat.

The [Sovereign](features/sovereign/README.md) is an earlier prototype that predates these rules. The [Warden](features/warden/README.md) applies them.
