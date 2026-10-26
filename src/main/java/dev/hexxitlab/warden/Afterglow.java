package dev.hexxitlab.warden;

import dev.hexxitlab.Telegraphs;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * A short-lived shape left by a strike: the warning flashing as it lands, a shockwave, a scorch
 * mark, a swing trail, or the nova's burst. It draws its own geometry in the lit particle layer.
 */
final class Afterglow extends EntityFX {
  enum Kind {
    SECTOR,
    DISC,
    SHOCKWAVE,
    SCORCH,
    SLASH,
    BURST
  }

  private static final int OWN_GEOMETRY_LAYER = 3;
  private static final float GROUND_LIFT = 0.05F;
  // A swing trail sweeps at chest height, from part-way out to the strike's reach.
  private static final double SLASH_HEIGHT = 1.4;
  private static final double SLASH_INNER = 0.4;
  private static final double SHOCKWAVE_GROWTH = 1.5;
  private static final double SHOCKWAVE_WIDTH = 0.6;
  private static final float[] SOOT = {0.06F, 0.04F, 0.03F};
  private static final float[] WHITE_HOT = {1, 0.9F, 0.65F};
  private static final float[] ASH = {1, 0.3F, 0.1F};
  private final Kind kind;
  private final double yaw;
  private final double arc;
  private final double radius;

  Afterglow(
      World world,
      double x,
      double y,
      double z,
      Kind kind,
      double yaw,
      double arc,
      double radius,
      int life) {
    super(world, x, y, z);
    this.kind = kind;
    this.yaw = yaw;
    this.arc = arc;
    this.radius = radius;
    particleMaxAge = life;
    noClip = true;
  }

  @Override
  public void onUpdate() {
    prevPosX = posX;
    prevPosY = posY;
    prevPosZ = posZ;
    if (particleAge++ >= particleMaxAge) {
      setDead();
    }
  }

  @Override
  public int getFXLayer() {
    return OWN_GEOMETRY_LAYER;
  }

  @Override
  public void renderParticle(
      Tessellator tessellator, float partialTick, float x, float y, float z, float u, float v) {
    float t = Math.min(1, (particleAge + partialTick) / particleMaxAge);
    float time = particleAge + partialTick;
    GL11.glPushMatrix();
    Telegraphs.begin();
    try {
      GL11.glTranslated(posX - interpPosX, posY - interpPosY + GROUND_LIFT, posZ - interpPosZ);
      switch (kind) {
        case SECTOR:
          Telegraphs.sector(
              yaw,
              arc,
              radius,
              new Telegraphs.Warning(WHITE_HOT, Telegraphs.Style.IMPACT, t, time));
          break;
        case DISC:
          Telegraphs.disc(
              radius, new Telegraphs.Warning(WHITE_HOT, Telegraphs.Style.IMPACT, t, time));
          break;
        case SHOCKWAVE:
          float out = 1 - (1 - t) * (1 - t);
          Telegraphs.ring(
              radius * (0.3 + SHOCKWAVE_GROWTH * out), SHOCKWAVE_WIDTH, WHITE_HOT, 0.8F * (1 - t));
          break;
        case SCORCH:
          // Lingers, then fades: a dark burn where the blow fell.
          Telegraphs.patch(radius, SOOT, 0.55F * Math.min(1, 3 * (1 - t)));
          break;
        case SLASH:
          GL11.glTranslated(0, SLASH_HEIGHT, 0);
          Telegraphs.arcBand(yaw, arc, radius * SLASH_INNER, radius, WHITE_HOT, 0, 0.7F * (1 - t));
          break;
        default:
          Telegraphs.dome(
              radius * (1 - (1 - t) * (1 - t) * (1 - t)),
              0,
              new Telegraphs.Warning(ASH, Telegraphs.Style.IMPACT, t, time));
          break;
      }
    } finally {
      Telegraphs.end();
      GL11.glPopMatrix();
    }
  }
}
