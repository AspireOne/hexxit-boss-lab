package dev.hexxitlab;

import cpw.mods.fml.client.registry.RenderingRegistry;
import dev.hexxitlab.sovereign.Sovereign;
import dev.hexxitlab.sovereign.SovereignRenderer;
import dev.hexxitlab.warden.Cinder;
import dev.hexxitlab.warden.CinderRenderer;
import dev.hexxitlab.warden.Warden;
import dev.hexxitlab.warden.WardenRenderer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;

public final class ClientProxy extends CommonProxy {
  @Override
  public void registerRenderers() {
    RenderingRegistry.registerEntityRenderingHandler(LabTarget.class, new TargetRenderer());
    RenderingRegistry.registerEntityRenderingHandler(Sovereign.class, new SovereignRenderer());
    RenderingRegistry.registerEntityRenderingHandler(Warden.class, new WardenRenderer());
    RenderingRegistry.registerEntityRenderingHandler(Cinder.class, new CinderRenderer());
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
