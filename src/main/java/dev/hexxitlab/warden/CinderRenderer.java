package dev.hexxitlab.warden;

import dev.hexxitlab.Telegraphs;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class CinderRenderer extends RenderLiving {
  private static final float SHADOW = 0.2F;
  private static final float MARK_SCALE = 1.8F;
  private static final float SWELL_TICKS = 4;
  private static final float FULL_BRIGHT = 240;
  private static final float[] DIVE = {1, 0.8F, 0.25F};
  private static final float[] HALO = {1, 0.45F, 0.1F};
  private static final float[] FLARE = {1, 0.9F, 0.5F};
  private static final double HALO_RADIUS = 0.55;
  private static final double FLARE_RADIUS = 1.3;
  private static final double STREAK = 2.5;
  private static final int HALO_SEGMENTS = 20;
  private static final int RAYS = 6;

  public CinderRenderer() {
    super(new CinderModel(), SHADOW);
  }

  /** A dive line is end-on to its victim, so the marked cinder itself swells as the warning. */
  @Override
  protected void preRenderCallback(EntityLiving entity, float partialTick) {
    Cinder cinder = (Cinder) entity;
    if (cinder.getState() == Cinder.State.MARK) {
      float swell =
          1 + (MARK_SCALE - 1) * Math.min(1, (cinder.getStateAge() + partialTick) / SWELL_TICKS);
      GL11.glScalef(swell, swell, swell);
    }
  }

  @Override
  public void doRenderLiving(
      EntityLiving entity, double x, double y, double z, float yaw, float partialTick) {
    super.doRenderLiving(entity, x, y, z, yaw, partialTick);
    Cinder cinder = (Cinder) entity;
    if (!cinder.isEntityAlive()) {
      return;
    }
    Cinder.State state = cinder.getState();
    float time = cinder.ticksExisted + partialTick;
    double ix = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTick;
    double iy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTick;
    double iz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTick;
    GL11.glPushMatrix();
    Telegraphs.begin();
    try {
      GL11.glTranslated(x, y + entity.height / 2, z);
      Vec3 target = cinder.getTarget();
      Vec3 aim =
          Vec3.createVectorHelper(
              target.xCoord - ix, target.yCoord - iy - entity.height / 2, target.zCoord - iz);
      if (state == Cinder.State.MARK) {
        // Locked from the start; the dive runs on past its mark, so the warning does too.
        double length = aim.lengthVector();
        Vec3 path =
            length < 1e-3
                ? aim
                : Vec3.createVectorHelper(
                    aim.xCoord * (1 + Cinder.DIVE_OVERSHOOT / length),
                    aim.yCoord * (1 + Cinder.DIVE_OVERSHOOT / length),
                    aim.zCoord * (1 + Cinder.DIVE_OVERSHOOT / length));
        Telegraphs.corridor(
            path,
            Cinder.HIT_RADIUS,
            false,
            new Telegraphs.Warning(
                DIVE,
                Telegraphs.Style.LOCKED,
                (cinder.getStateAge() + partialTick) / Cinder.MARK_TICKS,
                time));
      } else if (state == Cinder.State.DIVE) {
        streak(aim.normalize());
      }
      halo(state == Cinder.State.MARK, time);
    } finally {
      Telegraphs.end();
      GL11.glPopMatrix();
    }
  }

  /** A soft additive glow facing the camera; a marked cinder flares with turning rays. */
  private void halo(boolean marked, float time) {
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    GL11.glRotatef(-renderManager.playerViewY, 0, 1, 0);
    GL11.glRotatef(renderManager.playerViewX, 1, 0, 0);
    float flicker = 0.85F + 0.15F * (float) Math.sin(time * 1.7);
    double radius = (marked ? FLARE_RADIUS : HALO_RADIUS) * flicker;
    float[] tone = marked ? FLARE : HALO;
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    GL11.glColor4f(tone[0], tone[1], tone[2], marked ? 0.75F : 0.5F);
    GL11.glVertex3d(0, 0, 0);
    GL11.glColor4f(tone[0], tone[1], tone[2], 0);
    for (int i = 0; i <= HALO_SEGMENTS; i++) {
      double angle = i * Math.PI * 2 / HALO_SEGMENTS;
      GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
    }
    GL11.glEnd();
    if (!marked) {
      return;
    }
    GL11.glRotatef(time * 6, 0, 0, 1);
    GL11.glBegin(GL11.GL_TRIANGLES);
    for (int i = 0; i < RAYS; i++) {
      double angle = i * Math.PI * 2 / RAYS;
      double spread = 0.12;
      GL11.glColor4f(FLARE[0], FLARE[1], FLARE[2], 0.8F);
      GL11.glVertex3d(Math.cos(angle - spread) * 0.2, Math.sin(angle - spread) * 0.2, 0);
      GL11.glVertex3d(Math.cos(angle + spread) * 0.2, Math.sin(angle + spread) * 0.2, 0);
      GL11.glColor4f(FLARE[0], FLARE[1], FLARE[2], 0);
      GL11.glVertex3d(Math.cos(angle) * radius * 1.6, Math.sin(angle) * radius * 1.6, 0);
    }
    GL11.glEnd();
  }

  /** A fading streak behind a diving cinder. */
  private static void streak(Vec3 direction) {
    float[] widths = {7, 4, 2};
    for (int i = 0; i < widths.length; i++) {
      GL11.glLineWidth(widths[i]);
      GL11.glBegin(GL11.GL_LINES);
      GL11.glColor4f(DIVE[0], DIVE[1] - i * 0.2F, DIVE[2], 0.7F);
      GL11.glVertex3d(0, 0, 0);
      GL11.glColor4f(HALO[0], HALO[1], HALO[2], 0);
      double length = STREAK * (1 - i * 0.25);
      GL11.glVertex3d(
          -direction.xCoord * length, -direction.yCoord * length, -direction.zCoord * length);
      GL11.glEnd();
    }
  }

  /** A blazing core inside tumbling plates of charcoal crust that burst open when it marks. */
  private static final class CinderModel extends ModelBase {
    private static final int PLATES = 4;
    private static final int SPARKS = 3;
    private static final float CENTRE = 19;
    private final ModelRenderer core = new ModelRenderer(this, 0, 0);
    private final ModelRenderer plate = new ModelRenderer(this, 0, 0);
    private final ModelRenderer spark = new ModelRenderer(this, 0, 0);

    CinderModel() {
      core.addBox(-1.5F, CENTRE - 1.5F, -1.5F, 3, 3, 3);
      plate.addBox(-1.5F, -1.5F, -0.5F, 3, 3, 1);
      spark.addBox(-0.5F, -0.5F, -0.5F, 1, 1, 1);
    }

    @Override
    public void render(
        Entity entity, float limb, float amount, float age, float yaw, float pitch, float scale) {
      boolean overlay = !GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
      Cinder cinder = (Cinder) entity;
      boolean marked = cinder.getState() == Cinder.State.MARK;
      float open =
          marked
              ? Math.min(1, (cinder.getStateAge() + age - entity.ticksExisted) / SWELL_TICKS)
              : 0;
      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
      GL11.glPushMatrix();
      try {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        if (!overlay) {
          OpenGlHelper.setLightmapTextureCoords(
              OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
          float pulse = 0.85F + 0.15F * (float) Math.sin(age * 0.9);
          GL11.glColor3f(1, (marked ? 0.95F : 0.7F) * pulse, (marked ? 0.6F : 0.25F) * pulse);
        }
        core.render(scale);
        for (int i = 0; i < PLATES; i++) {
          GL11.glPushMatrix();
          GL11.glTranslatef(0, CENTRE * scale, 0);
          GL11.glRotatef(age * 4 + i * 90, 0.3F, 1, 0.2F);
          GL11.glTranslatef(0, 0, (1.9F + 2.5F * open) * scale);
          GL11.glRotatef(age * 9 + i * 37, 1, 0, 0);
          if (!overlay) {
            float ember = 0.12F + 0.1F * (float) Math.sin(age * 0.5 + i);
            GL11.glColor3f(0.16F + ember, 0.1F + ember * 0.3F, 0.08F);
          }
          plate.render(scale);
          GL11.glPopMatrix();
        }
        if (!overlay) {
          GL11.glColor3f(1, marked ? 0.9F : 0.55F, 0.2F);
        }
        for (int i = 0; i < SPARKS; i++) {
          double angle = age * 0.25 + i * Math.PI * 2 / SPARKS;
          spark.setRotationPoint(
              (float) Math.cos(angle) * (4 + 2 * open),
              CENTRE + (float) Math.sin(age * 0.2 + i) * 1.5F,
              (float) Math.sin(angle) * (4 + 2 * open));
          spark.render(scale);
        }
      } finally {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
      }
    }
  }
}
