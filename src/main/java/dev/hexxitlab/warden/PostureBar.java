package dev.hexxitlab.warden;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.boss.BossStatus;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.ForgeSubscribe;

/**
 * A slim bar under the boss bar: posture building towards a break, then, once broken, the stagger's
 * remaining window draining away. Hidden while posture is empty.
 */
public final class PostureBar extends Gui {
  // Matches the vanilla boss bar: 182 pixels wide, drawn at y 12 and 5 pixels tall.
  private static final int WIDTH = 182;
  private static final int TOP = 19;
  private static final int HEIGHT = 3;
  private static final int BACKING = 0xB0100804;
  private static final int BUILDING = 0xFFE09030;
  private static final int NEAR_BREAK = 0xFFFF5A1E;
  private static final int BROKEN = 0xFFFFF0C0;
  private static final int BROKEN_PULSE = 0xFFFFB040;
  private static final float NEAR_SHARE = 0.75F;

  @ForgeSubscribe
  public void draw(RenderGameOverlayEvent.Post event) {
    if (event.type != RenderGameOverlayEvent.ElementType.BOSSHEALTH
        || BossStatus.statusBarLength <= 0) {
      return;
    }
    Minecraft minecraft = Minecraft.getMinecraft();
    Warden boss = shown(minecraft);
    if (boss == null) {
      return;
    }
    boolean broken = boss.getShownAction() == Warden.Action.STAGGER;
    float share =
        broken
            ? Math.max(0, boss.getActionTicks() - event.partialTicks) / Warden.STAGGER_TICKS
            : boss.getPosture() / (float) Warden.POSTURE_LIMIT;
    if (share <= 0) {
      return;
    }
    int left = event.resolution.getScaledWidth() / 2 - WIDTH / 2;
    int fill = Math.round(WIDTH * Math.min(1, share));
    boolean pulse = (boss.ticksExisted / 3) % 2 == 0;
    int color =
        broken ? (pulse ? BROKEN : BROKEN_PULSE) : share >= NEAR_SHARE ? NEAR_BREAK : BUILDING;
    drawRect(left - 1, TOP - 1, left + WIDTH + 1, TOP + HEIGHT + 1, BACKING);
    drawRect(left, TOP, left + fill, TOP + HEIGHT, color);
  }

  /** The Warden whose name the boss bar shows: the nearest one loaded. */
  private static Warden shown(Minecraft minecraft) {
    if (minecraft.theWorld == null || minecraft.thePlayer == null || BossStatus.bossName == null) {
      return null;
    }
    Warden nearest = null;
    for (Object value : minecraft.theWorld.loadedEntityList) {
      if (value instanceof Warden
          && ((Warden) value).isEntityAlive()
          && BossStatus.bossName.equals(((Warden) value).getEntityName())
          && (nearest == null
              || minecraft.thePlayer.getDistanceSqToEntity((Warden) value)
                  < minecraft.thePlayer.getDistanceSqToEntity(nearest))) {
        nearest = (Warden) value;
      }
    }
    return nearest;
  }
}
