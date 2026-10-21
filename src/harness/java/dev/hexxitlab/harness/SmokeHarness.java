package dev.hexxitlab.harness;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import dev.hexxitlab.BossLab;
import dev.hexxitlab.LabTarget;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Properties;
import java.util.logging.Level;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.World;
import net.minecraft.world.storage.ThreadedFileIOBase;

/** Explicitly enabled development mod; never included in the release artifact. */
@Mod(
    modid = "hexxitlabharness",
    name = "Boss Lab Smoke Harness",
    version = "0.1.0",
    dependencies = "required-after:hexxitbosslab")
public final class SmokeHarness {
  private static final String WORLD = "Kratom - Boss Lab";
  private static final String RUN_TAG = "BossLabSmoke";
  private final Properties request = new Properties();
  private volatile int targetId = -1;
  private volatile int observedHealth = -1;
  private volatile boolean finished;
  private volatile String failure;
  private volatile int step;
  private volatile boolean imageSaved;
  private int renderFrames;
  private boolean launched;
  private boolean resultWritten;
  private boolean shutdownRequested;
  private int serverTicks;
  private EntityLiving target;
  private File gameDirectory;

  @Mod.PostInit
  public void initialize(FMLPostInitializationEvent event) throws IOException {
    gameDirectory = Minecraft.getMinecraft().mcDataDir;
    File source = new File(gameDirectory, "bosslab-smoke.properties");
    if (!source.isFile()) {
      return;
    }
    if (!Boolean.getBoolean("hexxitlab.enabled")
        || !new File(gameDirectory.getParentFile(), ".hexxit-boss-lab.json").isFile()) {
      throw new IllegalStateException("Smoke harness requires the marked lab instance");
    }
    FileInputStream input = new FileInputStream(source);
    try {
      request.load(input);
    } finally {
      input.close();
    }
    if (!WORLD.equals(request.getProperty("world"))
        || !request.getProperty("run", "").matches("[A-Za-z0-9-]+")
        || !("create".equals(request.getProperty("phase"))
            || "reload".equals(request.getProperty("phase")))) {
      throw new IllegalArgumentException("Invalid smoke request");
    }
    TickRegistry.registerTickHandler(new ClientTicks(), Side.CLIENT);
    TickRegistry.registerTickHandler(new ServerTicks(), Side.SERVER);
    BossLab.LOGGER.info("Smoke harness enabled: " + request.getProperty("phase"));
  }

  private void serverTick() {
    MinecraftServer server = MinecraftServer.getServer();
    if (server == null || server.getConfigurationManager().playerEntityList.isEmpty() || finished) {
      return;
    }
    EntityPlayerMP player =
        (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
    serverTicks++;
    if (serverTicks > 2400) {
      throw new IllegalStateException(
          "Timed out waiting for client entity synchronization at step " + step);
    }
    World world = player.worldObj;
    if (step == 0) {
      if ("create".equals(request.getProperty("phase"))) {
        server.getCommandManager().executeCommand(player, "bosslab clear");
        server.getCommandManager().executeCommand(player, "bosslab spawn");
        for (Object candidate : world.loadedEntityList) {
          if (candidate instanceof LabTarget && !((LabTarget) candidate).isDead) {
            target = (LabTarget) candidate;
          }
        }
        if (target == null || target.getHealth() != 40) {
          throw new IllegalStateException("Command must spawn a healthy target");
        }
        target.getEntityData().setString(RUN_TAG, request.getProperty("run"));
      } else {
        for (Object candidate : world.loadedEntityList) {
          if (candidate instanceof LabTarget
              && request
                  .getProperty("run")
                  .equals(((Entity) candidate).getEntityData().getString(RUN_TAG))) {
            require(target == null, "Only one persisted smoke target expected");
            target = (LabTarget) candidate;
          }
        }
        // Chunks around the player can take several ticks to load.
        if (target == null && serverTicks < 400) {
          return;
        }
        if (target == null || target.getHealth() != 33) {
          throw new IllegalStateException("Target identity and health must survive reload");
        }
      }
      targetId = target.entityId;
      step = 1;
    } else if (step == 1 && observedHealth == target.getHealth()) {
      if ("create".equals(request.getProperty("phase"))) {
        require(
            target.attackEntityFrom(DamageSource.causePlayerDamage(player), 7),
            "Player damage must be accepted");
        require(target.getHealth() == 33, "Seven damage must leave 33 health");
        step = 2;
      } else {
        require(
            target.attackEntityFrom(DamageSource.causePlayerDamage(player), 100),
            "Lethal damage must be accepted");
        require(target.getHealth() <= 0, "Lethal damage must kill the target");
        server.getCommandManager().executeCommand(player, "bosslab spawn");
        EntityPig unrelated = new EntityPig(world);
        unrelated.setPosition(player.posX, player.posY + 2, player.posZ);
        require(world.spawnEntityInWorld(unrelated), "Unrelated sentinel must spawn");
        server.getCommandManager().executeCommand(player, "bosslab clear");
        for (Object candidate : world.loadedEntityList) {
          require(
              !(candidate instanceof LabTarget) || ((LabTarget) candidate).isDead,
              "Clear must remove every loaded lab target");
        }
        require(!unrelated.isDead, "Clear must preserve unrelated entities");
        unrelated.setDead();
        complete(server);
      }
    } else if (step == 2 && observedHealth == 33 && imageSaved) {
      complete(server);
    }
  }

  private void complete(MinecraftServer server) {
    server.getConfigurationManager().saveAllPlayerData();
    finished = true;
    BossLab.LOGGER.info("Smoke assertions passed: " + request.getProperty("phase"));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  private void writeResult() throws IOException {
    Properties result = new Properties();
    result.setProperty("status", failure == null ? "PASS" : "FAIL");
    result.setProperty("run", request.getProperty("run"));
    result.setProperty("phase", request.getProperty("phase"));
    result.setProperty("message", failure == null ? "Runtime assertions passed" : failure);
    java.io.InputStream metadata = BossLab.class.getResourceAsStream("/hexxitlab-build.properties");
    if (metadata == null) {
      throw new IOException("Missing build identity");
    }
    try {
      result.load(metadata);
    } finally {
      metadata.close();
    }
    File destination =
        new File(gameDirectory, "bosslab-" + request.getProperty("phase") + "-result.properties");
    File temporary = new File(destination.getPath() + ".tmp");
    FileOutputStream output = new FileOutputStream(temporary);
    try {
      result.store(output, "Hexxit Boss Lab runtime evidence");
    } finally {
      output.close();
    }
    if (!temporary.renameTo(destination)) {
      throw new IOException("Cannot publish smoke result");
    }
  }

  private final class ClientTicks implements ITickHandler {
    public void tickStart(EnumSet<TickType> types, Object... data) {}

    public void tickEnd(EnumSet<TickType> types, Object... data) {
      Minecraft minecraft = Minecraft.getMinecraft();
      try {
        if (types.contains(TickType.RENDER)) {
          if (step == 2 && observedHealth == 33 && !imageSaved && minecraft.theWorld != null) {
            Entity entity = minecraft.theWorld.getEntityByID(targetId);
            if (entity != null && minecraft.thePlayer != null) {
              double dx = entity.posX - minecraft.thePlayer.posX;
              double dz = entity.posZ - minecraft.thePlayer.posZ;
              double dy =
                  entity.posY
                      + 0.9
                      - (minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight());
              minecraft.thePlayer.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
              minecraft.thePlayer.rotationPitch =
                  (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
              if (++renderFrames >= 10) {
                File evidence = new File(gameDirectory, "bosslab-evidence");
                if (!evidence.isDirectory() && !evidence.mkdirs()) {
                  throw new IOException("Cannot create screenshot evidence directory");
                }
                BossLab.LOGGER.info(
                    ScreenShotHelper.saveScreenshot(
                        evidence, minecraft.displayWidth, minecraft.displayHeight));
                imageSaved = true;
              }
            }
          }
          return;
        }
        if (!launched && minecraft.currentScreen instanceof GuiMainMenu) {
          launched = true;
          minecraft.launchIntegratedServer(WORLD, WORLD, null);
        }
        if (minecraft.theWorld != null && targetId >= 0) {
          Entity entity = minecraft.theWorld.getEntityByID(targetId);
          if (entity instanceof LabTarget) {
            observedHealth = ((EntityLiving) entity).getHealth();
          }
        }
        if (finished && !resultWritten) {
          MinecraftServer server = minecraft.getIntegratedServer();
          if (server != null && !server.isServerStopped()) {
            if (!shutdownRequested) {
              // Keep the client connection alive while legacy server mods finish saving.
              server.initiateShutdown();
              shutdownRequested = true;
            }
            return;
          }
          resultWritten = true;
          // Server save hooks have finished before the client network connection is cleared.
          minecraft.loadWorld(null);
          ThreadedFileIOBase.threadedIOInstance.waitForFinish();
          writeResult();
          minecraft.shutdown();
        }
      } catch (Throwable error) {
        BossLab.LOGGER.log(Level.SEVERE, "Client smoke failed", error);
        failure = error.toString();
        finished = true;
      }
    }

    public EnumSet<TickType> ticks() {
      return EnumSet.of(TickType.CLIENT, TickType.RENDER);
    }

    public String getLabel() {
      return "bosslab-smoke-client";
    }
  }

  private final class ServerTicks implements ITickHandler {
    public void tickStart(EnumSet<TickType> types, Object... data) {}

    public void tickEnd(EnumSet<TickType> types, Object... data) {
      try {
        serverTick();
      } catch (Throwable error) {
        BossLab.LOGGER.log(Level.SEVERE, "Server smoke failed", error);
        failure = error.toString();
        finished = true;
      }
    }

    public EnumSet<TickType> ticks() {
      return EnumSet.of(TickType.SERVER);
    }

    public String getLabel() {
      return "bosslab-smoke-server";
    }
  }
}
