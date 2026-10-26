package dev.hexxitlab.warden;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

/** A glowing mote that cools from white-hot through orange to a dull red as it drifts. */
final class Ember extends EntityFX {
  // The small soft dot in the vanilla particle sheet.
  private static final int DOT = 7;
  private static final int FULL_BRIGHT = 0xF000F0;
  private static final float DRAG = 0.94F;
  private static final float FLICKER = 0.012F;
  private final float heat;

  /** Heat runs from 0, a dull ember, to 1, white-hot. */
  Ember(
      World world,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      float size,
      float heat,
      int life) {
    super(world, x, y, z);
    motionX = vx;
    motionY = vy;
    motionZ = vz;
    particleScale = size;
    particleMaxAge = life;
    particleGravity = -0.05F;
    noClip = true;
    this.heat = heat;
    setParticleTextureIndex(DOT);
    color(0);
  }

  private void color(float age) {
    float t = Math.max(0, heat - age);
    particleRed = 1;
    particleGreen = 0.25F + 0.7F * t;
    particleBlue = 0.05F + 0.6F * t * t;
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
    motionX = motionX * DRAG + (rand.nextFloat() - 0.5F) * FLICKER;
    motionY = motionY * DRAG - 0.04 * particleGravity;
    motionZ = motionZ * DRAG + (rand.nextFloat() - 0.5F) * FLICKER;
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
