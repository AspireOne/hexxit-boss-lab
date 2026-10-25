package dev.hexxitlab;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import dev.hexxitlab.practice.PracticeCheckpoint;
import dev.hexxitlab.sovereign.Sovereign;
import dev.hexxitlab.warden.Cinder;
import dev.hexxitlab.warden.Warden;
import java.util.logging.Logger;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = BossLab.MOD_ID, name = "Hexxit Boss Lab", version = "0.5.0")
@NetworkMod(clientSideRequired = true, serverSideRequired = true)
public final class BossLab {
  public static final String MOD_ID = "hexxitbosslab";
  public static final Logger LOGGER = Logger.getLogger(MOD_ID);

  @SidedProxy(clientSide = "dev.hexxitlab.ClientProxy", serverSide = "dev.hexxitlab.CommonProxy")
  public static CommonProxy proxy;

  @Mod.PreInit
  public void initialize(FMLPreInitializationEvent event) {
    LOGGER.setParent(FMLLog.getLogger());
    EntityRegistry.registerModEntity(LabTarget.class, "LabTarget", 1, this, 80, 1, true);
    EntityRegistry.registerModEntity(Sovereign.class, "Sovereign", 2, this, 160, 1, true);
    EntityRegistry.registerModEntity(Warden.class, "Warden", 3, this, 160, 1, true);
    EntityRegistry.registerModEntity(Cinder.class, "Cinder", 4, this, 80, 1, true);
    MinecraftForge.EVENT_BUS.register(new TargetDrops());
    if (Boolean.getBoolean("hexxitlab.enabled")) {
      PracticeCheckpoint practice = new PracticeCheckpoint();
      MinecraftForge.EVENT_BUS.register(practice);
      TickRegistry.registerTickHandler(practice, Side.SERVER);
    }
    proxy.registerRenderers();
    LOGGER.info("Hexxit Boss Lab initialized");
  }

  @Mod.ServerStarting
  public void serverStarting(FMLServerStartingEvent event) {
    if (Boolean.getBoolean("hexxitlab.enabled")) {
      event.registerServerCommand(new LabCommand());
    }
  }
}
