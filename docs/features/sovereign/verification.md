# Sovereign runtime acceptance

Date: 2026-10-21. Target: the marked local lab, Minecraft 1.5.2 / Forge 7.8.1.737, with its existing Java 8 runtime and installed Hexxit mods. Shipping version: **0.2.0**.

## Combat

`.\lab.ps1 smoke-boss` passed both launches for source revision `d093770`. Evidence is local under `.lab/runs/20261021-224627-21f96382/`; both phase results report PASS. The run summary passed and `world-restored.json` confirms restoration of the exact starting save.

The create phase verified twelve combat cases: lance hit/dodge/cover; rush hit/dodge; blast hit/escape/cover; actual enchanted armour damage; actual Phoenix Feather resurrection and consumption; phase-two lance; and phase-three rush. Checks also established locked aim synchronization, warnings before damage, one hit per charge, and no rush overshoot. Invulnerable-player exclusion, disengagement healing, and exact phase thresholds passed.

The reload phase found the same tagged boss at 777 health, verified safe attack reset, killed it through player damage, observed one Nether Star, and checked subsequent summoning, selective administrative cleanup, and Peaceful handling.

The copied equipped sword dealt **19 health points** in this run; a fully drawn Crystal Bow shot dealt **24**. A lance removed **28** of the equipped character's 40 health points. These are observations, not fixed balance guarantees: enchantments, critical hits, effects, and difficulty affect outcomes. Earlier probes observed different sword and mitigated-damage values.

Screenshots of all three warnings and both lance/blast impacts were captured through Minecraft's own API and inspected. They show the custom model, health bar, solid warning geometry, and stronger impact lines. Combat tests temporarily hold the character in open air at a fixed position; their complete save restoration returns inventory, abilities, effects, and position to the pre-test state.

## Regression and checks

The build enforces Java 6 APIs/bytecode, compiler warnings, SpotBugs for production and harness, formatting through the commit hook, and workflow regression tests. The workflow suite now has **21 tests**, including exact combat-save restoration, unmanaged-path rejection, copy failure, and recovery when the final restore rename fails.

`.\lab.ps1 smoke` passed both launches for revision `387cf8a`, with evidence under `.lab/runs/20261021-225534-58a45f78/`. It separately verified registration, player damage, client health synchronization, persistence, rendering, lethal damage, and selective cleanup. Its controlled target excludes incidental non-player damage in the harness; unexpected health changes fail immediately. Automated runs temporarily disable pause-on-focus-loss and restore the previous preference at shutdown.

## Limits

This establishes compatibility and tested combat behaviour in the installed local pack. Human balance, fight duration, and enjoyment still need the first real attempt. Dedicated-server support and arbitrary interactions with every other mod item are outside this acceptance.
