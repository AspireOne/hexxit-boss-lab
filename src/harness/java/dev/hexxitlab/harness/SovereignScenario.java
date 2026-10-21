package dev.hexxitlab.harness;

import dev.hexxitlab.BossLab;
import dev.hexxitlab.sovereign.Sovereign;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.EventPriority;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

/**
 * Actual pack integration: geometry probes cancel at the damage boundary; equipment probes do not.
 */
public final class SovereignScenario {
  private static final String TAG = "SovereignSmoke";
  private static final String[] CASES = {
    "lance-hit", "lance-dodge", "lance-cover", "rush-hit", "rush-dodge",
    "blast-hit", "blast-escape", "blast-cover", "equipped-hit", "phoenix-rescue"
  };
  private final String run;
  private final String phase;
  private final File directory;
  private final EnumSet<Sovereign.Action> pictures = EnumSet.noneOf(Sovereign.Action.class);
  private volatile int entityId = -1;
  private volatile int observedHealth = -1;
  private volatile int observedAction = -1;
  private volatile double observedAimX;
  private volatile double observedAimY;
  private volatile double observedAimZ;
  private volatile int pictureCount;
  private Sovereign boss;
  private NBTTagCompound originalPlayer;
  private int originalDifficulty;
  private double x;
  private double y;
  private double z;
  private int stage;
  private int ticks;
  private int testCase;
  private int hits;
  private int rawDamage;
  private int beforeHealth;
  private int beforeFeathers;
  private int featherId;
  private boolean protect;
  private boolean warned;
  private boolean synchronizedWarning;
  private boolean wall;
  private Vec3 lockedAim;
  private Vec3 rushStart;
  private double playerZ;
  private EntityPlayerMP player;

  SovereignScenario(String run, String phase, File directory) {
    this.run = run;
    this.phase = phase;
    this.directory = directory;
    MinecraftForge.EVENT_BUS.register(this);
  }

  @ForgeSubscribe(priority = EventPriority.HIGHEST)
  public void observeAttack(LivingAttackEvent event) {
    if (event.entityLiving == player && event.source.getEntity() == boss) {
      hits++;
      rawDamage = event.ammount;
      require(warned, "Boss damage must follow a warning");
      if (protect) {
        event.setCanceled(true);
      }
    }
  }

  boolean tick(MinecraftServer server, EntityPlayerMP current) {
    player = current;
    ticks++;
    World world = player.worldObj;
    if (stage == 0) {
      originalPlayer = new NBTTagCompound();
      player.writeEntityToNBT(originalPlayer);
      originalDifficulty = world.difficultySetting;
      world.difficultySetting = 2;
      x = player.posX;
      y = 220;
      z = player.posZ;
      playerZ = z;
      player.capabilities.allowFlying = true;
      player.capabilities.isFlying = true;
      player.capabilities.disableDamage = false;
      player.sendPlayerAbilities();
      pinPlayer();
      if ("reload".equals(phase)) {
        stage = 20;
      } else {
        server.getCommandManager().executeCommand(player, "bosslab clear");
        server.getCommandManager().executeCommand(player, "bosslab sovereign");
        boss = findBoss(world, null);
        if (boss == null || boss.getHealth() != 1200) {
          throw new IllegalStateException("Summon must create a healthy boss");
        }
        server.getCommandManager().executeCommand(player, "bosslab sovereign");
        require(countBosses(world) == 1, "Duplicate summon must be rejected");
        boss.setPosition(x + 8, y, z);
        entityId = boss.entityId;
        ItemStack sword = player.inventory.getStackInSlot(0);
        require(sword != null && sword.isItemEnchanted(), "Copied enchanted sword required");
        player.inventory.currentItem = 0;
        player.attackTargetEntityWithCurrentItem(boss);
        require(boss.getHealth() < 1200, "Real equipped sword must damage boss");
        BossLab.LOGGER.info("Sovereign equipment: sword damage=" + (1200 - boss.getHealth()));
        boss.hurtResistantTime = 0;
        boss.setEntityHealth(1200);
        face(player, boss);
        ItemStack bow = player.inventory.getStackInSlot(4);
        if (bow == null || !bow.getItem().getClass().getName().contains("bowDiamond")) {
          throw new IllegalStateException("Copied Crystal Bow required");
        }
        player.inventory.currentItem = 4;
        bow.onPlayerStoppedUsing(world, player, bow.getMaxItemUseDuration() - 6);
        stage = 1;
        ticks = 0;
      }
      return false;
    }
    pinPlayer();
    if (stage == 1) {
      if (boss.getHealth() < 1200) {
        BossLab.LOGGER.info("Sovereign equipment: Crystal Bow damage=" + (1200 - boss.getHealth()));
        featherId = findItem("PhoenixFeather");
        require(featherId >= 0, "Copied Phoenix Feathers required");
        startCase(world);
        stage = 2;
      } else {
        require(ticks < 80, "Real Crystal Bow projectile must hit boss");
      }
    } else if (stage == 2) {
      Sovereign.Action action = boss.getAction();
      Sovereign.Action expected = expectedAttack();
      if (action == expected) {
        if (!warned) {
          require(hits == 0 && boss.getActionTicks() >= 20, "Full warning must precede damage");
          warned = true;
          lockedAim = boss.getAim();
          rushStart = Vec3.createVectorHelper(boss.posX, boss.posY, boss.posZ);
          if (testCase == 1 || testCase == 4) {
            playerZ = z + 8;
          }
          if (testCase == 6) {
            playerZ = z + 22;
          }
          if (testCase == 2 || testCase == 7) {
            buildWall(world);
          }
        }
        require(
            lockedAim.distanceTo(boss.getAim()) < 0.0001,
            "Warning direction must stay locked after a dodge");
        if (observedAction == action.ordinal()
            && Math.abs(observedAimX - lockedAim.xCoord) < 0.001
            && Math.abs(observedAimY - lockedAim.yCoord) < 0.001
            && Math.abs(observedAimZ - lockedAim.zCoord) < 0.001) {
          synchronizedWarning = true;
        }
      }
      if (warned && action == Sovereign.Action.RECOVER) {
        require(
            synchronizedWarning, "Client must receive the same attack geometry before execution");
        boolean shouldHit = testCase == 0 || testCase == 3 || testCase == 5 || testCase >= 8;
        require(
            hits == (shouldHit ? 1 : 0),
            CASES[testCase] + ": expected " + (shouldHit ? 1 : 0) + " hit, saw " + hits);
        if (shouldHit) {
          int expectedDamage = testCase == 3 ? 100 : testCase == 5 ? 36 : 48;
          require(rawDamage == expectedDamage, "Attack must deliver its documented raw damage");
        }
        if (testCase == 3 || testCase == 4) {
          require(
              rushStart.distanceTo(Vec3.createVectorHelper(boss.posX, boss.posY, boss.posZ))
                  <= lockedAim.lengthVector() + 0.001,
              "Rush must not overshoot its warning");
        }
        if (testCase == 8) {
          require(
              player.getHealth() < beforeHealth && player.isEntityAlive(),
              "Enchanted armour probe must take damage and survive");
          BossLab.LOGGER.info(
              "Sovereign equipment: equipped lance health loss="
                  + (beforeHealth - player.getHealth()));
        }
        if (testCase == 9) {
          require(
              player.isEntityAlive() && player.getHealth() > 0,
              "Phoenix Feather must prevent lethal boss damage");
          require(
              countItem(featherId) == beforeFeathers - 1,
              "Exactly one real Phoenix Feather must be consumed");
        }
        BossLab.LOGGER.info("Sovereign combat PASS: " + CASES[testCase]);
        removeWall(world);
        if (++testCase < CASES.length) {
          startCase(world);
        } else {
          require(pictureCount == 3, "Screenshots of all three attack warnings required");
          boss.setDead();
          boss = new Sovereign(world);
          boss.setPosition(x + 8, y, z);
          require(world.spawnEntityInWorld(boss), "Reset probe boss must spawn");
          boss.setEntityHealth(600);
          player.capabilities.disableDamage = true;
          hits = 0;
          ticks = 0;
          stage = 3;
        }
      }
      require(ticks < 160, "Combat case timed out: " + CASES[Math.min(testCase, CASES.length - 1)]);
    } else if (stage == 3) {
      require(
          hits == 0 && boss.getAction() == Sovereign.Action.HUNT,
          "Invulnerable players must not be attacked");
      if (ticks >= 305) {
        require(boss.getHealth() == 1200, "Boss must recover after disengagement");
        boss.setEntityHealth(801);
        require(boss.getPhase() == 1, "Above 800 is phase one");
        boss.setEntityHealth(800);
        require(boss.getPhase() == 2, "800 starts phase two");
        boss.setEntityHealth(401);
        require(boss.getPhase() == 2, "Above 400 is phase two");
        boss.setEntityHealth(400);
        require(boss.getPhase() == 3, "400 starts phase three");
        boss.setEntityHealth(777);
        boss.getEntityData().setString(TAG, run);
        entityId = boss.entityId;
        stage = 4;
      }
    } else if (stage == 4 && observedHealth == 777) {
      restorePlayer();
      return true;
    } else if (stage == 20) {
      boss = findBoss(world, run);
      if (boss == null) {
        require(ticks < 400, "Persisted boss must load");
        return false;
      }
      require(boss.getHealth() == 777, "Boss identity and health must survive restart");
      require(boss.getAction() == Sovereign.Action.HUNT, "Reload must cancel transient attacks");
      entityId = boss.entityId;
      stage = 21;
    } else if (stage == 21 && observedHealth == 777) {
      boss.hurtResistantTime = 0;
      require(
          boss.attackEntityFrom(DamageSource.causePlayerDamage(player), 10000),
          "Lethal player damage must be accepted");
      require(boss.getHealth() <= 0, "Lethal damage must defeat boss");
      ticks = 0;
      stage = 22;
    } else if (stage == 22 && ticks >= 25) {
      require(boss.isDead, "Boss death animation must finish");
      int stars = 0;
      for (Object value : world.loadedEntityList.toArray()) {
        if (value instanceof EntityItem) {
          EntityItem item = (EntityItem) value;
          if (item.getDistanceSq(x + 8, y, z) < 256
              && item.getEntityItem().itemID == Item.netherStar.itemID) {
            stars += item.getEntityItem().stackSize;
            item.setDead();
          }
        }
      }
      require(stars == 1, "Player kill must drop one Nether Star");
      EntityPig sentinel = new EntityPig(world);
      sentinel.setPosition(x, y, z);
      require(world.spawnEntityInWorld(sentinel), "Sentinel must spawn");
      server.getCommandManager().executeCommand(player, "bosslab sovereign");
      require(countBosses(world) == 1, "A new boss can be summoned after death");
      server.getCommandManager().executeCommand(player, "bosslab clear");
      require(
          countBosses(world) == 0 && !sentinel.isDead,
          "Clear must remove bosses and preserve unrelated entities");
      sentinel.setDead();
      world.difficultySetting = 0;
      server.getCommandManager().executeCommand(player, "bosslab sovereign");
      require(countBosses(world) == 0, "Peaceful must reject summoning");
      Sovereign peaceful = new Sovereign(world);
      peaceful.setPosition(x, y, z);
      require(world.spawnEntityInWorld(peaceful), "Peaceful removal probe must spawn");
      peaceful.onLivingUpdate();
      require(peaceful.isDead, "Peaceful must remove an existing boss");
      restorePlayer();
      return true;
    }
    return false;
  }

  private Sovereign.Action expectedAttack() {
    return testCase == 3 || testCase == 4
        ? Sovereign.Action.RUSH
        : testCase >= 5 && testCase <= 7 ? Sovereign.Action.BLAST : Sovereign.Action.LANCE;
  }

  private void startCase(World world) {
    boss.setDead();
    boss = new Sovereign(world);
    NBTTagCompound state = new NBTTagCompound();
    boss.writeEntityToNBT(state);
    state.setInteger(
        "SovereignAttack",
        expectedAttack() == Sovereign.Action.BLAST
            ? 2
            : expectedAttack() == Sovereign.Action.RUSH ? 1 : 0);
    boss.readEntityFromNBT(state);
    boss.setPosition(x + 8, y, z);
    require(world.spawnEntityInWorld(boss), "Combat case boss must spawn");
    entityId = boss.entityId;
    ticks = hits = rawDamage = 0;
    warned = synchronizedWarning = false;
    protect = testCase < 8;
    playerZ = z;
    player.clearActivePotions();
    player.hurtResistantTime = 0;
    player.setEntityHealth(testCase == 9 ? 1 : player.getMaxHealth());
    beforeHealth = player.getHealth();
    beforeFeathers = countItem(featherId);
  }

  private void pinPlayer() {
    player.motionX = player.motionY = player.motionZ = 0;
    player.fallDistance = 0;
    player.setPositionAndUpdate(x, y, playerZ);
  }

  private void restorePlayer() {
    player.readEntityFromNBT(originalPlayer);
    player.sendPlayerAbilities();
    player.setPositionAndUpdate(player.posX, player.posY, player.posZ);
    player.worldObj.difficultySetting = originalDifficulty;
  }

  private void buildWall(World world) {
    int bx = (int) Math.floor(x) + 3;
    for (int dy = -3; dy <= 5; dy++) {
      for (int dz = -4; dz <= 4; dz++) {
        require(
            world.isAirBlock(bx, (int) y + dy, (int) Math.floor(z) + dz),
            "Cover probe requires empty air");
        world.setBlock(bx, (int) y + dy, (int) Math.floor(z) + dz, Block.stone.blockID);
      }
    }
    wall = true;
  }

  private void removeWall(World world) {
    if (!wall) {
      return;
    }
    int bx = (int) Math.floor(x) + 3;
    for (int dy = -3; dy <= 5; dy++) {
      for (int dz = -4; dz <= 4; dz++) {
        world.setBlockToAir(bx, (int) y + dy, (int) Math.floor(z) + dz);
      }
    }
    wall = false;
  }

  private int findItem(String className) {
    for (ItemStack item : player.inventory.mainInventory) {
      if (item != null && item.getItem().getClass().getSimpleName().equals(className)) {
        return item.itemID;
      }
    }
    return -1;
  }

  private int countItem(int id) {
    int count = 0;
    for (ItemStack item : player.inventory.mainInventory) {
      if (item != null && item.itemID == id) {
        count += item.stackSize;
      }
    }
    return count;
  }

  private static Sovereign findBoss(World world, String tag) {
    for (Object value : world.loadedEntityList) {
      if (value instanceof Sovereign
          && !((Sovereign) value).isDead
          && (tag == null || tag.equals(((Entity) value).getEntityData().getString(TAG)))) {
        return (Sovereign) value;
      }
    }
    return null;
  }

  private static int countBosses(World world) {
    int count = 0;
    for (Object value : world.loadedEntityList) {
      if (value instanceof Sovereign && !((Sovereign) value).isDead) {
        count++;
      }
    }
    return count;
  }

  void observe(Minecraft minecraft) {
    if (minecraft.theWorld == null) {
      return;
    }
    Entity entity = minecraft.theWorld.getEntityByID(entityId);
    if (entity instanceof Sovereign) {
      Sovereign visible = (Sovereign) entity;
      observedHealth = visible.getHealth();
      Vec3 aim = visible.getAim();
      observedAimX = aim.xCoord;
      observedAimY = aim.yCoord;
      observedAimZ = aim.zCoord;
      observedAction = visible.getAction().ordinal();
    }
  }

  void render(Minecraft minecraft) throws IOException {
    if (minecraft.theWorld == null || minecraft.thePlayer == null) {
      return;
    }
    Entity entity = minecraft.theWorld.getEntityByID(entityId);
    if (!(entity instanceof Sovereign)) {
      return;
    }
    Sovereign visible = (Sovereign) entity;
    face(minecraft.thePlayer, visible);
    Sovereign.Action action = visible.getAction();
    if ((action == Sovereign.Action.LANCE
            || action == Sovereign.Action.RUSH
            || action == Sovereign.Action.BLAST)
        && visible.getActionTicks() < 16
        && !pictures.contains(action)) {
      File evidence = new File(directory, "bosslab-evidence");
      if (!evidence.isDirectory() && !evidence.mkdirs()) {
        throw new IOException("Cannot create screenshot directory");
      }
      BossLab.LOGGER.info(
          ScreenShotHelper.func_74292_a(
              evidence,
              run + "-" + action + ".png",
              minecraft.displayWidth,
              minecraft.displayHeight));
      pictures.add(action);
      pictureCount = pictures.size();
    }
  }

  private static void face(net.minecraft.entity.EntityLiving viewer, Entity target) {
    double dx = target.posX - viewer.posX;
    double dz = target.posZ - viewer.posZ;
    double dy = target.posY + Sovereign.CENTER_HEIGHT - (viewer.posY + viewer.getEyeHeight());
    viewer.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    viewer.rotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
