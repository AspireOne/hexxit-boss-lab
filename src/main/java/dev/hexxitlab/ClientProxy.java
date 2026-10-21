package dev.hexxitlab;

import cpw.mods.fml.client.registry.RenderingRegistry;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;

public final class ClientProxy extends CommonProxy {
  @Override
  public void registerRenderers() {
    RenderingRegistry.registerEntityRenderingHandler(LabTarget.class, new TargetRenderer());
  }

  private static final class TargetRenderer extends RenderLiving {
    TargetRenderer() {
      super(new ModelBiped(), 0.5F);
    }

    @Override
    protected void passSpecialRender(EntityLiving entity, double x, double y, double z) {
      renderLivingLabel(entity, "Lab Target " + entity.getHealth() + "/40", x, y, z, 32);
    }
  }
}
