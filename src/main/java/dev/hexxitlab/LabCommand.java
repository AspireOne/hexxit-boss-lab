package dev.hexxitlab;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
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
    return "/bosslab spawn|status|clear";
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
    if ("spawn".equals(args[0])) {
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
        if (entity instanceof LabTarget) {
          count++;
          if ("clear".equals(args[0])) {
            ((LabTarget) entity).setDead();
          }
        }
      }
      sender.sendChatToPlayer(
          ("clear".equals(args[0]) ? "Cleared " : "Loaded ")
              + count
              + " lab targets in this dimension.");
    } else {
      throw new WrongUsageException(getCommandUsage(sender));
    }
  }
}
