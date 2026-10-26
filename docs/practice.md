# Practice rematches

Summoning a lab boss (`/bosslab sovereign` or `/bosslab warden`) automatically saves your character at the starting position. After dying, click **Respawn**: the previous attempt's boss is removed and your inventory, armour, item durability and NBT (including backpack contents), health, hunger, experience, effects, and flight abilities return to that checkpoint. Use `/bosslab retry` when ready for the rematch, or use it while alive to restart an attempt. Retry summons the same boss that created the checkpoint. Each new summon replaces the checkpoint with your current starting state. Phoenix Feathers still work normally during combat.

- `/bosslab retry`: restore the character checkpoint and start a fresh attempt against the same boss.
- `/bosslab practice-off`: discard the checkpoint and return to normal death and drop behaviour.

This is a character reset, not a world rewind. Terrain, containers, dropped projectiles, and other entities retain their current state. The checkpoint remains active until `/bosslab practice-off`, including after `/bosslab clear`; turn practice off before unrelated adventuring. Do not transfer or deliberately drop checkpoint items into the world and then retry: practice recovery restores your starting inventory. Death drops and death experience orbs are suppressed so dying itself does not duplicate supplies.

Checkpoints survive quitting, restarting Minecraft, and replacing the mod during iteration. Forge's `PlayerPersisted` data carries them across respawn; snapshots exclude that wrapper to avoid recursively nesting earlier checkpoints. Restoration waits until the new player exists on the server, then synchronizes inventory, abilities, position, health, and experience. Checkpoints saved before 0.5.0 record no boss kind and rematch the Sovereign. The original source save remains untouched.

## Verification

`.\lab.ps1 smoke-practice` tests live retry, two real deaths and client Respawn requests, exact equipment restoration, absence of duplicate death drops, client synchronization, persistence across a restart, and opting out. It snapshots the current lab save and restores it afterwards, like the combat scenarios.

Inventory comparisons are exact except for tags that carried items advance by themselves every tick, such as the Twilight Cloak's `chargeTime`. Such tags drift between a restore and its comparison even though the restore was exact. Add a newly found one to `SELF_TICKING_TAGS` in the practice scenario rather than loosening the comparison.
