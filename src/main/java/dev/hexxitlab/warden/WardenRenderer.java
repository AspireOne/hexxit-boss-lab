package dev.hexxitlab.warden;

import dev.hexxitlab.BossCombat;
import dev.hexxitlab.Telegraphs;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class WardenRenderer extends RenderLiving {
  private static final float SCALE = 1.5F;
  private static final float SHADOW = 1.1F;
  private static final float[] EMBER = {1, 0.42F, 0.08F};
  private static final float[] ASH = {1, 0.2F, 0.08F};
  private static final float[] WARD = {1, 0.78F, 0.4F};
  private static final float[] IRON = {0.22F, 0.2F, 0.19F, 1};
  private static final float[] HOT_LINKS = {1, 0.55F, 0.2F, 0.9F};
  private static final float GROUND_LIFT = 0.06F;
  // The harrowing ward hugs the body; deflected hits make it flare.
  private static final double WARD_RADIUS = 2;
  // A hooked player within this distance of the chain's line is the one being reeled.
  private static final double REEL_SEARCH = 3;

  public WardenRenderer() {
    super(new WardenModel(), SHADOW);
  }

  @Override
  protected void preRenderCallback(EntityLiving entity, float partialTick) {
    GL11.glScalef(SCALE, SCALE, SCALE);
  }

  @Override
  public void doRenderLiving(
      EntityLiving entity, double x, double y, double z, float yaw, float partialTick) {
    Warden boss = (Warden) entity;
    BossStatus.func_82824_a(boss, false);
    super.doRenderLiving(entity, x, y, z, yaw, partialTick);
    if (!boss.isEntityAlive()) {
      return;
    }
    // Locked points are world coordinates; draw them relative to the interpolated entity.
    Vec3 strike = boss.getStrike();
    double sx = x + strike.xCoord - lerp(entity.lastTickPosX, entity.posX, partialTick);
    double sy = y + strike.yCoord - lerp(entity.lastTickPosY, entity.posY, partialTick);
    double sz = z + strike.zCoord - lerp(entity.lastTickPosZ, entity.posZ, partialTick);
    GL11.glPushMatrix();
    Telegraphs.begin();
    try {
      warn(boss, x, y, z, sx, sy, sz, partialTick);
    } finally {
      Telegraphs.end();
      GL11.glPopMatrix();
    }
  }

  private void warn(
      Warden boss, double x, double y, double z, double sx, double sy, double sz, float tick) {
    Warden.Action action = boss.getAction();
    float time = boss.ticksExisted + tick;
    float remaining = Math.max(0, boss.getActionTicks() - tick);
    if (action == Warden.Action.CLEAVE
        || action == Warden.Action.BACKHAND
        || action == Warden.Action.BASH) {
      GL11.glTranslated(x, y + GROUND_LIFT, z);
      double yaw = boss.getStrikeYaw();
      if (action == Warden.Action.CLEAVE) {
        Telegraphs.sector(
            yaw, Warden.CLEAVE_ARC, Warden.CLEAVE_RADIUS, warning(boss, action, remaining, time));
      } else if (action == Warden.Action.BACKHAND) {
        Telegraphs.sector(
            yaw + 180,
            Warden.BACKHAND_ARC,
            Warden.BACKHAND_RADIUS,
            warning(boss, action, remaining, time));
      } else {
        Telegraphs.sector(
            yaw, Warden.BASH_ARC, Warden.BASH_RADIUS, warning(boss, action, remaining, time));
      }
    } else if (action == Warden.Action.SLAM) {
      GL11.glTranslated(sx, sy + GROUND_LIFT, sz);
      Telegraphs.disc(Warden.SLAM_RADIUS, warning(boss, action, remaining, time));
    } else if (action == Warden.Action.LEAP || action == Warden.Action.AIRBORNE) {
      // The landing stays locked through the flight; its fill completes as it lands.
      int window = Warden.commit(Warden.Action.LEAP, boss.getPhase()) + Warden.LEAP_AIRTIME;
      float left = action == Warden.Action.AIRBORNE ? remaining : remaining + Warden.LEAP_AIRTIME;
      Telegraphs.Style style =
          boss.isCommitted() ? Telegraphs.Style.LOCKED : Telegraphs.Style.TRACKING;
      GL11.glTranslated(sx, sy + GROUND_LIFT, sz);
      Telegraphs.disc(
          Warden.LEAP_RADIUS, new Telegraphs.Warning(EMBER, style, 1 - left / window, time));
    } else if (action == Warden.Action.HOOK) {
      GL11.glTranslated(x, y + Warden.CHEST_HEIGHT, z);
      Telegraphs.corridor(
          Vec3.createVectorHelper(sx - x, sy - y - Warden.CHEST_HEIGHT, sz - z),
          Warden.HOOK_RADIUS,
          true,
          warning(boss, action, remaining, time));
    } else if (action == Warden.Action.REEL) {
      reel(boss, x, y, z, sx, sy, sz);
    } else if (boss.isWarded()) {
      GL11.glTranslated(x, y + Warden.CHEST_HEIGHT, z);
      float flare = boss.getGuardFlash(tick);
      Telegraphs.dome(
          WARD_RADIUS,
          0,
          new Telegraphs.Warning(WARD, Telegraphs.Style.IMPACT, 0.75F - 0.75F * flare, time));
      if (action == Warden.Action.RECLAIM) {
        // Cinders still feed it, so it can grow: drawn as a warning that has not locked yet.
        Telegraphs.dome(
            boss.getNovaRadius(),
            Warden.CHEST_HEIGHT,
            new Telegraphs.Warning(ASH, Telegraphs.Style.TRACKING, 0, time));
      } else if (action == Warden.Action.NOVA) {
        Telegraphs.dome(
            boss.getNovaRadius(),
            Warden.CHEST_HEIGHT,
            new Telegraphs.Warning(
                ASH, Telegraphs.Style.LOCKED, 1 - remaining / Warden.NOVA_WINDUP, time));
      }
    }
  }

  /** Faint and following while it tracks; once locked, it fills towards the moment it lands. */
  private static Telegraphs.Warning warning(
      Warden boss, Warden.Action action, float remaining, float time) {
    if (!boss.isCommitted()) {
      return new Telegraphs.Warning(EMBER, Telegraphs.Style.TRACKING, 0, time);
    }
    int commit = Warden.commit(action, boss.getPhase());
    return new Telegraphs.Warning(EMBER, Telegraphs.Style.LOCKED, 1 - remaining / commit, time);
  }

  /** A taut chain from the ember core to the hooked player, or to the hook if none is near. */
  private static void reel(
      Warden boss, double x, double y, double z, double sx, double sy, double sz) {
    Vec3 core = Vec3.createVectorHelper(x, y + Warden.CHEST_HEIGHT, z);
    Vec3 end = Vec3.createVectorHelper(sx, sy, sz);
    Vec3 origin = boss.chest();
    Vec3 hook = boss.getStrike();
    for (Object value : boss.worldObj.playerEntities) {
      Vec3 centre = BossCombat.center((EntityPlayer) value);
      if (BossCombat.inCorridor(origin, hook, centre, REEL_SEARCH)) {
        end =
            Vec3.createVectorHelper(
                x + centre.xCoord - origin.xCoord,
                y + Warden.CHEST_HEIGHT + centre.yCoord - origin.yCoord,
                z + centre.zCoord - origin.zCoord);
        break;
      }
    }
    Telegraphs.chain(core, end, IRON, 5);
    Telegraphs.chain(core, end, HOT_LINKS, 2);
  }

  private static double lerp(double from, double to, float t) {
    return from + (to - from) * t;
  }
}
