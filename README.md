# Hexxit Boss Lab

Hand-built boss fights for the Hexxit modpack (Minecraft 1.5.2), designed against an endgame character and tested in the real game.

![The Ashen Warden guarding, enraged in phase three, and staggered](docs/images/warden.png)

## The bosses

**The Ashen Warden** is an armoured duelist. Its shield turns your blows aside until it commits to a strike, so you bait an attack, dodge it, and punish the opening. Clean hits break its posture and drop it to one knee. Wounded, it raises a swarm of Cinders, then reclaims them for an Ash Nova and fights on with its guard shattered. [Guide](docs/bosses/warden.md)

**The Hexbound Sovereign** is the airborne first prototype: lances, charges, and a crown-shaped blast. [Guide](docs/bosses/sovereign.md)

![A locked Cleave, the Chain Hook, and the Ash Nova](docs/images/warnings.png)

Every attack is warned. A warning follows you faintly, aimed at where you are heading, then flares and locks: keep moving the same way and you walk into the blow; change direction and you escape.

## Design

The fights are built for a character with free flight, instant teleport, revival feathers, and nearly endless healing. Against that kit, a fight only matters if no simple pattern wins safely. Every playtest exploit, such as circling, kiting with a bow, or out-healing the damage, becomes a rule and an automated regression. See [encounter design](docs/encounter-design.md).

## Running it

This is a personal lab rather than a packaged mod: it installs into one local Prism copy of Hexxit 1.0.11 on Windows, and never touches the original instance or saves.

```powershell
.\lab.ps1 setup   # create the isolated lab instance and world, once
.\lab.ps1 play    # build, install, and launch; then /bosslab warden
```

In game: `/bosslab warden` or `/bosslab sovereign` to summon, `/bosslab retry` for a rematch, and `/bosslab clear` to end a fight.

## How it is built

Java 6 code for Forge 7.8.1.737, compiled with JDK 11 through Gradle and Voldeloom. A separate test mod plays scripted fights in the real modpack, including dodges, exploits, deaths, and save/reload, and photographs every boss state. See [development](docs/development.md) and [testing](docs/testing.md).
