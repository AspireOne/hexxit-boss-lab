package dev.hexxitlab.practice;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import dev.hexxitlab.BossLab;
import dev.hexxitlab.sovereign.Sovereign;
import java.util.EnumSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.packet.Packet43Experience;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

/** Lab-only character checkpoints, carried across death by Forge's PlayerPersisted contract. */
public final class PracticeCheckpoint implements ITickHandler {
  private static final String KEY = "BossLabCheckpoint";
  private static final int VERSION = 1;

  private static NBTTagCompound persisted(EntityPlayer player) {
    NBTTagCompound data = player.getEntityData();
    if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
      data.setCompoundTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
    }
    return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
  }

  public static boolean exists(EntityPlayer player) {
    return persisted(player).hasKey(KEY);
  }

  public static void capture(EntityPlayerMP player, Sovereign boss) {
    if (!player.isEntityAlive() || player.ridingEntity != null || player.isPlayerSleeping()) {
      throw new IllegalStateException(
          "Practice checkpoint requires an alive, awake, unmounted player");
    }
    NBTTagCompound checkpoint = new NBTTagCompound();
    NBTTagCompound character = new NBTTagCompound();
    // Entity's ForgeData wrapper is intentionally excluded: embedding it would nest checkpoints.
    player.writeEntityToNBT(character);
    checkpoint.setCompoundTag("Character", character);
    checkpoint.setInteger("Version", VERSION);
    checkpoint.setInteger("MaxHealth", player.getMaxHealth());
    checkpoint.setInteger("Dimension", player.dimension);
    checkpoint.setDouble("X", player.posX);
    checkpoint.setDouble("Y", player.posY);
    checkpoint.setDouble("Z", player.posZ);
    checkpoint.setFloat("Yaw", player.rotationYaw);
    checkpoint.setFloat("Pitch", player.rotationPitch);
    checkpoint.setString("Boss", boss.getPersistentID().toString());
    persisted(player).setCompoundTag(KEY, checkpoint);
    player.mcServer.getConfigurationManager().saveAllPlayerData();
    BossLab.LOGGER.info("Practice checkpoint saved for " + player.username);
  }

  public static void forget(EntityPlayer player) {
    persisted(player).removeTag(KEY);
    BossLab.LOGGER.info("Practice checkpoint disabled for " + player.username);
  }

  public static boolean restore(EntityPlayerMP player) {
    if (!exists(player)) {
      return false;
    }
    NBTTagCompound checkpoint = (NBTTagCompound) persisted(player).getCompoundTag(KEY).copy();
    if (checkpoint.getInteger("Version") != VERSION) {
      throw new IllegalStateException("Unsupported practice checkpoint version");
    }
    WorldServer destination =
        player.mcServer.worldServerForDimension(checkpoint.getInteger("Dimension"));
    if (destination == null) {
      throw new IllegalStateException("Practice checkpoint dimension unavailable");
    }
    // Remove only this attempt's boss, including when it is still waiting in the original
    // dimension.
    for (Object value : destination.loadedEntityList.toArray()) {
      if (value instanceof Sovereign
          && checkpoint.getString("Boss").equals(((Entity) value).getPersistentID().toString())) {
        ((Entity) value).setDead();
      }
    }
    player.inventory.setItemStack(null);
    player.closeScreen();
    if (player.ridingEntity != null) {
      player.mountEntity(null);
    }
    if (player.dimension != checkpoint.getInteger("Dimension")) {
      player
          .mcServer
          .getConfigurationManager()
          .transferPlayerToDimension(player, checkpoint.getInteger("Dimension"));
    }
    player.clearActivePotions();
    player.maxHealth = checkpoint.getInteger("MaxHealth");
    NBTTagCompound character = (NBTTagCompound) checkpoint.getCompoundTag("Character").copy();
    player.inventory.readFromNBT(character.getTagList("Inventory"));
    player.inventory.currentItem = character.getInteger("SelectedItemSlot");
    player.getFoodStats().readNBT(character);
    player.experience = character.getFloat("XpP");
    player.experienceLevel = character.getInteger("XpLevel");
    player.experienceTotal = character.getInteger("XpTotal");
    player.capabilities.readCapabilitiesFromNBT(character);
    player.setEntityHealth(character.getShort("Health"));
    NBTTagList effects = character.getTagList("ActiveEffects");
    for (int i = 0; i < effects.tagCount(); i++) {
      PotionEffect effect =
          PotionEffect.readCustomPotionEffectFromNBT((NBTTagCompound) effects.tagAt(i));
      if (effect != null) {
        player.addPotionEffect(effect);
      }
    }
    player.motionX = player.motionY = player.motionZ = 0;
    player.fallDistance = 0;
    player.deathTime = 0;
    player.extinguish();
    player.hurtResistantTime = 60;
    player.playerNetServerHandler.setPlayerLocation(
        checkpoint.getDouble("X"),
        checkpoint.getDouble("Y"),
        checkpoint.getDouble("Z"),
        checkpoint.getFloat("Yaw"),
        checkpoint.getFloat("Pitch"));
    player.sendPlayerAbilities();
    player.mcServer.getConfigurationManager().syncPlayerInventory(player);
    player.playerNetServerHandler.sendPacketToPlayer(
        new Packet43Experience(player.experience, player.experienceTotal, player.experienceLevel));
    player.setPlayerHealthUpdated();
    checkpoint.setBoolean("PendingRespawn", false);
    persisted(player).setCompoundTag(KEY, checkpoint);
    player.mcServer.getConfigurationManager().saveAllPlayerData();
    player.sendChatToPlayer("Practice checkpoint restored. /bosslab retry starts a fresh rematch.");
    BossLab.LOGGER.info("Practice checkpoint restored for " + player.username);
    return true;
  }

  @ForgeSubscribe(priority = EventPriority.LOWEST, receiveCanceled = true)
  public void onDeathDrops(PlayerDropsEvent event) {
    EntityPlayer player = event.entityPlayer;
    if (player.worldObj.isRemote || !exists(player)) {
      return;
    }
    event.drops.clear();
    event.setCanceled(true);
    // XP is restored from the checkpoint; suppress death orbs to avoid duplicating it.
    player.experienceLevel = 0;
    persisted(player).getCompoundTag(KEY).setBoolean("PendingRespawn", true);
    BossLab.LOGGER.info("Practice death recorded for " + player.username + "; awaiting Respawn");
  }

  public void tickStart(EnumSet<TickType> types, Object... data) {}

  public void tickEnd(EnumSet<TickType> types, Object... data) {
    MinecraftServer server = MinecraftServer.getServer();
    if (server == null) {
      return;
    }
    for (Object value : server.getConfigurationManager().playerEntityList.toArray()) {
      EntityPlayerMP player = (EntityPlayerMP) value;
      if (player.isEntityAlive()
          && exists(player)
          && persisted(player).getCompoundTag(KEY).getBoolean("PendingRespawn")) {
        restore(player);
      }
    }
  }

  public EnumSet<TickType> ticks() {
    return EnumSet.of(TickType.SERVER);
  }

  public String getLabel() {
    return "bosslab-practice-respawn";
  }
}
