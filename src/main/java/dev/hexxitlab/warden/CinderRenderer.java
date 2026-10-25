package dev.hexxitlab.warden;

import dev.hexxitlab.Telegraphs;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class CinderRenderer extends RenderLiving {
  private static final float MARK_SCALE = 1.8F;

  public CinderRenderer() {
    super(new CinderModel(), 0.2F);
  }

  /** A dive line is end-on to its victim, so the marked cinder itself swells as the warning. */
  @Override
  protected void preRenderCallback(EntityLiving entity, float partialTick) {
    if (((Cinder) entity).getState() == Cinder.State.MARK) {
      GL11.glScalef(MARK_SCALE, MARK_SCALE, MARK_SCALE);
    }
  }

  @Override
  public void doRenderLiving(
      EntityLiving entity, double x, double y, double z, float yaw, float partialTick) {
    super.doRenderLiving(entity, x, y, z, yaw, partialTick);
    Cinder cinder = (Cinder) entity;
    if (!cinder.isEntityAlive() || cinder.getState() != Cinder.State.MARK) {
      return;
    }
    // The dive line runs from the cinder to its locked target, like the Warden's own warnings.
    Vec3 target = cinder.getTarget();
    double ix = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTick;
    double iy = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTick;
    double iz = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTick;
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glPushMatrix();
    try {
      GL11.glTranslated(x, y + entity.height / 2, z);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glDisable(GL11.GL_LIGHTING);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glDepthMask(false);
      GL11.glLineWidth(2);
      GL11.glColor4f(1, 0.85F, 0.3F, 0.9F);
      Telegraphs.corridor(
          Vec3.createVectorHelper(
              target.xCoord - ix, target.yCoord - iy - entity.height / 2, target.zCoord - iz),
          Cinder.HIT_RADIUS);
    } finally {
      GL11.glPopMatrix();
      GL11.glPopAttrib();
    }
  }

  private static final class CinderModel extends ModelBase {
    private final ModelRenderer core = new ModelRenderer(this, 0, 0);
    private final ModelRenderer shard = new ModelRenderer(this, 0, 0);

    CinderModel() {
      core.addBox(-2, 17, -2, 4, 4, 4);
      shard.addBox(-1, -1, -1, 2, 2, 2);
    }

    @Override
    public void render(
        Entity entity, float limb, float amount, float age, float yaw, float pitch, float scale) {
      boolean marked = ((Cinder) entity).getState() == Cinder.State.MARK;
      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
      GL11.glPushMatrix();
      try {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor3f(1, marked ? 0.9F : 0.5F, marked ? 0.4F : 0.12F);
        core.render(scale);
        for (int i = 0; i < 3; i++) {
          double angle = age * 0.25 + i * Math.PI * 2 / 3;
          shard.setRotationPoint(
              (float) Math.cos(angle) * 4,
              19 + (float) Math.sin(age * 0.2 + i) * 1.5F,
              (float) Math.sin(angle) * 4);
          shard.render(scale);
        }
      } finally {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
      }
    }
  }
}
