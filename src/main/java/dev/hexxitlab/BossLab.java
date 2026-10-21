package dev.hexxitlab;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import java.util.logging.Logger;

@Mod(modid = BossLab.MOD_ID, name = "Hexxit Boss Lab", version = "0.1.0")
public final class BossLab {
  public static final String MOD_ID = "hexxitbosslab";
  public static final Logger LOGGER = Logger.getLogger(MOD_ID);

  @Mod.PreInit
  public void initialize(FMLPreInitializationEvent event) {
    LOGGER.setParent(FMLLog.getLogger());
    LOGGER.info("Hexxit Boss Lab initialized");
  }
}
