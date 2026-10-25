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
        sender.sendChatToPlayer("No practice checkpoint yet. Summon a boss first.");
        return;
      }
      processCommand(player, new String[] {PracticeCheckpoint.kind(player)});
      return;
    }
    if ("sovereign".equals(args[0])) {
      summon(player, args[0]);
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
        if ((entity instanceof LabTarget || entity instanceof LabBoss)
            && !((Entity) entity).isDead) {
          count++;
          if ("clear".equals(args[0])) {
            ((Entity) entity).setDead();
          } else if (entity instanceof LabBoss) {
            sender.sendChatToPlayer(((LabBoss) entity).describe());
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

  private static void summon(EntityPlayerMP player, String kind) {
    World world = player.worldObj;
    if (world.difficultySetting == 0) {
      player.sendChatToPlayer("Set difficulty to Easy, Normal or Hard before summoning.");
      return;
    }
    for (Object entity : world.loadedEntityList) {
      if (entity instanceof LabBoss && !((Entity) entity).isDead) {
        player.sendChatToPlayer("A lab boss is already loaded. Use /bosslab clear to remove it.");
        return;
      }
    }
    double yaw = Math.toRadians(player.rotationYaw);
    Entity boss = new Sovereign(world);
    boss.setPosition(
        player.posX - Math.sin(yaw) * 18, player.posY + 5, player.posZ + Math.cos(yaw) * 18);
    if (!world.spawnEntityInWorld(boss)) {
      throw new IllegalStateException(kind + " spawn rejected");
    }
    try {
      PracticeCheckpoint.capture(player, boss, kind);
    } catch (RuntimeException error) {
      boss.setDead();
      throw error;
    }
    BossLab.LOGGER.info("Boss summoned: " + kind + " " + boss.getPersistentID());
    player.sendChatToPlayer(
        "Checkpoint saved. Click Respawn after death to recover your gear. /bosslab retry restarts the fight.");
  }
}
