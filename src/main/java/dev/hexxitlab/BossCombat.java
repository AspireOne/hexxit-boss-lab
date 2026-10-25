package dev.hexxitlab;

import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Targeting and strike geometry shared by lab bosses. Combat is server-owned. */
public final class BossCombat {
  public static final double PLAYER_HIT_RADIUS = 0.45;

  private BossCombat() {}

  public static boolean isOpponent(EntityLiving boss, EntityPlayer player, double range) {
    return player != null
        && player.isEntityAlive()
        && !player.capabilities.disableDamage
        && player.worldObj == boss.worldObj
        && boss.getDistanceSqToEntity(player) <= range * range;
  }

  public static Vec3 center(EntityPlayer player) {
    return Vec3.createVectorHelper(player.posX, player.posY + 0.9, player.posZ);
  }

  public static MovingObjectPosition trace(World world, Vec3 start, Vec3 end) {
    // Minecraft 1.5.2's DDA mutates its input vector. Never hand it locked attack geometry.
    return world.rayTraceBlocks(
        Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
        Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
  }

  public static boolean unobstructed(World world, Vec3 start, Vec3 end) {
    return trace(world, start, end) == null;
  }

  /** Finite cylindrical strike with rounded ends. */
  public static boolean inCorridor(Vec3 start, Vec3 end, Vec3 point, double radius) {
    double dx = end.xCoord - start.xCoord;
    double dy = end.yCoord - start.yCoord;
    double dz = end.zCoord - start.zCoord;
    double lengthSquared = dx * dx + dy * dy + dz * dz;
    double t =
        lengthSquared == 0
            ? 0
            : Math.max(
                0,
                Math.min(
                    1,
                    ((point.xCoord - start.xCoord) * dx
                            + (point.yCoord - start.yCoord) * dy
                            + (point.zCoord - start.zCoord) * dz)
                        / lengthSquared));
    double x = start.xCoord + t * dx - point.xCoord;
    double y = start.yCoord + t * dy - point.yCoord;
    double z = start.zCoord + t * dz - point.zCoord;
    return x * x + y * y + z * z <= radius * radius;
  }

  /** The legacy DataWatcher has no float type; floats travel as their raw int bits. */
  public static double readFloat(DataWatcher watcher, int index) {
    return Float.intBitsToFloat(watcher.getWatchableObjectInt(index));
  }

  public static void writeFloat(DataWatcher watcher, int index, double value) {
    watcher.updateObject(index, Integer.valueOf(Float.floatToIntBits((float) value)));
  }
}
