package dev.hexxitlab.harness;

import dev.hexxitlab.BossLab;
import dev.hexxitlab.practice.PracticeCheckpoint;
import dev.hexxitlab.sovereign.Sovereign;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

/** Real network respawn, Forge cloning, disk persistence, inventory and client synchronization. */
public final class PracticeScenario {
  private static final String EXPECTED = "BossLabPracticeExpected";
  // Tags that carried items advance on their own every tick, so they drift after any restore.
  // chargeTime: Xeno's Reliquary Twilight Cloak countdown while active.
  private static final String[] SELF_TICKING_TAGS = {"chargeTime"};
  private final boolean reload;
  private volatile int stage;
  private int ticks;
  private int deaths;
  private EntityPlayerMP corpse;
  private NBTTagCompound expected;
  private volatile boolean requestRespawn;
  private volatile boolean respawnSent;
  private volatile boolean clientRestored;
  private volatile String clientMismatch = "client not observed";
  private int clientDeathTicks;
  private double x;
  private double y;
  private double z;
  private int suppressedDeaths;

  PracticeScenario(String phase) {
    reload = "reload".equals(phase);
    MinecraftForge.EVENT_BUS.register(this);
  }

  @ForgeSubscribe(priority = EventPriority.LOWEST, receiveCanceled = true)
  public void verifyDeathDrops(PlayerDropsEvent event) {
    if (event.entityPlayer == corpse) {
      require(
          event.isCanceled() && event.drops.isEmpty(),
          "Practice death must cancel and clear the actual drop event");
      suppressedDeaths++;
    }
  }

  boolean tick(MinecraftServer server, EntityPlayerMP player) {
    ticks++;
    if (stage == 0) {
      player.worldObj.difficultySetting = 2;
      player.worldObj.getGameRules().setOrCreateGameRule("keepInventory", "false");
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
        server.getCommandManager().executeCommand(player, "bosslab checkpoint off");
        require(!PracticeCheckpoint.exists(player), "Checkpoint off must remove checkpoint");
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
        player.addPotionEffect(new PotionEffect(Potion.waterBreathing.id, 12000, 0));
        expected = new NBTTagCompound();
        player.writeEntityToNBT(expected);
        expected = (NBTTagCompound) expected.copy();
        require(expected.getTagList("Inventory").tagCount() > 10, "Real copied equipment required");
        expected.setDouble("X", x);
        expected.setDouble("Y", y);
        expected.setDouble("Z", z);
        player
            .getEntityData()
            .getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
            .setCompoundTag(EXPECTED, expected);
        server.getCommandManager().executeCommand(player, "bosslab sovereign");
        require(!PracticeCheckpoint.exists(player), "Summoning must not save a checkpoint");
        server.getCommandManager().executeCommand(player, "bosslab checkpoint");
        require(!PracticeCheckpoint.exists(player), "A checkpoint must not be saved mid-fight");
        server.getCommandManager().executeCommand(player, "bosslab clear");
        server.getCommandManager().executeCommand(player, "bosslab checkpoint");
        require(PracticeCheckpoint.exists(player), "The checkpoint command must save one");
        server.getCommandManager().executeCommand(player, "bosslab sovereign");
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
      player
          .worldObj
          .getGameRules()
          .setOrCreateGameRule("keepInventory", deaths == 0 ? "false" : "true");
      damageEquipment(player);
      // Die with experience to exercise orb suppression, not just XP restoration from zero.
      player.experienceLevel = expected.getInteger("XpLevel");
      player.experienceTotal = expected.getInteger("XpTotal");
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
      require(
          player.experienceLevel == 0,
          "Practice death must suppress vanilla death experience orbs");
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
          suppressedDeaths == 1,
          "Normal death must suppress drops; keepInventory death must not emit them");
      require(
          bosses(player.worldObj) == 0,
          "Automatic respawn must remove the previous attempt's boss");
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
      server.getCommandManager().executeCommand(player, "bosslab checkpoint off");
      require(!PracticeCheckpoint.exists(player), "Opt-out must persistently remove checkpoint");
      require(!PracticeCheckpoint.restore(player), "Missing checkpoint must not alter the player");
      BossLab.LOGGER.info(
          "Practice PASS: checkpoint survived restart, retry synchronized, opt-out works");
      return true;
    }
    require(ticks < 400, "Practice scenario timed out at stage " + stage + ": " + clientMismatch);
    return false;
  }

  private void assertCharacter(EntityPlayerMP player) {
    require(
        sameInventory(player.inventory.writeToNBT(new NBTTagList("Inventory"))),
        "Inventory, armour, enchantments, durability, bags and consumables must match checkpoint");
    require(player.getHealth() == expected.getShort("Health"), "Health must match checkpoint");
    require(
        player.getFoodStats().getFoodLevel() == expected.getInteger("foodLevel"),
        "Hunger must match checkpoint");
    require(
        player.getFoodStats().getSaturationLevel() == expected.getFloat("foodSaturationLevel"),
        "Saturation must match checkpoint");
    require(
        player.inventory.currentItem == expected.getInteger("SelectedItemSlot"),
        "Held slot must match checkpoint");
    require(player.isPotionActive(Potion.waterBreathing), "Checkpoint effects must be restored");
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

  private boolean sameInventory(NBTTagList actual) {
    return withoutSelfTickingTags(actual)
        .equals(withoutSelfTickingTags(expected.getTagList("Inventory")));
  }

  private static NBTTagList withoutSelfTickingTags(NBTTagList inventory) {
    NBTTagList copy = (NBTTagList) inventory.copy();
    for (int i = 0; i < copy.tagCount(); i++) {
      NBTTagCompound stack = (NBTTagCompound) copy.tagAt(i);
      for (String tag : SELF_TICKING_TAGS) {
        stack.getCompoundTag("tag").removeTag(tag);
      }
    }
    return copy;
  }

  private static void damageEquipment(EntityPlayerMP player) {
    ItemStack sword = player.inventory.mainInventory[0];
    if (sword != null) {
      sword.setItemDamage(sword.getItemDamage() + 10);
      sword.getTagCompound().setBoolean("PracticeMutation", true);
    }
    player.inventory.currentItem = 4;
    player.clearActivePotions();
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
      EntityPlayer local = minecraft.thePlayer;
      // The local legacy player's posY includes its eye offset; compare feet on both sides.
      clientMismatch =
          !sameInventory(local.inventory.writeToNBT(new NBTTagList("Inventory")))
              ? "client inventory differs"
              : local.inventory.currentItem != expected.getInteger("SelectedItemSlot")
                  ? "client held slot " + local.inventory.currentItem
                  : Math.abs(local.posX - x) >= 0.5
                          || Math.abs(local.boundingBox.minY - y) >= 0.5
                          || Math.abs(local.posZ - z) >= 0.5
                      ? "client feet at "
                          + local.posX
                          + ","
                          + local.boundingBox.minY
                          + ","
                          + local.posZ
                          + " expected "
                          + x
                          + ","
                          + y
                          + ","
                          + z
                      : null;
      clientRestored = clientMismatch == null;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
