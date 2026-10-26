package dev.hexxitlab.sovereign;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/**
 * A mote of hex light: it is born white, takes its colour, and dims as it dies. A wisp drawn to a
 * point flies there and vanishes on arrival, so the Sovereign visibly gathers power.
 */
final class Wisp extends EntityFX {
  // The small soft dot in the vanilla particle sheet.
  private static final int DOT = 7;
  private static final int FULL_BRIGHT = 0xF000F0;
  private static final float DRAG = 0.92F;
  private static final float FLICKER = 0.01F;
  private static final double ARRIVAL = 0.3;
  private final float[] rgb;
  private double targetX;
  private double targetY;
  private double targetZ;
  private double pull;

  Wisp(
      World world,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      float size,
      float[] rgb,
      int life) {
    super(world, x, y, z);
    motionX = vx;
    motionY = vy;
    motionZ = vz;
    particleScale = size;
    particleMaxAge = life;
    noClip = true;
    this.rgb = rgb.clone();
    setParticleTextureIndex(DOT);
    color(0);
  }

  /** Draws the wisp towards a point by the given share of the distance each tick. */
  Wisp toward(double x, double y, double z, double pull) {
    targetX = x;
    targetY = y;
    targetZ = z;
    this.pull = pull;
    return this;
  }

  private void color(float age) {
    float white = Math.max(0, 1 - age * 4);
    float dim = age > 0.6F ? 1 - (age - 0.6F) * 1.5F : 1;
    particleRed = (rgb[0] + (1 - rgb[0]) * white) * dim;
    particleGreen = (rgb[1] + (1 - rgb[1]) * white) * dim;
    particleBlue = (rgb[2] + (1 - rgb[2]) * white) * dim;
  }

  @Override
  public void onUpdate() {
    prevPosX = posX;
    prevPosY = posY;
    prevPosZ = posZ;
    if (particleAge++ >= particleMaxAge) {
      setDead();
      return;
    }
    float age = particleAge / (float) particleMaxAge;
    color(age);
    particleAlpha = age > 0.7F ? (1 - age) / 0.3F : 1;
    if (pull > 0) {
      double dx = targetX - posX;
      double dy = targetY - posY;
      double dz = targetZ - posZ;
      if (dx * dx + dy * dy + dz * dz < ARRIVAL * ARRIVAL) {
        setDead();
        return;
      }
      motionX = motionX * DRAG + dx * pull;
      motionY = motionY * DRAG + dy * pull;
      motionZ = motionZ * DRAG + dz * pull;
    } else {
      motionX = motionX * DRAG + (rand.nextFloat() - 0.5F) * FLICKER;
      motionY = motionY * DRAG;
      motionZ = motionZ * DRAG + (rand.nextFloat() - 0.5F) * FLICKER;
    }
    moveEntity(motionX, motionY, motionZ);
  }

  @Override
  public int getBrightnessForRender(float partialTick) {
    return FULL_BRIGHT;
  }

  @Override
  public float getBrightness(float partialTick) {
    return 1;
  }
}
