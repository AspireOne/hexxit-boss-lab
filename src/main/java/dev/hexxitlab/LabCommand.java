package dev.hexxitlab;

import dev.hexxitlab.practice.PracticeCheckpoint;
import dev.hexxitlab.sovereign.Sovereign;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

public final class LabCommand extends CommandBase {
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
    return "/bosslab spawn|sovereign|retry|practice-off|status|clear";
  }

  @Override
  public int getRequiredPermissionLevel() {
    return 0;
  }

  @Override
  public void processCommand(ICommandSender sender, String[] args) {
    if (args.length != 1) {
      throw new WrongUsageException(getCommandUsage(sender));
    }
    EntityPlayerMP player = getCommandSenderAsPlayer(sender);
    World world = player.worldObj;
    if ("practice-off".equals(args[0])) {
      PracticeCheckpoint.forget(player);
      sender.sendChatToPlayer("Practice recovery disabled. Death and drops now behave normally.");
      return;
    }
    if ("retry".equals(args[0])) {
      if (!PracticeCheckpoint.restore(player)) {
        sender.sendChatToPlayer("No practice checkpoint yet. Summon a Sovereign first.");
        return;
      }
      processCommand(player, new String[] {"sovereign"});
      return;
    }
    if ("sovereign".equals(args[0])) {
      if (world.difficultySetting == 0) {
        sender.sendChatToPlayer("Set difficulty to Easy, Normal or Hard before summoning.");
        return;
      }
      for (Object entity : world.loadedEntityList) {
        if (entity instanceof Sovereign && !((Sovereign) entity).isDead) {
          sender.sendChatToPlayer(
              "A Sovereign is already loaded. Use /bosslab clear to remove it.");
          return;
        }
      }
      double yaw = Math.toRadians(player.rotationYaw);
      Sovereign boss = new Sovereign(world);
      boss.setPosition(
          player.posX - Math.sin(yaw) * 18, player.posY + 5, player.posZ + Math.cos(yaw) * 18);
      if (!world.spawnEntityInWorld(boss)) {
        throw new IllegalStateException("Sovereign spawn rejected");
      }
      try {
        PracticeCheckpoint.capture(player, boss);
      } catch (RuntimeException error) {
        boss.setDead();
        throw error;
      }
      BossLab.LOGGER.info("Sovereign summoned: " + boss.getPersistentID());
      sender.sendChatToPlayer(
          "Checkpoint saved. Click Respawn after death to recover your gear. /bosslab retry restarts the fight.");
    } else if ("spawn".equals(args[0])) {
      LabTarget target = new LabTarget(world);
      target.setPosition(player.posX + 2, player.posY + 1, player.posZ);
      if (!world.spawnEntityInWorld(target)) {
        throw new IllegalStateException("Lab target spawn rejected");
      }
      BossLab.LOGGER.info("Lab target spawned: " + target.getPersistentID());
      sender.sendChatToPlayer("Spawned Lab Target (40 health).");
    } else if ("status".equals(args[0]) || "clear".equals(args[0])) {
      int count = 0;
      Object[] entities = world.loadedEntityList.toArray();
      for (Object entity : entities) {
        if ((entity instanceof LabTarget || entity instanceof Sovereign)
            && !((Entity) entity).isDead) {
          count++;
          if ("clear".equals(args[0])) {
            ((Entity) entity).setDead();
          } else if (entity instanceof Sovereign) {
            Sovereign boss = (Sovereign) entity;
            sender.sendChatToPlayer(
                "Sovereign: "
                    + boss.getHealth()
                    + "/"
                    + boss.getMaxHealth()
                    + " health, phase "
                    + boss.getPhase()
                    + ", "
                    + boss.getAction());
          }
        }
      }
      sender.sendChatToPlayer(
          ("clear".equals(args[0]) ? "Cleared " : "Loaded ")
              + count
              + " lab entities in this dimension.");
    } else {
      throw new WrongUsageException(getCommandUsage(sender));
    }
  }
}
