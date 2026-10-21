package dev.hexxitlab;

import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;

/** A persistent, harmless practice target. No natural spawn registration or AI tasks. */
public final class LabTarget extends EntityLiving {
  public static final int MAX_HEALTH = 40;
  private static final int HEALTH_WATCHER = 20;

  public LabTarget(World world) {
    super(world);
    texture = "/mob/char.png";
    setSize(0.6F, 1.8F);
    setEntityHealth(MAX_HEALTH);
  }

  @Override
  protected void entityInit() {
    super.entityInit();
    dataWatcher.addObject(HEALTH_WATCHER, Integer.valueOf(MAX_HEALTH));
  }

  @Override
  public int getMaxHealth() {
    return MAX_HEALTH;
  }

  @Override
  public String getEntityName() {
    return "Lab Target";
  }

  @Override
  protected boolean canDespawn() {
    return false;
  }

  @Override
  protected void updateEntityActionState() {
    // Intentionally stationary, with no wandering or attack behavior.
  }

  @Override
  public void onLivingUpdate() {
    double x = posX;
    double y = posY;
    double z = posZ;
    super.onLivingUpdate();
    motionX = 0;
    motionY = 0;
    motionZ = 0;
    setPosition(x, y, z);
    if (worldObj.isRemote) {
      setEntityHealth(dataWatcher.getWatchableObjectInt(HEALTH_WATCHER));
    } else {
      dataWatcher.updateObject(HEALTH_WATCHER, Integer.valueOf(getHealth()));
    }
  }
}
