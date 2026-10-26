# Practice rematches

A checkpoint is a saved copy of your character, which you make yourself. While you have one, dying costs nothing: you come back to the checkpoint with your gear. Without one, death works as normal. Summoning a boss never saves or changes your checkpoint.

| Command or action | What it does |
| --- | --- |
| `/bosslab checkpoint` | Save your character where you stand, replacing any earlier checkpoint. Refused while a lab boss is loaded, so you cannot save mid-fight. |
| `/bosslab warden` or `/bosslab sovereign` | Summon a boss and nothing else. The chat says whether a checkpoint protects you. Refused while a boss is already loaded. |
| **Respawn** after dying | With a checkpoint, removes the boss and returns you to the checkpoint. Without one, a normal respawn. |
| `/bosslab retry` | Leave the current fight: return to the checkpoint, remove the boss, and summon the boss you last summoned there again. Works alive or after a respawn. |
| `/bosslab checkpoint off` | Delete the checkpoint. Death and drops are normal again. |
| `/bosslab status` | Show the loaded boss and whether you have a checkpoint. |
| `/bosslab` or `/bosslab help` | List these commands in the chat. |

A typical session: put on your gear, `/bosslab checkpoint`, `/bosslab warden`. After a death, click Respawn, then `/bosslab retry` when you are ready. To stop practising, use `/bosslab checkpoint off`.

The checkpoint holds your inventory, armour, item durability and NBT (including backpack contents), health, hunger, experience, effects, flight abilities, and position. Phoenix Feathers still work normally during combat.

This is a character reset, not a world rewind. Terrain, containers, dropped projectiles, and other entities keep their current state. The checkpoint stays until you delete it, including after `/bosslab clear`; delete it before unrelated adventuring. Do not move or drop checkpoint items into the world and then retry: recovery restores your starting inventory. Death drops and death experience orbs are suppressed so dying itself does not duplicate supplies.

Checkpoints survive quitting, restarting Minecraft, and replacing the mod during iteration. Forge's `PlayerPersisted` data carries them across respawn; snapshots exclude that wrapper to avoid recursively nesting earlier checkpoints. Restoration waits until the new player exists on the server, then synchronizes inventory, abilities, position, health, and experience. Summoning records the boss on the checkpoint, so a restore removes that boss even from another dimension, and retry knows which boss to summon. The original source save remains untouched.

## Verification

`.\lab.ps1 smoke-practice` tests that summoning saves no checkpoint and that one cannot be saved mid-fight, then live retry, two real deaths and client Respawn requests, exact equipment restoration, absence of duplicate death drops, client synchronization, persistence across a restart, and deleting the checkpoint. It snapshots the current lab save and restores it afterwards, like the combat scenarios.

Inventory comparisons are exact except for tags that carried items advance by themselves every tick, such as the Twilight Cloak's `chargeTime`. Such tags drift between a restore and its comparison even though the restore was exact. Add a newly found one to `SELF_TICKING_TAGS` in the practice scenario rather than loosening the comparison.
