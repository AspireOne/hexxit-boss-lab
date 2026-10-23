package dev.hexxitlab.harness;

import dev.hexxitlab.BossLab;
import dev.hexxitlab.practice.PracticeCheckpoint;
import dev.hexxitlab.sovereign.Sovereign;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** Real network respawn, Forge cloning, disk persistence, inventory and client synchronization. */
final class PracticeScenario {
  private static final String EXPECTED = "BossLabPracticeExpected";
  private final boolean reload;
  private volatile int stage;
  private int ticks;
  private int deaths;
  private EntityPlayerMP corpse;
  private NBTTagCompound expected;
  private volatile boolean requestRespawn;
  private volatile boolean respawnSent;
  private volatile boolean clientRestored;
  private int clientDeathTicks;
  private double x;
  private double y;
  private double z;

  PracticeScenario(String phase) {
    reload = "reload".equals(phase);
  }

  boolean tick(MinecraftServer server, EntityPlayerMP player) {
    ticks++;
    if (stage == 0) {
      player.worldObj.difficultySetting = 2;
      if (reload) {
        require(PracticeCheckpoint.exists(player), "Checkpoint must survive restart");
        expected =
            player
                .getEntityData()
                .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getCompoundTag(EXPECTED);
        x = expected.getDouble("X");
        y = expected.getDouble("Y");
        z = expected.getDouble("Z");
        require(expected.hasKey("Inventory"), "Expected checkpoint inventory must survive restart");
        server.getCommandManager().executeCommand(player, "bosslab retry");
        assertCharacter(player);
        require(bosses(player.worldObj) == 1, "Retry after restart must summon a new boss");
        stage = 4;
      } else {
        server.getCommandManager().executeCommand(player, "bosslab clear");
        server.getCommandManager().executeCommand(player, "bosslab practice-off");
        require(!PracticeCheckpoint.exists(player), "Practice-off must remove checkpoint");
        x = player.posX;
        y = 220;
        z = player.posZ;
        player.capabilities.allowFlying = true;
        player.capabilities.isFlying = true;
        player.setPositionAndUpdate(x, y, z);
        player.setEntityHealth(player.getMaxHealth());
        player.getFoodStats().setFoodLevel(20);
        player.getFoodStats().setFoodSaturationLevel(5);
        player.inventory.currentItem = 0;
        expected = new NBTTagCompound();
        player.writeEntityToNBT(expected);
        require(expected.getTagList("Inventory").tagCount() > 10, "Real copied equipment required");
        expected.setDouble("X", x);
        expected.setDouble("Y", y);
        expected.setDouble("Z", z);
        player
            .getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .setCompoundTag(EXPECTED, expected);
        server.getCommandManager().executeCommand(player, "bosslab sovereign");
        require(PracticeCheckpoint.exists(player), "Summoning must capture a checkpoint");
        damageEquipment(player);
        player.setPositionAndUpdate(x + 6, y, z);
        server.getCommandManager().executeCommand(player, "bosslab retry");
        assertCharacter(player);
        require(
            bosses(player.worldObj) == 1, "Live retry must replace rather than duplicate the boss");
        BossLab.LOGGER.info("Practice PASS: live retry restores real equipment and supplies");
        stage = 1;
      }
      ticks = 0;
      return false;
    }
    if (stage == 1) {
      damageEquipment(player);
      for (int i = 0; i < player.inventory.mainInventory.length; i++) {
        ItemStack item = player.inventory.mainInventory[i];
        if (item != null && item.getItem().getClass().getSimpleName().equals("PhoenixFeather")) {
          player.inventory.mainInventory[i] = null;
        }
      }
      corpse = player;
      requestRespawn = true;
      respawnSent = clientRestored = false;
      player.hurtResistantTime = 0;
      require(
          player.attackEntityFrom(DamageSource.outOfWorld, 10000),
          "Real lethal damage must be accepted");
      require(player.getHealth() <= 0, "Practice test requires real death, not resurrection");
      stage = 2;
      ticks = 0;
    } else if (stage == 2 && player != corpse && player.isEntityAlive()) {
      // The production handler runs in the same server tick; allow its restoration to complete.
      if (PracticeCheckpoint.awaitingRecovery(player)) {
        require(ticks < 120, "Automatic restore must run after respawn");
        return false;
      }
      assertCharacter(player);
      require(
          bosses(player.worldObj) == 0,
          "Automatic respawn must remove the previous attempt's boss");
      for (Object value : player.worldObj.loadedEntityList) {
        if (value instanceof EntityItem || value instanceof EntityXPOrb) {
          Entity entity = (Entity) value;
          require(
              entity.getDistanceSq(x, y, z) > 400,
              "Practice death must not leave duplicate item or XP drops");
        }
      }
      requestRespawn = false;
      stage = 3;
      ticks = 0;
      BossLab.LOGGER.info(
          "Practice PASS: real death and Respawn restored checkpoint " + (++deaths));
    } else if (stage == 3 && clientRestored) {
      if (deaths < 2) {
        server.getCommandManager().executeCommand(player, "bosslab retry");
        require(bosses(player.worldObj) == 1, "Rematch command must create one fresh boss");
        stage = 1;
      } else {
        // Save a depleted character with an intact checkpoint to prove disk recovery next launch.
        damageEquipment(player);
        server.getConfigurationManager().saveAllPlayerData();
        return true;
      }
    } else if (stage == 4 && clientRestored) {
      server.getCommandManager().executeCommand(player, "bosslab clear");
      server.getCommandManager().executeCommand(player, "bosslab practice-off");
      require(!PracticeCheckpoint.exists(player), "Opt-out must persistently remove checkpoint");
      require(!PracticeCheckpoint.restore(player), "Missing checkpoint must not alter the player");
      BossLab.LOGGER.info(
          "Practice PASS: checkpoint survived restart, retry synchronized, opt-out works");
      return true;
    }
    require(ticks < 400, "Practice scenario timed out at stage " + stage);
    return false;
  }

  private void assertCharacter(EntityPlayerMP player) {
    require(
        player
            .inventory
            .writeToNBT(new NBTTagList("Inventory"))
            .equals(expected.getTagList("Inventory")),
        "Inventory, armour, enchantments, durability, bags and consumables must match checkpoint");
    require(player.getHealth() == expected.getShort("Health"), "Health must match checkpoint");
    require(
        player.getFoodStats().getFoodLevel() == expected.getInteger("foodLevel"),
        "Hunger must match checkpoint");
    require(
        player.experienceLevel == expected.getInteger("XpLevel")
            && player.experienceTotal == expected.getInteger("XpTotal"),
        "Experience must match checkpoint");
    require(player.getDistanceSq(x, y, z) < 0.1, "Position must return to checkpoint");
    require(
        player.capabilities.isFlying && player.capabilities.allowFlying,
        "Capabilities must return to checkpoint");
    require(player.hurtResistantTime > 0, "Restore must grant brief recovery grace");
  }

  private static void damageEquipment(EntityPlayerMP player) {
    ItemStack sword = player.inventory.mainInventory[0];
    if (sword != null) {
      sword.setItemDamage(sword.getItemDamage() + 10);
    }
    player.inventory.mainInventory[5] = null;
    player.inventory.armorInventory[0] = null;
    player.experienceLevel = 0;
    player.experienceTotal = 0;
    player.getFoodStats().setFoodLevel(3);
    player.setEntityHealth(2);
  }

  private static int bosses(World world) {
    int count = 0;
    for (Object value : world.loadedEntityList) {
      if (value instanceof Sovereign && !((Sovereign) value).isDead) {
        count++;
      }
    }
    return count;
  }

  void observe(Minecraft minecraft) {
    if (stage == 0 || minecraft.thePlayer == null) {
      return;
    }
    if (requestRespawn && minecraft.thePlayer.getHealth() <= 0 && !respawnSent) {
      if (++clientDeathTicks >= 25) {
        minecraft.thePlayer.respawnPlayer();
        minecraft.displayGuiScreen(null);
        respawnSent = true;
        clientDeathTicks = 0;
      }
    }
    if (!requestRespawn && expected != null && minecraft.thePlayer.getHealth() > 0) {
      clientRestored =
          minecraft
                  .thePlayer
                  .inventory
                  .writeToNBT(new NBTTagList("Inventory"))
                  .equals(expected.getTagList("Inventory"))
              && minecraft.thePlayer.getDistanceSq(x, y, z) < 1;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
