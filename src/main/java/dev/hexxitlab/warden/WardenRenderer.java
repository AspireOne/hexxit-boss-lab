package dev.hexxitlab.warden;

import dev.hexxitlab.Telegraphs;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

public final class WardenRenderer extends RenderLiving {
  private static final float[] EMBER = {1, 0.45F, 0.1F, 1};
  private static final float[] ASH = {1, 0.2F, 0.08F, 1};
  private static final float[] CHAIN = {0.85F, 0.75F, 0.6F, 1};
  private static final float GROUND_LIFT = 0.06F;
  private static final float TRACKING_ALPHA = 0.3F;

  public WardenRenderer() {
    super(new WardenModel(), 1.1F);
  }

  @Override
  protected void preRenderCallback(EntityLiving entity, float partialTick) {
    GL11.glScalef(1.6F, 1.6F, 1.6F);
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
    Warden.Action action = boss.getAction();
    // A tracking warning is faint; it flares and pulses once it locks.
    float pulse =
        boss.isCommitted() || !Warden.isStrike(action)
            ? 0.6F + 0.35F * (float) Math.abs(Math.sin((entity.ticksExisted + partialTick) * 0.35))
            : TRACKING_ALPHA;
    // Locked points are world coordinates; draw them relative to the interpolated entity.
    Vec3 strike = boss.getStrike();
    double sx =
        x
            + strike.xCoord
            - (entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTick);
    double sy =
        y
            + strike.yCoord
            - (entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTick);
    double sz =
        z
            + strike.zCoord
            - (entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTick);
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glPushMatrix();
    try {
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glDisable(GL11.GL_LIGHTING);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glDepthMask(false);
      GL11.glLineWidth(2);
      if (action == Warden.Action.CLEAVE || action == Warden.Action.BACKHAND) {
        boolean cleave = action == Warden.Action.CLEAVE;
        GL11.glTranslated(x, y + GROUND_LIFT, z);
        Telegraphs.sector(
            boss.getStrikeYaw() + (cleave ? 0 : 180),
            cleave ? Warden.CLEAVE_ARC : Warden.BACKHAND_ARC,
            cleave ? Warden.CLEAVE_RADIUS : Warden.BACKHAND_RADIUS,
            tint(EMBER, pulse));
      } else if (action == Warden.Action.SLAM
          || action == Warden.Action.LEAP
          || action == Warden.Action.AIRBORNE) {
        GL11.glTranslated(sx, sy + GROUND_LIFT, sz);
        Telegraphs.disc(
            action == Warden.Action.SLAM ? Warden.SLAM_RADIUS : Warden.LEAP_RADIUS,
            tint(EMBER, pulse));
      } else if (action == Warden.Action.HOOK) {
        GL11.glTranslated(x, y + Warden.CHEST_HEIGHT, z);
        GL11.glColor4f(CHAIN[0], CHAIN[1], CHAIN[2], pulse);
        Telegraphs.corridor(
            Vec3.createVectorHelper(sx - x, sy - y - Warden.CHEST_HEIGHT, sz - z),
            Warden.HOOK_RADIUS);
      } else if (action == Warden.Action.RECLAIM || action == Warden.Action.NOVA) {
        GL11.glTranslated(x, y + Warden.CHEST_HEIGHT, z);
        GL11.glColor4f(ASH[0], ASH[1], ASH[2], pulse);
        if (action == Warden.Action.NOVA) {
          GL11.glLineWidth(4);
        }
        Telegraphs.sphere(boss.getNovaRadius());
      }
    } finally {
      GL11.glPopMatrix();
      GL11.glPopAttrib();
    }
  }

  private static float[] tint(float[] rgb, float alpha) {
    return new float[] {rgb[0], rgb[1], rgb[2], alpha};
  }

  @Override
  protected void passSpecialRender(EntityLiving entity, double x, double y, double z) {
    Warden boss = (Warden) entity;
    String text;
    switch (boss.getAction()) {
      case CLEAVE:
        text = "CLEAVE - roll behind or back off";
        break;
      case BACKHAND:
        text = "BACKHAND - back off";
        break;
      case SLAM:
        text = "SLAM - leave the circle";
        break;
      case LEAP:
      case AIRBORNE:
        text = "LEAP - leave the circle";
        break;
      case HOOK:
        text = "CHAIN HOOK - sidestep";
        break;
      case REEL:
        text = "HOOKED";
        break;
      case RECOVER:
        text = "EXPOSED";
        break;
      case KINDLE:
        text = "HARROWING - warded";
        break;
      case RECLAIM:
        text = "HARROWING - shoot the cinders";
        break;
      case NOVA:
        text = "ASH NOVA - get clear";
        break;
      default:
        text =
            "Ashen Warden - Phase "
                + boss.getPhase()
                + (boss.getPhase() == 3 ? " - guard broken" : "");
        break;
    }
    renderLivingLabel(entity, text, x, y + 0.5, z, 96);
  }

  /** Coloured geometry instead of a texture, matching the Sovereign's lab style. */
  private static final class WardenModel extends ModelBase {
    private final ModelRenderer head = part(0, -1, 0);
    private final ModelRenderer visor = part(0, -1, 0);
    private final ModelRenderer body = part(0, 0, 0);
    private final ModelRenderer trim = part(0, 0, 0);
    private final ModelRenderer core = part(0, 0, 0);
    private final ModelRenderer swordArm = part(-8.5F, 1, 0);
    private final ModelRenderer blade = part(0, 11, -1);
    private final ModelRenderer shieldArm = part(8.5F, 1, 0);
    private final ModelRenderer shield = part(0, 6, -3);
    private final ModelRenderer rightLeg = part(-3.2F, 12, 0);
    private final ModelRenderer leftLeg = part(3.2F, 12, 0);

    WardenModel() {
      head.addBox(-4, -9, -4, 8, 9, 8);
      head.addBox(-1, -12, -4.5F, 2, 3, 9);
      visor.addBox(-3, -6, -4.4F, 6, 1, 1);
      body.addBox(-6, -1, -3.5F, 12, 13, 7);
      trim.addBox(-11, -3, -4.5F, 6, 5, 9);
      trim.addBox(5, -3, -4.5F, 6, 5, 9);
      trim.addBox(-6.5F, 10, -4, 13, 4, 8);
      core.addBox(-2, 2, -4, 4, 4, 1);
      swordArm.addBox(-2.5F, -2, -2.5F, 5, 14, 5);
      blade.addBox(-0.5F, -0.5F, -1, 1, 1, 4);
      blade.addBox(-3, -1, -3, 6, 2, 2);
      blade.addBox(-1, -1, -25, 2, 2, 22);
      shieldArm.addBox(-2.5F, -2, -2.5F, 5, 14, 5);
      shield.addBox(-5, -6, -2, 10, 18, 2);
      rightLeg.addBox(-3, 0, -3, 6, 12, 6);
      leftLeg.addBox(-3, 0, -3, 6, 12, 6);
    }

    private ModelRenderer part(float x, float y, float z) {
      ModelRenderer part = new ModelRenderer(this, 0, 0);
      part.setRotationPoint(x, y, z);
      return part;
    }

    @Override
    public void render(
        Entity entity, float limb, float amount, float age, float yaw, float pitch, float scale) {
      Warden boss = (Warden) entity;
      pose(boss.getAction(), limb, amount);
      head.rotateAngleY = visor.rotateAngleY = yaw / (180F / (float) Math.PI);
      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
      GL11.glPushMatrix();
      try {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor3f(0.33F, 0.31F, 0.3F);
        head.render(scale);
        body.render(scale);
        rightLeg.render(scale);
        leftLeg.render(scale);
        swordArm.render(scale);
        shieldArm.render(scale);
        GL11.glColor3f(0.18F, 0.16F, 0.16F);
        trim.render(scale);
        if (boss.getPhase() < 3) {
          GL11.glColor3f(0.25F, 0.22F, 0.2F);
          renderChild(shieldArm, shield, scale);
        }
        GL11.glColor3f(0.72F, 0.72F, 0.76F);
        renderChild(swordArm, blade, scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor3f(1, boss.getPhase() == 3 ? 0.3F : 0.5F, 0.12F);
        visor.render(scale);
        core.render(scale);
      } finally {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
      }
    }

    /** Renders a part in its parent's frame without coupling their colours. */
    private static void renderChild(ModelRenderer parent, ModelRenderer child, float scale) {
      GL11.glPushMatrix();
      parent.postRender(scale);
      child.render(scale);
      GL11.glPopMatrix();
    }

    private void pose(Warden.Action action, float limb, float amount) {
      float stride = (float) Math.cos(limb * 0.6662F) * 0.9F * amount;
      rightLeg.rotateAngleX = stride;
      leftLeg.rotateAngleX = -stride;
      swordArm.rotateAngleX = -0.35F;
      swordArm.rotateAngleY = 0;
      shieldArm.rotateAngleX = -0.45F;
      shieldArm.rotateAngleY = 0;
      switch (action) {
        case CLEAVE:
          swordArm.rotateAngleX = -1.3F;
          swordArm.rotateAngleY = 0.9F;
          break;
        case BACKHAND:
          swordArm.rotateAngleX = -1.2F;
          swordArm.rotateAngleY = -1.1F;
          break;
        case SLAM:
        case LEAP:
        case AIRBORNE:
          swordArm.rotateAngleX = -2.9F;
          break;
        case HOOK:
        case REEL:
          shieldArm.rotateAngleX = -1.5F;
          break;
        case RECOVER:
          swordArm.rotateAngleX = 0.25F;
          break;
        case KINDLE:
        case RECLAIM:
        case NOVA:
          swordArm.rotateAngleX = shieldArm.rotateAngleX = -3;
          break;
        default:
          break;
      }
    }
  }
}
