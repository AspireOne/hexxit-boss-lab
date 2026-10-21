package dev.hexxitlab;

import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

public final class TargetDrops {
  @ForgeSubscribe(priority = EventPriority.LOWEST)
  public void preventDrops(LivingDropsEvent event) {
    if (event.entityLiving instanceof LabTarget) {
      event.drops.clear();
      event.setCanceled(true);
    }
  }
}
