package dev.hexxitlab.sovereign;

import dev.hexxitlab.BossCombat;
import dev.hexxitlab.Telegraphs;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class SovereignRenderer extends RenderLiving {
  // Each attack's colour, on its warning and on the body while it winds up, and the paler heat
  // its warning fills with: violet for the lance, rose for the rush, orange for Crown Break.
  static final float[] LANCE = {0.58F, 0.3F, 1};
  static final float[] RUSH = {1, 0.3F, 0.62F};
  static final float[] BLAST = {1, 0.45F, 0.08F};
  private static final float[] LANCE_HEAT = {0.86F, 0.78F, 1};
  private static final float[] RUSH_HEAT = {1, 0.8F, 0.9F};
  private static final float[] BLAST_HEAT = {1, 0.88F, 0.6F};
  // The body's glow while hunting, by phase: a cold violet that heats towards magenta.
  private static final float[][] IDLE = {{0.5F, 0.42F, 1}, {0.62F, 0.36F, 1}, {0.86F, 0.4F, 1}};
  private static final float SCALE = 1.7F;
  private static final float SHADOW = 1.2F;
  // Ticks over which a released attack's shape flashes and fades.
  private static final int FLASH_TICKS = 6;

  public SovereignRenderer() {
    super(new SovereignModel(), SHADOW);
  }

  static float[] idle(int phase) {
    return IDLE[phase - 1];
  }

  @Override
  protected void preRenderCallback(EntityLiving entity, float partialTick) {
    GL11.glScalef(SCALE, SCALE, SCALE);
  }

  /** It does not topple when it dies; its model comes apart instead. */
  @Override
  protected float getDeathMaxRotation(EntityLiving entity) {
    return 0;
  }

  @Override
  public void doRenderLiving(
      EntityLiving entity, double x, double y, double z, float yaw, float partialTick) {
    Sovereign boss = (Sovereign) entity;
    BossStatus.func_82824_a(boss, false);
    super.doRenderLiving(entity, x, y, z, yaw, partialTick);
    if (!boss.isEntityAlive()) {
      return;
    }
    GL11.glPushMatrix();
    Telegraphs.begin();
    try {
      GL11.glTranslated(x, y + Sovereign.CENTER_HEIGHT, z);
      warn(boss, partialTick);
    } finally {
      Telegraphs.end();
      GL11.glPopMatrix();
    }
  }

  /** Draws relative to the Sovereign's centre: warnings, release flashes, and a charge's wake. */
  private static void warn(Sovereign boss, float tick) {
    Sovereign.Action action = boss.getAction();
    float time = boss.ticksExisted + tick;
    float remaining = Math.max(0, boss.getActionTicks() - tick);
    Vec3 aim = boss.getAim();
    if (action == Sovereign.Action.LANCE
        || action == Sovereign.Action.RUSH
        || action == Sovereign.Action.BLAST) {
      // Every Sovereign warning is locked from the start.
      float progress = 1 - remaining / Sovereign.windup(action, boss.getPhase());
      Telegraphs.Warning warning =
          new Telegraphs.Warning(
              color(action), heat(action), Telegraphs.Style.LOCKED, progress, time);
      if (action == Sovereign.Action.BLAST) {
        double floor = floor(boss);
        Telegraphs.dome(Sovereign.BLAST_RADIUS, floor, warning);
        SovereignWarnings.crownSigil(Sovereign.BLAST_RADIUS, floor, BLAST, progress, time);
      } else if (action == Sovereign.Action.LANCE) {
        Telegraphs.corridor(aim, Sovereign.LANCE_RADIUS, false, warning);
        SovereignWarnings.hexRings(aim, Sovereign.LANCE_RADIUS, LANCE, progress, time);
      } else {
        Telegraphs.corridor(aim, Sovereign.RUSH_RADIUS, false, warning);
        SovereignWarnings.chevrons(aim, Sovereign.RUSH_RADIUS, RUSH, progress, time);
      }
      return;
    }
    Sovereign.Action previous = boss.getPreviousAction();
    float age = boss.getActionAge() + tick;
    if (boss.getShownAction() == Sovereign.Action.RECOVER && age < FLASH_TICKS) {
      // The attack just fired: its shape flashes and fades.
      float fade = age / FLASH_TICKS;
      Telegraphs.Warning flash =
          new Telegraphs.Warning(
              color(previous), heat(previous), Telegraphs.Style.IMPACT, fade, time);
      if (previous == Sovereign.Action.LANCE) {
        Telegraphs.corridor(aim, Sovereign.LANCE_RADIUS, false, flash);
        SovereignWarnings.beam(aim, Sovereign.LANCE_RADIUS, LANCE, fade);
      } else if (previous == Sovereign.Action.BLAST) {
        Telegraphs.dome(Sovereign.BLAST_RADIUS, floor(boss), flash);
      }
    } else if (boss.getShownAction() == Sovereign.Action.CHARGE) {
      Vec3 start = boss.getChargeStart();
      double ix = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * tick;
      double iy = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * tick;
      double iz = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * tick;
      if (start != null) {
        SovereignWarnings.wake(
            Vec3.createVectorHelper(start.xCoord - ix, start.yCoord - iy, start.zCoord - iz),
            Sovereign.RUSH_RADIUS,
            RUSH);
      }
    }
  }

  /** How far the ground lies below the Sovereign's centre. */
  private static double floor(Sovereign boss) {
    double centre = boss.posY + Sovereign.CENTER_HEIGHT;
    return centre - BossCombat.groundBelow(boss.worldObj, boss.posX, centre, boss.posZ);
  }

  private static float[] heat(Sovereign.Action action) {
    return action == Sovereign.Action.BLAST
        ? BLAST_HEAT
        : action == Sovereign.Action.LANCE ? LANCE_HEAT : RUSH_HEAT;
  }

  static float[] color(Sovereign.Action action) {
    return action == Sovereign.Action.BLAST
        ? BLAST
        : action == Sovereign.Action.LANCE ? LANCE : RUSH;
  }
}
