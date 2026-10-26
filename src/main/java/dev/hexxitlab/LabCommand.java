package dev.hexxitlab;

import dev.hexxitlab.practice.PracticeCheckpoint;
import dev.hexxitlab.sovereign.Sovereign;
import dev.hexxitlab.warden.Warden;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public final class LabCommand extends CommandBase {
  private static final String[] HELP = {
    "/bosslab warden | sovereign: summon a boss. Never touches your checkpoint.",
    "/bosslab checkpoint: save your gear, health and position. While one is saved, dying costs"
        + " nothing: Respawn brings you back to it and removes the boss.",
    "/bosslab retry: go back to your checkpoint and fight the last boss again.",
    "/bosslab checkpoint off: delete the checkpoint. Death is real again.",
    "/bosslab clear: remove the boss. /bosslab status: boss and checkpoint state.",
    "/bosslab spawn: a harmless practice target."
  };

  // Voldeloom's legacy API jar omits CommandBase's compiler-generated raw bridge.
  @Override
  public int compareTo(Object other) {
    return super.compareTo((ICommand) other);
  }

  @Override
  public String getCommandName() {
    return "bosslab";
  }

  @Override
  public String getCommandUsage(ICommandSender sender) {
    return "/bosslab help|warden|sovereign|checkpoint [off]|retry|clear|status|spawn";
  }

  @Override
  public int getRequiredPermissionLevel() {
    return 0;
  }

  @Override
  public void processCommand(ICommandSender sender, String[] args) {
    EntityPlayerMP player = getCommandSenderAsPlayer(sender);
    World world = player.worldObj;
    String command = args.length == 0 ? "help" : args[0];
    if (args.length == 2 && "checkpoint".equals(command) && "off".equals(args[1])) {
      PracticeCheckpoint.forget(player);
      sender.sendChatToPlayer("Checkpoint deleted. Death and drops now behave normally.");
      return;
    }
    if (args.length > 1) {
      throw new WrongUsageException(getCommandUsage(sender));
    }
    if ("help".equals(command)) {
      for (String line : HELP) {
        sender.sendChatToPlayer(line);
      }
    } else if ("checkpoint".equals(command)) {
      if (loadedBoss(world)) {
        sender.sendChatToPlayer(
            "Finish the fight or /bosslab clear it before saving a checkpoint.");
        return;
      }
      PracticeCheckpoint.capture(player);
      sender.sendChatToPlayer(
          "Checkpoint saved. Dying now brings you back here; /bosslab retry restarts a fight.");
    } else if ("retry".equals(command)) {
      if (!PracticeCheckpoint.restore(player)) {
        sender.sendChatToPlayer("No checkpoint. Save one with /bosslab checkpoint first.");
        return;
      }
      String kind = PracticeCheckpoint.kind(player);
      sender.sendChatToPlayer(
          "Back at your checkpoint." + (kind == null ? " Summon a boss to fight." : ""));
      if (kind != null) {
        summon(player, kind);
      }
    } else if ("sovereign".equals(command) || "warden".equals(command)) {
      summon(player, command);
    } else if ("spawn".equals(command)) {
      LabTarget target = new LabTarget(world);
      target.setPosition(player.posX + 2, player.posY + 1, player.posZ);
      if (!world.spawnEntityInWorld(target)) {
        throw new IllegalStateException("Lab target spawn rejected");
      }
      BossLab.LOGGER.info("Lab target spawned: " + target.getPersistentID());
      sender.sendChatToPlayer("Spawned Lab Target (40 health).");
    } else if ("status".equals(command) || "clear".equals(command)) {
      int count = 0;
      Object[] entities = world.loadedEntityList.toArray();
      for (Object entity : entities) {
        if ((entity instanceof LabTarget || entity instanceof LabBoss)
            && !((Entity) entity).isDead) {
          count++;
          if ("clear".equals(command)) {
            ((Entity) entity).setDead();
          } else if (entity instanceof LabBoss) {
            sender.sendChatToPlayer(((LabBoss) entity).describe());
          }
        }
      }
      sender.sendChatToPlayer(
          ("clear".equals(command) ? "Cleared " : "Loaded ")
              + count
              + " lab entities in this dimension.");
      if ("status".equals(command)) {
        sender.sendChatToPlayer(
            PracticeCheckpoint.exists(player)
                ? "Checkpoint saved. Dying brings you back to it."
                : "No checkpoint. Death is real.");
      }
    } else {
      throw new WrongUsageException(getCommandUsage(sender));
    }
  }

  private static boolean loadedBoss(World world) {
    for (Object entity : world.loadedEntityList) {
      if (entity instanceof LabBoss && !((Entity) entity).isDead) {
        return true;
      }
    }
    return false;
  }

  private static void summon(EntityPlayerMP player, String kind) {
    World world = player.worldObj;
    if (world.difficultySetting == 0) {
      player.sendChatToPlayer("Set difficulty to Easy, Normal or Hard before summoning.");
      return;
    }
    if (loadedBoss(world)) {
      player.sendChatToPlayer("A lab boss is already loaded. Use /bosslab clear to remove it.");
      return;
    }
    double yaw = Math.toRadians(player.rotationYaw);
    Entity boss;
    if ("warden".equals(kind)) {
      // A grounded duelist starts on the surface ahead, facing the player.
      double x = player.posX - Math.sin(yaw) * 12;
      double z = player.posZ + Math.cos(yaw) * 12;
      boss = new Warden(world);
      boss.setLocationAndAngles(
          x, BossCombat.groundBelow(world, x, player.posY + 6, z), z, player.rotationYaw + 180, 0);
    } else {
      boss = new Sovereign(world);
      boss.setPosition(
          player.posX - Math.sin(yaw) * 18, player.posY + 5, player.posZ + Math.cos(yaw) * 18);
    }
    if (!world.spawnEntityInWorld(boss)) {
      throw new IllegalStateException(kind + " spawn rejected");
    }
    BossLab.LOGGER.info("Boss summoned: " + kind + " " + boss.getPersistentID());
    if (PracticeCheckpoint.attach(player, boss, kind)) {
      player.sendChatToPlayer("Boss summoned. Dying brings you back to your checkpoint.");
    } else {
      player.sendChatToPlayer(
          "Boss summoned. No checkpoint: death is real. /bosslab help explains practice.");
    }
  }
}
