package dev.hexxitlab.harness;

import dev.hexxitlab.BossLab;
import dev.hexxitlab.sovereign.Sovereign;
import dev.hexxitlab.warden.Cinder;
import dev.hexxitlab.warden.Warden;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
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
 * Drives the Warden through scripted positions on a temporary platform. Geometry probes cancel boss
 * damage at the Forge boundary after recording it; the whole save is restored afterwards.
 */
public final class WardenScenario {
  private static final String TAG = "WardenSmoke";
  private static final int PLATFORM_RADIUS = 16;
  private static final int PLATFORM_Y = 200;
  private static final int AIR_ABOVE = 14;
  private static final int CASE_LIMIT = 400;
  private static final double FRONT = 2.8;
  private static final int PERSISTED_HEALTH = 1777;
  private static final int PROBE_DAMAGE = 20;
  private static final int FRAMING_FRAMES = 6;
  private static final int ARROW_DAMAGE = 24;
  private static final int HOTBAR_SIZE = 9;
  private static final int APPLE_TICKS = 600;
  private static final int APPLE_LEVEL = 3;
  private static final double SNIPER_DISTANCE = Warden.HOOK_RANGE + 4;
  // A free Warden settles in reach; clients interpolate over three ticks, so it must stand still
  // longer than that before positions compare. Packets carry positions in 32nds of a block.
  private static final double SYNC_DISTANCE = 4.5;
  private static final int SETTLE_TICKS = 10;
  private static final double SYNC_TOLERANCE = 1.0 / 16;
  // Vanilla's stride amount: 0 standing, 1 at a quarter block per tick.
  private static final float WALKING_STRIDE = 0.5F;
  // A player's walking speed in blocks per tick.
  private static final double WALK = 0.216;
  private static final String[] PICTURES = {
    "CLEAVE", "SLAM", "LEAP", "HOOK", "RECLAIM", "EXPOSED", "CINDER"
  };

  /**
   * One attack and the player's scripted answer when its warning locks: a move in the Warden's
   * frame at the commit, reversing the combo's steady movement, or stopping and stepping aside.
   */
  private static final class Step {
    final Warden.Action action;
    final boolean move;
    final double forward;
    final double side;
    final double up;
    final int windup;
    final boolean reverse;
    final boolean sidestep;

    Step(Warden.Action action) {
      this(action, false, 0, 0, 0, 0, false, false);
    }

    Step(Warden.Action action, double forward, double side, double up) {
      this(action, true, forward, side, up, 0, false, false);
    }

    Step(
        Warden.Action action,
        boolean move,
        double forward,
        double side,
        double up,
        int windup,
        boolean reverse,
        boolean sidestep) {
      this.action = action;
      this.move = move;
      this.forward = forward;
      this.side = side;
      this.up = up;
      this.windup = windup;
      this.reverse = reverse;
      this.sidestep = sidestep;
    }

    static Step reversing(Warden.Action action) {
      return new Step(action, false, 0, 0, 0, 0, true, false);
    }

    /** Stops moving and steps sideways across the locked strike, from wherever the player is. */
    static Step sidestepping(Warden.Action action, double side) {
      return new Step(action, false, 0, side, 0, 0, false, true);
    }
  }

  /**
   * A fixture: the player starts ahead of a Warden facing +X. From the first warning on, the player
   * keeps moving steadily: orbiting at a tangential speed, and drifting along the world axes.
   */
  private static final class Combo {
    final String name;
    final int phase;
    final double forward;
    final double up;
    final Step[] steps;
    final Integer[] damage;
    double orbit;
    double driftX;
    double driftZ;
    boolean mash;

    Combo(String name, int phase, double forward, double up, Step[] steps, Integer... damage) {
      this.name = name;
      this.phase = phase;
      this.forward = forward;
      this.up = up;
      this.steps = steps;
      this.damage = damage;
    }

    /** The player hammers the raised shield before the Warden's first attack. */
    Combo mashing() {
      this.mash = true;
      return this;
    }

    Combo moving(double orbit, double driftX, double driftZ) {
      this.orbit = orbit;
      this.driftX = driftX;
      this.driftZ = driftZ;
      return this;
    }
  }

  private static final Combo[] COMBOS = {
    new Combo(
        "front-combo",
        1,
        FRONT,
        0,
        new Step[] {new Step(Warden.Action.CLEAVE), new Step(Warden.Action.SLAM)},
        Warden.CLEAVE_DAMAGE,
        Warden.SLAM_DAMAGE),
    // Lingering behind gets slammed as it turns; rolling behind and then leaving escapes both.
    new Combo(
            "shield-bash",
            1,
            FRONT,
            0,
            new Step[] {new Step(Warden.Action.BASH)},
            Warden.BASH_DAMAGE)
        .mashing(),
    new Combo(
        "roll-behind",
        1,
        FRONT,
        0,
        new Step[] {
          new Step(Warden.Action.CLEAVE, -2.5, 0, 0), new Step(Warden.Action.SLAM, -7, 0, 0)
        }),
    // Steady circling walks into warnings aimed where the player is heading.
    new Combo(
            "orbit-combo",
            1,
            FRONT,
            0,
            new Step[] {new Step(Warden.Action.CLEAVE), new Step(Warden.Action.SLAM)},
            Warden.CLEAVE_DAMAGE,
            Warden.SLAM_DAMAGE)
        .moving(WALK, 0, 0),
    new Combo(
            "orbit-reverse",
            1,
            FRONT,
            0,
            new Step[] {Step.reversing(Warden.Action.CLEAVE), Step.reversing(Warden.Action.SLAM)})
        .moving(WALK, 0, 0),
    new Combo(
        "backhand-chain",
        2,
        FRONT,
        0,
        new Step[] {
          new Step(Warden.Action.CLEAVE, -2.5, 0, 0),
          new Step(Warden.Action.BACKHAND),
          new Step(Warden.Action.SLAM, FRONT, 0, 0)
        },
        Warden.BACKHAND_DAMAGE,
        Warden.SLAM_DAMAGE),
    new Combo("leap-hit", 1, 18, 0, new Step[] {new Step(Warden.Action.LEAP)}, Warden.LEAP_DAMAGE),
    // A missed Leap throws the Chain Hook after the player instead of leaving an opening.
    new Combo(
        "leap-dodge",
        1,
        18,
        0,
        new Step[] {new Step(Warden.Action.LEAP, 18, 7, 0), new Step(Warden.Action.HOOK, 7, 4, 0)}),
    new Combo("leap-kite", 1, 15, 0, new Step[] {new Step(Warden.Action.LEAP)}, Warden.LEAP_DAMAGE)
        .moving(0, 0, WALK),
    new Combo(
            "leap-kite-reverse",
            1,
            15,
            0,
            // Reversing put the player in line behind the landing, so the chain needs a sidestep.
            new Step[] {
              Step.reversing(Warden.Action.LEAP), Step.sidestepping(Warden.Action.HOOK, 4)
            })
        .moving(0, 0, WALK),
    // Teleporting back to the takeoff point while it flies.
    new Combo(
        "leap-teleport",
        1,
        15,
        0,
        new Step[] {
          new Step(Warden.Action.LEAP, -3, 0, 0),
          new Step(Warden.Action.HOOK),
          new Step(Warden.Action.SLAM)
        },
        Warden.HOOK_DAMAGE,
        Warden.SLAM_DAMAGE),
    // Backing away from the Chain Hook stays in its line.
    new Combo(
            "kite-hook",
            1,
            9,
            0,
            new Step[] {new Step(Warden.Action.HOOK), new Step(Warden.Action.SLAM)},
            Warden.HOOK_DAMAGE,
            Warden.SLAM_DAMAGE)
        .moving(0, WALK, 0),
    new Combo(
        "hook-reel-slam",
        1,
        5,
        8,
        new Step[] {new Step(Warden.Action.HOOK), new Step(Warden.Action.SLAM)},
        Warden.HOOK_DAMAGE,
        Warden.SLAM_DAMAGE),
    new Combo("hook-dodge", 1, 5, 8, new Step[] {new Step(Warden.Action.HOOK, 5, 4, 8)}),
    // Standing close below its reach, under its ledge, gets a Leap down instead of blows that miss.
    new Combo(
        "below-reach", 1, FRONT, -3, new Step[] {new Step(Warden.Action.LEAP)}, Warden.LEAP_DAMAGE),
    new Combo(
        "berserk-combo",
        3,
        FRONT,
        0,
        new Step[] {
          new Step(
              Warden.Action.CLEAVE,
              false,
              0,
              0,
              0,
              Warden.windup(Warden.Action.CLEAVE, 3),
              false,
              false),
          new Step(Warden.Action.CLEAVE),
          new Step(Warden.Action.CLEAVE),
          new Step(Warden.Action.SLAM)
        },
        Warden.CLEAVE_DAMAGE,
        Warden.CLEAVE_DAMAGE,
        Warden.CLEAVE_DAMAGE,
        Warden.SLAM_DAMAGE)
  };

  private final String run;
  private final boolean reload;
  private final File directory;
  private final List<Integer> wardenHits = new ArrayList<Integer>();
  private final List<Integer> cinderHits = new ArrayList<Integer>();
  private final Set<String> pictures = new HashSet<String>();
  private volatile int pictureCount;
  private String framing;
  private int framedFrames;
  private volatile int entityId = -1;
  private volatile int observedHealth = -1;
  private volatile int observedActions;
  private EntityPlayerMP player;
  private NBTTagCompound originalPlayer;
  private Warden boss;
  private double cx;
  private double cz;
  private double playerX;
  private double playerY;
  private double playerZ;
  private boolean protect = true;
  private int stage;
  private int ticks;
  private int comboIndex;
  private int step;
  private Warden.Action lastAction;
  private Step pending;
  private double orbit;
  private double driftX;
  private double driftZ;
  private Cinder diver;
  private int dives;
  private int swarmAtReclaim;
  private boolean cinderShot;
  private int smotherCheck = -1;
  private volatile boolean clientHitRequested;
  private volatile boolean clientHitDone;
  private volatile double clientX = Double.NaN;
  private volatile double clientY = Double.NaN;
  private volatile double clientZ = Double.NaN;
  private volatile float clientStride;
  private double lastX;
  private double lastY;
  private double lastZ;
  private double lastClientX;
  private double lastClientY;
  private double lastClientZ;
  private int stillTicks;
  private int hitTick;

  WardenScenario(String run, String phase, File directory) {
    this.run = run;
    this.reload = "reload".equals(phase);
    this.directory = directory;
    MinecraftForge.EVENT_BUS.register(this);
  }

  @ForgeSubscribe(priority = EventPriority.HIGHEST)
  public void observeAttack(LivingAttackEvent event) {
    if (event.entityLiving != player) {
      return;
    }
    Entity source = event.source.getEntity();
    if (source instanceof Warden) {
      wardenHits.add(Integer.valueOf(event.ammount));
    } else if (source instanceof Cinder) {
      cinderHits.add(Integer.valueOf(event.ammount));
    } else {
      return;
    }
    if (protect) {
      event.setCanceled(true);
    }
  }

  boolean tick(MinecraftServer server, EntityPlayerMP current) {
    player = current;
    ticks++;
    World world = player.worldObj;
    if (stage == 0) {
      originalPlayer = new NBTTagCompound();
      player.writeEntityToNBT(originalPlayer);
      // Daylight keeps hostile spawns off the temporary platform; clear skies keep evidence
      // legible.
      world.setWorldTime(6000);
      world.getWorldInfo().setRaining(false);
      world.getWorldInfo().setThundering(false);
      player.capabilities.allowFlying = true;
      player.capabilities.isFlying = true;
      player.capabilities.disableDamage = false;
      player.sendPlayerAbilities();
      player.extinguish();
      cx = Math.floor(player.posX) + 0.5;
      cz = Math.floor(player.posZ) + 0.5;
      playerX = player.posX;
      playerY = player.posY;
      playerZ = player.posZ;
      if (reload) {
        stage = 20;
      } else {
        buildPlatform(world);
        stage = 30;
      }
      ticks = 0;
      return false;
    }
    // The reel moves the player; the combo adopts where it ends before pinning resumes.
    if (boss != null
        && boss.getAction() != Warden.Action.REEL
        && lastAction != Warden.Action.REEL) {
      pinPlayer();
    }
    player.extinguish();
    if (stage == 30) {
      boss = spawn(world, 1, Warden.MAX_HEALTH);
      placePlayer(SYNC_DISTANCE, 0, 0);
      ticks = 0;
      stage = 31;
    } else if (stage == 31) {
      tickSync();
    } else if (stage == 1) {
      guard(world);
      comboIndex = 0;
      startCombo(world);
      stage = 2;
    } else if (stage == 2) {
      tickCombo(world);
    } else if (stage == 3) {
      kindle(world);
    } else if (stage == 4) {
      tickDives();
    } else if (stage == 5) {
      tickReclaim(world);
    } else if (stage == 6) {
      tickNovaEscape(world);
    } else if (stage == 7) {
      tickDisengage(world);
    } else if (stage == 8 && observedHealth == PERSISTED_HEALTH) {
      require(pictureCount == PICTURES.length, "Screenshots required: " + pictures);
      restorePlayer();
      return true;
    } else if (stage == 20) {
      boss = findTagged(world);
      if (boss == null) {
        require(ticks < 400, "Persisted Warden must load");
        return false;
      }
      require(boss.getHealth() == PERSISTED_HEALTH, "Warden health must survive restart");
      require(boss.getPhase() == 2, "Warden phase must survive restart");
      require(boss.getAction() == Warden.Action.HUNT, "Reload must cancel transient attacks");
      require(count(world, Cinder.class) == 0, "Cinders must not be saved");
      entityId = boss.entityId;
      stage = 21;
    } else if (stage == 21 && observedHealth == PERSISTED_HEALTH) {
      boss.hurtResistantTime = 0;
      require(
          boss.attackEntityFrom(DamageSource.causePlayerDamage(player), 100000),
          "Lethal player damage must be accepted");
      require(boss.getHealth() <= 0, "Lethal damage must defeat the Warden");
      ticks = 0;
      stage = 22;
    } else if (stage == 22 && ticks >= 25) {
      verifyLootAndCommands(server, world);
      restorePlayer();
      return true;
    }
    return false;
  }

  /**
   * A Warden that walks on its first tick leaves clients offset by that step. Clients must see it
   * where the server has it, at spawn and after a hit through the real client attack path.
   */
  private void tickSync() {
    // A lagging client is still gliding toward the last position; a desynchronized one has
    // stopped at the wrong place. Compare only once both copies have settled.
    double x = clientX;
    double y = clientY;
    double z = clientZ;
    boolean still =
        boss.posX == lastX
            && boss.posY == lastY
            && boss.posZ == lastZ
            && x == lastClientX
            && y == lastClientY
            && z == lastClientZ;
    stillTicks = still ? stillTicks + 1 : 0;
    lastX = boss.posX;
    lastY = boss.posY;
    lastZ = boss.posZ;
    lastClientX = x;
    lastClientY = y;
    lastClientZ = z;
    if (stillTicks >= SETTLE_TICKS) {
      if (!clientHitRequested) {
        requireSynced("at spawn");
        // Clients skip vanilla travel, which also drives the walk cycle; without it, it slides.
        require(
            clientStride > WALKING_STRIDE,
            "Clients must swing the Warden's legs as it walks: stride " + clientStride);
        BossLab.LOGGER.info("Warden combat PASS: walk-cycle");
        clientHitRequested = true;
      } else if (clientHitDone && hitTick == 0) {
        hitTick = ticks;
      } else if (hitTick > 0 && ticks - hitTick >= SETTLE_TICKS) {
        requireSynced("after a hit");
        BossLab.LOGGER.info("Warden combat PASS: client-sync");
        boss.setDead();
        stage = 1;
      }
    }
    require(ticks < CASE_LIMIT, "Client sync timed out at " + boss.getAction());
  }

  private void requireSynced(String when) {
    double error =
        Math.max(
            Math.abs(clientX - boss.posX),
            Math.max(Math.abs(clientY - boss.posY), Math.abs(clientZ - boss.posZ)));
    require(
        error <= SYNC_TOLERANCE,
        "Clients must see the Warden where the server has it "
            + when
            + ": off by "
            + error
            + " blocks; client "
            + clientX
            + ","
            + clientY
            + ","
            + clientZ
            + ", server "
            + boss.posX
            + ","
            + boss.posY
            + ","
            + boss.posZ);
  }

  /** Guard maths with deterministic sources, then the same rule through the real weapon. */
  private void guard(World world) {
    boss = spawn(world, 1, Warden.MAX_HEALTH);
    placePlayer(FRONT, 0, 0);
    require(
        damageTaken(DamageSource.causePlayerDamage(player), PROBE_DAMAGE)
            == Warden.guarded(PROBE_DAMAGE),
        "Front hits must be guarded");
    placePlayer(-FRONT, 0, 0);
    require(
        damageTaken(DamageSource.causePlayerDamage(player), PROBE_DAMAGE) == PROBE_DAMAGE,
        "Back hits must land in full");
    // A projectile is judged where it strikes: the shooter stands in front for both arrows.
    placePlayer(FRONT, 0, 0);
    require(
        damageTaken(arrowAt(world, FRONT), ARROW_DAMAGE) == 0,
        "The shield must stop arrows striking the front");
    require(
        damageTaken(arrowAt(world, -FRONT), ARROW_DAMAGE) == ARROW_DAMAGE,
        "Arrows striking the back must land in full");
    placePlayer(SNIPER_DISTANCE, 0, 0);
    require(
        damageTaken(arrowAt(world, -FRONT), ARROW_DAMAGE) == 0,
        "Shots from beyond the hook's reach must glance off");
    // Pack weapons vary per swing and can bypass the attack event, so the exact rule is proven
    // above and the real weapon only has to be clearly blunted from the front.
    player.inventory.currentItem = bestWeapon();
    placePlayer(-FRONT, 0, 0);
    int full = swing();
    placePlayer(FRONT, 0, 0);
    int guarded = swing();
    require(guarded * 2 < full, "Real weapon: front " + guarded + " vs back " + full);
    BossLab.LOGGER.info("Warden equipment: weapon back=" + full + " front=" + guarded);
    require(boss.getPosture() == 0, "Hits outside its openings must not build posture");
    BossLab.LOGGER.info("Warden combat PASS: guard");
  }

  private void startCombo(World world) {
    Combo combo = COMBOS[comboIndex];
    if (boss != null) {
      boss.setDead();
    }
    boss = spawn(world, combo.phase, Warden.MAX_HEALTH);
    placePlayer(combo.forward, 0, combo.up);
    if (comboIndex == 0) {
      // An enchanted golden apple's Regeneration IV, for the first blow to smother.
      player.addPotionEffect(new PotionEffect(Potion.regeneration.id, APPLE_TICKS, APPLE_LEVEL));
    }
    wardenHits.clear();
    step = 0;
    pending = null;
    orbit = combo.orbit;
    driftX = combo.driftX;
    driftZ = combo.driftZ;
    lastAction = Warden.Action.HUNT;
    ticks = 0;
  }

  private void tickCombo(World world) {
    Combo combo = COMBOS[comboIndex];
    Warden.Action action = boss.getAction();
    if (step == 0 && action == Warden.Action.HUNT) {
      // Stable fixtures: unconstrained pursuit is exercised by the swarm stages.
      boss.setPosition(cx, PLATFORM_Y, cz);
      if (combo.mash && ticks == 2) {
        for (int hit = 0; hit < Warden.BASH_PROVOCATION; hit++) {
          damageTaken(DamageSource.causePlayerDamage(player), PROBE_DAMAGE);
        }
        action = boss.getAction();
      }
    }
    if (action != lastAction) {
      if (lastAction == Warden.Action.REEL) {
        Vec3 expected = frame(Warden.REEL_DISTANCE, 0, 0);
        require(
            Math.abs(player.posX - expected.xCoord) < 0.6
                && Math.abs(player.posZ - expected.zCoord) < 0.6
                && Math.abs(player.posY - boss.posY) < 0.6,
            "Hook must reel the player in front of the Warden: player at "
                + player.posX
                + ","
                + player.posY
                + ","
                + player.posZ
                + " expected "
                + expected);
        playerX = player.posX;
        playerY = player.posY;
        playerZ = player.posZ;
        orbit = driftX = driftZ = 0;
      }
      if (Warden.isStrike(action)) {
        require(step < combo.steps.length, combo.name + ": unexpected extra " + action);
        Step expected = combo.steps[step];
        require(
            action == expected.action,
            combo.name + ": step " + step + " expected " + expected.action + ", saw " + action);
        require(
            expected.windup == 0 || boss.getActionTicks() == expected.windup,
            combo.name + ": windup " + boss.getActionTicks());
        pending = expected;
        step++;
      }
      lastAction = action;
    }
    // Tracking would follow an earlier answer; the player reacts once the warning locks.
    if (pending != null && boss.isCommitted()) {
      if (pending.move) {
        placePlayer(pending.forward, pending.side, pending.up);
      }
      if (pending.reverse) {
        orbit = -orbit;
        driftX = -driftX;
        driftZ = -driftZ;
      }
      if (pending.sidestep) {
        double yaw = Math.toRadians(boss.getStrikeYaw());
        playerX -= Math.cos(yaw) * pending.side;
        playerZ += -Math.sin(yaw) * pending.side;
        orbit = driftX = driftZ = 0;
        pinPlayer();
      }
      pending = null;
    }
    if (step > 0 && action != Warden.Action.REEL) {
      moveSteadily();
    }
    require(wardenHits.isEmpty() || step > 0, combo.name + ": damage before any warning");
    if (step == combo.steps.length && action == Warden.Action.RECOVER) {
      require(
          wardenHits.equals(Arrays.asList(combo.damage)),
          combo.name + ": expected hits " + Arrays.asList(combo.damage) + ", saw " + wardenHits);
      require(
          (observedActions & (1 << combo.steps[0].action.ordinal())) != 0,
          combo.name + ": client must receive the warning");
      if (comboIndex == 0) {
        require(
            damageTaken(DamageSource.causePlayerDamage(player), PROBE_DAMAGE) == PROBE_DAMAGE,
            "Finisher recovery must drop the guard");
        require(
            !player.isPotionActive(Potion.regeneration), "Warden blows must smother regeneration");
        posture(world);
      }
      if (combo.steps[0].action == Warden.Action.LEAP
          && !combo.steps[0].move
          && !combo.steps[0].reverse) {
        require(
            boss.getDistance(player.posX, boss.posY, player.posZ) < 1.5,
            "Leap must land on its locked target");
      }
      BossLab.LOGGER.info("Warden combat PASS: " + combo.name);
      if (++comboIndex < COMBOS.length) {
        startCombo(world);
      } else {
        boss.setDead();
        boss = spawn(world, 1, Warden.KINDLE_HEALTH + 1);
        placePlayer(FRONT, 0, 0);
        wardenHits.clear();
        ticks = 0;
        stage = 3;
      }
    }
    require(ticks < CASE_LIMIT, combo.name + ": timed out at " + action);
  }

  /** Clean hits in a recovery fill posture; the break staggers it, open to heavier blows. */
  private void posture(World world) {
    require(boss.getPosture() == PROBE_DAMAGE, "Recovery hits must build posture");
    boss.hurtResistantTime = 0;
    boss.attackEntityFrom(DamageSource.causePlayerDamage(player), Warden.POSTURE_LIMIT);
    require(
        boss.getAction() == Warden.Action.STAGGER && boss.getPosture() == 0,
        "A full posture bar must stagger the Warden");
    int heavier = Math.round(PROBE_DAMAGE * Warden.STAGGER_MULTIPLIER);
    require(
        damageTaken(DamageSource.causePlayerDamage(player), PROBE_DAMAGE) == heavier,
        "A staggered Warden must take heavier blows");
    require(
        damageTaken(arrowAt(world, FRONT), PROBE_DAMAGE) == heavier,
        "A staggered Warden must not shield arrows");
    BossLab.LOGGER.info("Warden combat PASS: posture-break");
  }

  private void moveSteadily() {
    if (orbit != 0) {
      double dx = playerX - boss.posX;
      double dz = playerZ - boss.posZ;
      double radius = Math.sqrt(dx * dx + dz * dz);
      double angle = Math.atan2(dz, dx) + orbit / radius;
      playerX = boss.posX + Math.cos(angle) * radius;
      playerZ = boss.posZ + Math.sin(angle) * radius;
    }
    playerX += driftX;
    playerZ += driftZ;
    pinPlayer();
  }

  private void kindle(World world) {
    Warden.Action action = boss.getAction();
    if (ticks == 1) {
      boss.attackEntityFrom(DamageSource.generic, 10);
    } else if (action == Warden.Action.KINDLE) {
      int health = boss.getHealth();
      boss.hurtResistantTime = 0;
      require(
          !boss.attackEntityFrom(DamageSource.causePlayerDamage(player), PROBE_DAMAGE)
              && boss.getHealth() == health,
          "Harrowing must ward off damage");
    } else if (ticks > 2 && boss.getPhase() == 2) {
      require(
          count(world, Cinder.class) == Warden.KINDLE_CINDERS,
          "Kindling must raise " + Warden.KINDLE_CINDERS + " cinders");
      BossLab.LOGGER.info("Warden combat PASS: kindle");
      cinderHits.clear();
      ticks = 0;
      stage = 4;
    }
    require(ticks < CASE_LIMIT, "Kindle timed out at " + action);
  }

  /** The first conducted dive is taken; the second is dodged sideways after its warning. */
  private void tickDives() {
    if (ticks == smotherCheck) {
      require(
          !player.isPotionActive(Potion.regeneration),
          "A Cinder's ash must keep Regeneration from taking hold");
      BossLab.LOGGER.info("Warden combat PASS: cinder-smother");
    }
    if (diver == null) {
      for (Object value : player.worldObj.loadedEntityList) {
        if (value instanceof Cinder && ((Cinder) value).getState() == Cinder.State.MARK) {
          diver = (Cinder) value;
          cinderHits.clear();
          if (dives == 1) {
            playerZ += 4;
          }
        }
      }
    } else if (diver.getState() == Cinder.State.ORBIT || diver.isDead) {
      int expected = dives == 0 ? 1 : 0;
      require(
          cinderHits.size() == expected
              && (expected == 0 || cinderHits.get(0).intValue() == Cinder.DAMAGE),
          "Cinder dive " + dives + ": expected " + expected + " hit, saw " + cinderHits);
      BossLab.LOGGER.info("Warden combat PASS: cinder-dive-" + (dives == 0 ? "hit" : "dodge"));
      if (dives == 0) {
        // A golden apple eaten after the hit; the Warden strips it on its next tick.
        player.addPotionEffect(new PotionEffect(Potion.regeneration.id, APPLE_TICKS, APPLE_LEVEL));
        smotherCheck = ticks + 2;
      }
      diver = null;
      if (++dives == 2) {
        swarmAtReclaim = count(player.worldObj, Cinder.class);
        boss.setEntityHealth(Warden.RECLAIM_HEALTH + 1);
        boss.hurtResistantTime = 0;
        boss.attackEntityFrom(DamageSource.generic, 10);
        wardenHits.clear();
        ticks = 0;
        stage = 5;
      }
    }
    require(ticks < CASE_LIMIT, "Cinder dives timed out");
  }

  private void tickReclaim(World world) {
    Warden.Action action = boss.getAction();
    if (action == Warden.Action.RECLAIM && !cinderShot) {
      // Shooting one cinder down in flight must keep it out of the nova.
      for (Object value : world.loadedEntityList.toArray()) {
        if (!cinderShot && value instanceof Cinder && ((Cinder) value).isEntityAlive()) {
          ((Cinder) value).attackEntityFrom(DamageSource.causePlayerDamage(player), 100);
          cinderShot = true;
        }
      }
      require(cinderShot, "A recalled cinder must be in flight");
    }
    if (action == Warden.Action.NOVA && lastAction != Warden.Action.NOVA) {
      double expected = Warden.NOVA_BASE_RADIUS + Warden.NOVA_GROWTH * (swarmAtReclaim - 1);
      require(
          Math.abs(boss.getNovaRadius() - expected) < 0.01,
          "Nova radius "
              + boss.getNovaRadius()
              + " must grow only for absorbed cinders: "
              + expected);
    }
    if (lastAction == Warden.Action.NOVA && action != Warden.Action.NOVA) {
      require(
          wardenHits.equals(Arrays.asList(Integer.valueOf(Warden.NOVA_DAMAGE))),
          "Ash nova must hit inside its radius: " + wardenHits);
      require(count(world, Cinder.class) == 0, "The nova must consume the swarm");
    }
    lastAction = action;
    // Probe the broken guard from the front: hunting turns the Warden toward the player.
    if (boss.getPhase() == 3
        && action == Warden.Action.HUNT
        && Math.abs(angle(boss, player)) <= 60) {
      require(
          damageTaken(DamageSource.causePlayerDamage(player), PROBE_DAMAGE) == PROBE_DAMAGE,
          "Phase three must have no guard");
      BossLab.LOGGER.info("Warden combat PASS: reclaim-nova-shattered-guard");
      boss.setDead();
      boss = spawn(world, 2, Warden.RECLAIM_HEALTH + 1);
      placePlayer(Warden.NOVA_BASE_RADIUS + 3, 0, 0);
      boss.attackEntityFrom(DamageSource.generic, 10);
      wardenHits.clear();
      ticks = 0;
      stage = 6;
    }
    require(ticks < CASE_LIMIT, "Reclaim timed out at " + action);
  }

  private void tickNovaEscape(World world) {
    if (boss.getPhase() == 3) {
      require(wardenHits.isEmpty(), "Leaving the nova radius must avoid it: " + wardenHits);
      BossLab.LOGGER.info("Warden combat PASS: nova-escape");
      boss.setDead();
      boss = spawn(world, 3, 500);
      player.capabilities.disableDamage = true;
      ticks = 0;
      stage = 7;
    }
    require(ticks < CASE_LIMIT, "Nova escape timed out");
  }

  private void tickDisengage(World world) {
    require(wardenHits.isEmpty(), "Invulnerable players must not be attacked");
    if (ticks >= 305) {
      require(
          boss.getHealth() == Warden.MAX_HEALTH && boss.getPhase() == 1,
          "Disengagement must restore health and the first phase");
      BossLab.LOGGER.info("Warden combat PASS: disengage");
      player.capabilities.disableDamage = false;
      boss.setDead();
      boss = spawn(world, 2, PERSISTED_HEALTH);
      boss.getEntityData().setString(TAG, run);
      placePlayer(FRONT, 0, 0);
      stage = 8;
    }
  }

  private void verifyLootAndCommands(MinecraftServer server, World world) {
    require(boss.isDead, "Warden death animation must finish");
    int stars = 0;
    for (Object value : world.loadedEntityList.toArray()) {
      if (value instanceof EntityItem) {
        EntityItem item = (EntityItem) value;
        if (item.getDistanceSqToEntity(boss) < 256
            && item.getEntityItem().itemID == Item.netherStar.itemID) {
          stars += item.getEntityItem().stackSize;
          item.setDead();
        }
      }
    }
    require(stars == 1, "Player kill must drop one Nether Star");
    server.getCommandManager().executeCommand(player, "bosslab checkpoint");
    server.getCommandManager().executeCommand(player, "bosslab warden");
    require(count(world, Warden.class) == 1, "A Warden can be summoned after death");
    server.getCommandManager().executeCommand(player, "bosslab sovereign");
    require(count(world, Sovereign.class) == 0, "Only one lab boss may be loaded");
    server.getCommandManager().executeCommand(player, "bosslab retry");
    require(
        count(world, Warden.class) == 1 && count(world, Sovereign.class) == 0,
        "Retry must rematch the checkpoint's boss kind");
    EntityPig sentinel = new EntityPig(world);
    sentinel.setPosition(player.posX, player.posY, player.posZ);
    require(world.spawnEntityInWorld(sentinel), "Sentinel must spawn");
    server.getCommandManager().executeCommand(player, "bosslab clear");
    require(
        count(world, Warden.class) == 0 && !sentinel.isDead,
        "Clear must remove the Warden and preserve unrelated entities");
    sentinel.setDead();
    world.difficultySetting = 0;
    server.getCommandManager().executeCommand(player, "bosslab warden");
    require(count(world, Warden.class) == 0, "Peaceful must reject summoning");
    Warden peaceful = new Warden(world);
    peaceful.setPosition(player.posX, player.posY, player.posZ);
    require(world.spawnEntityInWorld(peaceful), "Peaceful removal probe must spawn");
    peaceful.onLivingUpdate();
    require(peaceful.isDead, "Peaceful must remove an existing Warden");
    BossLab.LOGGER.info("Warden combat PASS: persistence-loot-commands");
  }

  private Warden spawn(World world, int phase, int health) {
    Warden warden = new Warden(world);
    NBTTagCompound state = new NBTTagCompound();
    warden.writeEntityToNBT(state);
    state.setInteger("WardenPhase", phase);
    warden.readEntityFromNBT(state);
    warden.setEntityHealth(health);
    // Facing +X: Minecraft yaw -90.
    warden.setLocationAndAngles(cx, PLATFORM_Y, cz, -90, 0);
    require(world.spawnEntityInWorld(warden), "Warden probe must spawn");
    entityId = warden.entityId;
    observedActions = 0;
    return warden;
  }

  private int damageTaken(DamageSource source, int amount) {
    int before = boss.getHealth();
    boss.hurtResistantTime = 0;
    boss.attackEntityFrom(source, amount);
    int taken = before - boss.getHealth();
    boss.setEntityHealth(before);
    return taken;
  }

  private DamageSource arrowAt(World world, double forward) {
    EntityArrow arrow = new EntityArrow(world);
    arrow.setPosition(cx + forward, PLATFORM_Y + 1.5, cz);
    return DamageSource.causeArrowDamage(arrow, player);
  }

  private int swing() {
    int before = boss.getHealth();
    boss.hurtResistantTime = 0;
    player.attackTargetEntityWithCurrentItem(boss);
    int taken = before - boss.getHealth();
    boss.setEntityHealth(before);
    return taken;
  }

  /**
   * The copied character's loadout changes between sessions: wield its strongest item, moved into
   * the hotbar if needed. The player's inventory is restored afterwards.
   */
  private int bestWeapon() {
    ItemStack[] items = player.inventory.mainInventory;
    int best = 0;
    for (int slot = 1; slot < items.length; slot++) {
      if (strength(slot) > strength(best)) {
        best = slot;
      }
    }
    require(strength(best) > 1, "A weapon is required in the inventory");
    if (best >= HOTBAR_SIZE) {
      ItemStack held = items[0];
      items[0] = items[best];
      items[best] = held;
      best = 0;
    }
    return best;
  }

  private int strength(int slot) {
    ItemStack item = player.inventory.getStackInSlot(slot);
    return item == null ? 1 : item.getDamageVsEntity(boss);
  }

  /** Places the player in the Warden's current frame: forward along its locked yaw. */
  private void placePlayer(double forward, double side, double up) {
    Vec3 position = frame(forward, side, up);
    playerX = position.xCoord;
    playerY = position.yCoord;
    playerZ = position.zCoord;
    pinPlayer();
  }

  private Vec3 frame(double forward, double side, double up) {
    double yaw =
        Math.toRadians(
            boss.getAction() == Warden.Action.HUNT ? boss.rotationYaw : boss.getStrikeYaw());
    double fx = -Math.sin(yaw);
    double fz = Math.cos(yaw);
    return Vec3.createVectorHelper(
        boss.posX + fx * forward - fz * side, boss.posY + up, boss.posZ + fz * forward + fx * side);
  }

  private void pinPlayer() {
    player.motionX = player.motionY = player.motionZ = 0;
    player.fallDistance = 0;
    player.setPositionAndUpdate(playerX, playerY, playerZ);
  }

  private static double angle(EntityLiving viewer, Entity target) {
    double yaw =
        Math.toDegrees(Math.atan2(target.posZ - viewer.posZ, target.posX - viewer.posX)) - 90;
    return net.minecraft.util.MathHelper.wrapAngleTo180_double(yaw - viewer.rotationYaw);
  }

  private void buildPlatform(World world) {
    int x0 = (int) Math.floor(cx);
    int z0 = (int) Math.floor(cz);
    for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
      for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
        for (int dy = -1; dy < AIR_ABOVE; dy++) {
          require(
              world.isAirBlock(x0 + dx, PLATFORM_Y + dy, z0 + dz), "Platform probe requires air");
        }
        world.setBlock(x0 + dx, PLATFORM_Y - 1, z0 + dz, Block.stone.blockID);
      }
    }
  }

  private void restorePlayer() {
    player.readEntityFromNBT(originalPlayer);
    player.sendPlayerAbilities();
    player.setPositionAndUpdate(player.posX, player.posY, player.posZ);
  }

  private Warden findTagged(World world) {
    for (Object value : world.loadedEntityList) {
      if (value instanceof Warden
          && !((Warden) value).isDead
          && run.equals(((Entity) value).getEntityData().getString(TAG))) {
        return (Warden) value;
      }
    }
    return null;
  }

  private static int count(World world, Class<?> type) {
    int count = 0;
    for (Object value : world.loadedEntityList) {
      if (type.isInstance(value) && !((Entity) value).isDead) {
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
    if (entity instanceof Warden) {
      Warden visible = (Warden) entity;
      observedHealth = visible.getHealth();
      observedActions |= 1 << visible.getAction().ordinal();
      clientX = visible.posX;
      clientY = visible.posY;
      clientZ = visible.posZ;
      clientStride = Math.max(clientStride, visible.limbYaw);
      if (clientHitRequested && !clientHitDone && minecraft.thePlayer != null) {
        // The real input path: the client swings, then the server judges the packet.
        minecraft.thePlayer.swingItem();
        minecraft.playerController.attackEntity(minecraft.thePlayer, visible);
        clientHitDone = true;
      }
    }
  }

  void render(Minecraft minecraft) throws IOException {
    if (minecraft.theWorld == null || minecraft.thePlayer == null) {
      return;
    }
    for (Object value : minecraft.theWorld.loadedEntityList) {
      if (value instanceof Cinder && ((Cinder) value).getState() == Cinder.State.MARK) {
        Cinder cinder = (Cinder) value;
        face(minecraft.thePlayer, cinder.posX, cinder.posY, cinder.posZ);
        capture(minecraft, "CINDER");
        return;
      }
    }
    Entity entity = minecraft.theWorld.getEntityByID(entityId);
    if (!(entity instanceof Warden)) {
      return;
    }
    Warden visible = (Warden) entity;
    Warden.Action action = visible.getAction();
    if (action == Warden.Action.SLAM || action == Warden.Action.LEAP) {
      // Frame the Warden and its ground warning together; a point straight below is degenerate.
      Vec3 strike = visible.getStrike();
      face(
          minecraft.thePlayer,
          (strike.xCoord + visible.posX) / 2,
          strike.yCoord,
          (strike.zCoord + visible.posZ) / 2);
    } else {
      face(minecraft.thePlayer, visible.posX, visible.posY + 1.2, visible.posZ);
    }
    if (visible.getActionTicks() < 8 || action == Warden.Action.RECLAIM) {
      capture(minecraft, action == Warden.Action.RECOVER ? "EXPOSED" : action.toString());
    }
  }

  private void capture(Minecraft minecraft, String label) throws IOException {
    if (!Arrays.asList(PICTURES).contains(label) || pictures.contains(label)) {
      return;
    }
    // Camera rotation applies on the next frame; hold the framing before capturing it.
    if (!label.equals(framing)) {
      framing = label;
      framedFrames = 0;
    }
    if (++framedFrames < FRAMING_FRAMES) {
      return;
    }
    File evidence = new File(directory, "bosslab-evidence");
    if (!evidence.isDirectory() && !evidence.mkdirs()) {
      throw new IOException("Cannot create screenshot directory");
    }
    BossLab.LOGGER.info(
        ScreenShotHelper.func_74292_a(
            evidence,
            run + "-WARDEN-" + label + ".png",
            minecraft.displayWidth,
            minecraft.displayHeight));
    pictures.add(label);
    pictureCount = pictures.size();
  }

  private static void face(EntityLiving viewer, double x, double y, double z) {
    double dx = x - viewer.posX;
    double dz = z - viewer.posZ;
    double dy = y - (viewer.posY + viewer.getEyeHeight());
    viewer.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    viewer.rotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
