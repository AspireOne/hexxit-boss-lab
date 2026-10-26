package dev.hexxitlab.sovereign;

import dev.hexxitlab.Telegraphs;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

/**
 * A short-lived shape left by the Sovereign: a ring of force facing along an attack, a shockwave
 * across the ground, Crown Break's burst, or a scorched sigil. It draws its own geometry in the lit
 * particle layer.
 */
final class HexFlash extends EntityFX {
  enum Kind {
    RING,
    SHOCKWAVE,
    BURST,
    SCORCH
  }

  private static final int OWN_GEOMETRY_LAYER = 3;
  private static final float GROUND_LIFT = 0.05F;
  private static final double RING_WIDTH = 0.35;
  private static final double SHOCKWAVE_WIDTH = 0.7;
  private final Kind kind;
  private final float[] rgb;
  private final double radius;
  private Vec3 facing = Vec3.createVectorHelper(0, 1, 0);

  HexFlash(
      World world, double x, double y, double z, Kind kind, float[] rgb, double radius, int life) {
    super(world, x, y, z);
    this.kind = kind;
    this.rgb = rgb.clone();
    this.radius = radius;
    particleMaxAge = life;
    noClip = true;
  }

  /** Turns a ring to face along a direction. */
  HexFlash facing(Vec3 direction) {
    facing = direction.normalize();
    return this;
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
    float out = 1 - (1 - t) * (1 - t);
    GL11.glPushMatrix();
    Telegraphs.begin();
    try {
      GL11.glTranslated(posX - interpPosX, posY - interpPosY, posZ - interpPosZ);
      switch (kind) {
        case RING:
          // Rings lie in the ground plane; tip that plane's normal onto the facing.
          double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, facing.yCoord))));
          if (Math.abs(facing.xCoord) + Math.abs(facing.zCoord) > 1e-4) {
            GL11.glRotatef((float) angle, (float) facing.zCoord, 0, (float) -facing.xCoord);
          }
          Telegraphs.ring(radius * (0.25 + 0.75 * out), RING_WIDTH, rgb, 0.9F * (1 - t));
          break;
        case SHOCKWAVE:
          GL11.glTranslatef(0, GROUND_LIFT, 0);
          Telegraphs.ring(radius * (0.2 + 0.8 * out), SHOCKWAVE_WIDTH, rgb, 0.85F * (1 - t));
          break;
        case BURST:
          Telegraphs.dome(
              radius * (1 - (1 - t) * (1 - t) * (1 - t)),
              0,
              new Telegraphs.Warning(rgb, Telegraphs.Style.IMPACT, t, particleAge + partialTick));
          break;
        default:
          // Lingers, then fades: a dark burn where the crown broke.
          GL11.glTranslatef(0, GROUND_LIFT, 0);
          Telegraphs.patch(radius, rgb, 0.6F * Math.min(1, 3 * (1 - t)));
          break;
      }
    } finally {
      Telegraphs.end();
      GL11.glPopMatrix();
    }
  }
}
