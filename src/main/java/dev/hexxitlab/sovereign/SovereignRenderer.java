package dev.hexxitlab.sovereign;

import dev.hexxitlab.Telegraphs;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.BossStatus;
import org.lwjgl.opengl.GL11;

public final class SovereignRenderer extends RenderLiving {
  public SovereignRenderer() {
    super(new SovereignModel(), 1.2F);
  }

  @Override
  protected void preRenderCallback(EntityLiving entity, float partialTick) {
    GL11.glScalef(1.7F, 1.7F, 1.7F);
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
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glPushMatrix();
    try {
      GL11.glTranslated(x, y + Sovereign.CENTER_HEIGHT, z);
      GL11.glDisable(GL11.GL_TEXTURE_2D);
      GL11.glDisable(GL11.GL_LIGHTING);
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL11.glDepthMask(false);
      GL11.glLineWidth(2);
      Sovereign.Action action = boss.getAction();
      boolean impact =
          action == Sovereign.Action.RECOVER
              && boss.getActionTicks() >= (boss.getImpact() == Sovereign.Action.BLAST ? 23 : 15);
      if (impact) {
        action = boss.getImpact();
        GL11.glLineWidth(5);
      }
      float alpha =
          0.55F + 0.35F * (float) Math.abs(Math.sin((entity.ticksExisted + partialTick) * 0.35));
      if (action == Sovereign.Action.BLAST) {
        GL11.glColor4f(1, 0.35F, 0.05F, alpha);
        Telegraphs.sphere(Sovereign.BLAST_RADIUS);
      } else if (action == Sovereign.Action.LANCE || action == Sovereign.Action.RUSH) {
        GL11.glColor4f(action == Sovereign.Action.LANCE ? 0.7F : 1, 0.2F, 1, alpha);
        Telegraphs.corridor(
            boss.getAim(),
            action == Sovereign.Action.LANCE ? Sovereign.LANCE_RADIUS : Sovereign.RUSH_RADIUS);
      }
    } finally {
      GL11.glPopMatrix();
      GL11.glPopAttrib();
    }
  }

  @Override
  protected void passSpecialRender(EntityLiving entity, double x, double y, double z) {
    Sovereign boss = (Sovereign) entity;
    String text;
    switch (boss.getAction()) {
      case LANCE:
        text = "HEX LANCE - sidestep";
        break;
      case RUSH:
        text = "RUIN RUSH - break away";
        break;
      case BLAST:
        text = "CROWN BREAK - get clear";
        break;
      case RECOVER:
        text = "EXPOSED";
        break;
      default:
        text = "Sovereign - Phase " + boss.getPhase();
        break;
    }
    renderLivingLabel(entity, text, x, y + 0.5, z, 96);
  }

  private static final class SovereignModel extends ModelBase {
    private final ModelRenderer armor = new ModelRenderer(this, 0, 0);
    private final ModelRenderer crown = new ModelRenderer(this, 0, 0);
    private final ModelRenderer core = new ModelRenderer(this, 0, 0);
    private final ModelRenderer blade = new ModelRenderer(this, 0, 0);

    SovereignModel() {
      armor.addBox(-5, -8, -4, 10, 8, 8);
      armor.addBox(-6, 0, -4, 12, 13, 8);
      armor.addBox(-11, 0, -5, 22, 4, 10);
      armor.addBox(-5, 13, -3, 10, 10, 6);
      crown.addBox(-6, -9, -5, 12, 2, 10);
      crown.addBox(-6, -13, -5, 2, 4, 2);
      crown.addBox(4, -13, -5, 2, 4, 2);
      crown.addBox(-1, -15, -5, 2, 6, 2);
      core.addBox(-3, -5, -4.5F, 6, 1, 1);
      core.addBox(-2, 4, -4.5F, 4, 5, 1);
      blade.addBox(-1, -9, -1, 2, 18, 2);
      blade.addBox(-3, 5, -2, 6, 2, 4);
    }

    @Override
    public void render(
        Entity entity, float limb, float amount, float age, float yaw, float pitch, float scale) {
      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
      GL11.glPushMatrix();
      try {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glTranslatef(0, (float) Math.sin(age * 0.06) * 0.06F, 0);
        GL11.glColor3f(0.2F, 0.22F, 0.3F);
        armor.render(scale);
        GL11.glColor3f(0.85F, 0.62F, 0.2F);
        crown.render(scale);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor3f(0.85F, 0.2F, 1);
        core.render(scale);
        for (int i = 0; i < 3; i++) {
          GL11.glPushMatrix();
          GL11.glRotatef(age * 1.5F + i * 120, 0, 1, 0);
          GL11.glTranslatef(1.05F, 0.35F, 0);
          GL11.glRotatef(20, 0, 0, 1);
          blade.render(scale);
          GL11.glPopMatrix();
        }
      } finally {
        GL11.glPopMatrix();
        GL11.glPopAttrib();
      }
    }
  }
}
