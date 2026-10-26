package dev.hexxitlab.warden;

import dev.hexxitlab.BossCombat;
import dev.hexxitlab.BossLab;
import dev.hexxitlab.LabBoss;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Server-owned grounded duel. Its front guard deflects hits until it commits to a strike. Each
 * strike tracks where the opponent is heading, then locks for its final ticks: steady movement
 * walks into a strike, and only a reaction to the lock escapes it.
 */
public final class Warden extends EntityLiving implements IBossDisplayData, LabBoss {
  public static final int MAX_HEALTH = 4000;
  public static final int KINDLE_HEALTH = 2400;
  public static final int RECLAIM_HEALTH = 1000;
  public static final float GUARD_FACTOR = 0.25F;
  public static final double CLEAVE_RADIUS = 5.5;
  public static final double CLEAVE_ARC = 50;
  public static final double BACKHAND_RADIUS = 5;
  // Covers the whole rear half that sends a step to the backhand, plus the flanks.
  public static final double BACKHAND_ARC = 100;
  public static final double SLAM_RADIUS = 3.5;
  public static final double LEAP_RADIUS = 4;
  public static final double HOOK_RADIUS = 1;
  public static final double CHEST_HEIGHT = 2.2;
  public static final double NOVA_BASE_RADIUS = 7;
  public static final double NOVA_GROWTH = 3;
  public static final int CLEAVE_DAMAGE = 30;
  public static final int BACKHAND_DAMAGE = 24;
  public static final int BASH_DAMAGE = 36;
  public static final double BASH_RADIUS = 3.5;
  public static final double BASH_ARC = 60;
  // Guarded melee hits within this many ticks of each other that provoke a Shield Bash.
  public static final int BASH_PROVOCATION = 3;
  private static final int BASH_PROVOCATION_TICKS = 40;
  private static final int BASH_RECOVERY = 16;
  private static final double BASH_KNOCKBACK = 1.6;
  private static final double BASH_LIFT = 0.4;
  public static final int SLAM_DAMAGE = 50;
  public static final int LEAP_DAMAGE = 40;
  public static final int HOOK_DAMAGE = 12;
  public static final int NOVA_DAMAGE = 60;
  public static final double REEL_DISTANCE = 4;
  public static final int KINDLE_CINDERS = 3;
  private static final byte IMPACT_EFFECT = 20;
  private static final byte NOVA_EFFECT = 21;
  private static final byte GUARD_EFFECT = 22;
  // Ticks a deflected hit lights the shield runes.
  private static final int GUARD_FLASH_TICKS = 8;
  private static final double GUARD_ARC = 70;
  private static final double SLAM_REACH = 3;
  public static final double HOOK_RANGE = 22;
  private static final double REACH_LOW = -1.5;
  private static final double REACH_HIGH = 3;
  private static final double CLOSE_DISTANCE = 3;
  private static final double OPEN_DISTANCE = 6;
  // A Slam reaches 3 + 3.5 blocks; an opponent heading beyond that gets a Leap instead.
  private static final double SLAM_CHOICE = 6;
  private static final double MEDIUM_DISTANCE = 8;
  // Leading is capped so a burst of flight does not send a strike absurdly far ahead.
  private static final double LEAD_LIMIT = 8;
  // Larger jumps between ticks are teleports or reels, not movement worth leading.
  private static final double TELEPORT_STEP = 3;
  private static final double VELOCITY_SMOOTHING = 0.4;
  private static final double LEAP_DISTANCE = 14;
  // Rolling behind a cleave leaves the opponent outside the front half even after the chain turn.
  private static final double BEHIND_ANGLE = 90;
  private static final double LEAP_HEIGHT = 5;
  private static final double CHAIN_SPEED = 1.25;
  private static final double ENGAGE_RANGE = 128;
  public static final int LEAP_AIRTIME = 16;
  private static final int REEL_TICKS = 6;
  private static final int HOOK_MISS_RECOVERY = 24;
  private static final int NOVA_RECOVERY = 40;
  // Clean hits during openings build posture; a full bar staggers it, open to heavier blows.
  public static final int POSTURE_LIMIT = 240;
  public static final float STAGGER_MULTIPLIER = 1.5F;
  public static final int STAGGER_TICKS = 80;
  // Posture drains one point per tick once it has gone this long without a clean hit.
  private static final int POSTURE_GRACE = 100;
  private static final int STUCK_TICKS = 40;
  private static final int HOOK_COOLDOWN = 160;
  private static final int KINDLE_TICKS = 40;
  private static final int RECLAIM_LIMIT = 120;
  public static final int NOVA_WINDUP = 30;
  private static final int SWARM_SIZE = 4;
  private static final int RAISE_INTERVAL = 160;
  private static final int DIVE_INTERVAL = 50;
  private static final int SPAWN_DELAY = 60;
  private static final int RESET_TICKS = 300;
  // Per-phase tuning, indexed by phase - 1. A strike tracks for its windup minus its commit, then
  // holds still for the commit: the commit is the reaction window.
  private static final int[] CLEAVE_WINDUP = {20, 17, 14};
  private static final int[] CLEAVE_COMMIT = {12, 11, 9};
  // The Backhand punishes standing behind it; it never tracks.
  private static final int[] BACKHAND_WINDUP = {12, 11, 9};
  // A reflex: locked from the start, like the Backhand.
  private static final int[] BASH_WINDUP = {10, 9, 8};
  private static final int[] SLAM_WINDUP = {24, 21, 18};
  private static final int[] SLAM_COMMIT = {18, 16, 14};
  // The landing locks shortly before takeoff; the flight adds to the reaction window.
  private static final int[] LEAP_WINDUP = {18, 16, 12};
  private static final int[] LEAP_COMMIT = {6, 5, 4};
  private static final int[] HOOK_WINDUP = {20, 17, 14};
  private static final int[] HOOK_COMMIT = {10, 9, 8};
  private static final int[] FINISHER_RECOVERY = {50, 40, 30};
  private static final int[] COMBO_LENGTH = {2, 3, 4};
  private static final int[] COMBO_GAP = {8, 7, 5};
  private static final int[] COOLDOWN = {20, 14, 8};
  private static final float[] TURN_RATE = {12, 15, 20};
  private static final float[] CHAIN_TURN_RATE = {9, 12, 16};
  // Players walk 0.22 blocks per tick and sprint 0.28.
  private static final double[] WALK_SPEED = {0.25, 0.28, 0.31};
  private static final int HEALTH = 20;
  private static final int ACTION = 21;
  private static final int TIMER = 22;
  private static final int STRIKE_X = 23;
  private static final int STRIKE_Y = 24;
  private static final int STRIKE_Z = 25;
  private static final int STRIKE_YAW = 26;
  private static final int PHASE = 27;
  private static final int NOVA_RADIUS = 28;
  private static final int POSTURE = 29;

  public enum Action {
    HUNT,
    CHAIN,
    CLEAVE,
    BACKHAND,
    BASH,
    SLAM,
    LEAP,
    AIRBORNE,
    HOOK,
    REEL,
    RECOVER,
    STAGGER,
    KINDLE,
    RECLAIM,
    NOVA
  }

  private final List<Cinder> swarm = new ArrayList<Cinder>();
  private EntityPlayer opponent;
  private int remaining = SPAWN_DELAY;
  private int absentTicks;
  private int comboStep;
  private int comboLength;
  private int stuckTicks;
  private double closestApproach = Double.MAX_VALUE;
  private int hookCooldown;
  private int raiseTimer;
  private int diveTimer;
  private int ash;
  private int postureIdle;
  private int guardedStreak;
  private int lastGuardedTick;
  private Vec3 travelStart;
  private EntityPlayer tracked;
  private double trackedX;
  private double trackedZ;
  private double velocityX;
  private double velocityZ;
  // Client presentation only: the action being shown, since when, and what it followed.
  private Action shownAction = Action.HUNT;
  private Action previousAction = Action.HUNT;
  private int actionAge;
  private int guardFlash;

  public Warden(World world) {
    super(world);
    setSize(1.6F, 3.4F);
    setEntityHealth(MAX_HEALTH);
    isImmuneToFire = true;
    stepHeight = 1;
    experienceValue = 200;
    renderDistanceWeight = 8;
    // Warnings extend far beyond the body; keep drawing them when the body is off-screen.
    ignoreFrustumCheck = true;
    texture = "/mods/hexxitbosslab/textures/entity/warden.png";
  }

  @Override
  protected void entityInit() {
    super.entityInit();
    dataWatcher.addObject(HEALTH, Integer.valueOf(MAX_HEALTH));
    dataWatcher.addObject(ACTION, Integer.valueOf(Action.HUNT.ordinal()));
    dataWatcher.addObject(TIMER, Integer.valueOf(SPAWN_DELAY));
    dataWatcher.addObject(STRIKE_X, Integer.valueOf(0));
    dataWatcher.addObject(STRIKE_Y, Integer.valueOf(0));
    dataWatcher.addObject(STRIKE_Z, Integer.valueOf(0));
    dataWatcher.addObject(STRIKE_YAW, Integer.valueOf(0));
    dataWatcher.addObject(PHASE, Integer.valueOf(1));
    dataWatcher.addObject(NOVA_RADIUS, Integer.valueOf(0));
    dataWatcher.addObject(POSTURE, Integer.valueOf(0));
  }

  @Override
  public int getMaxHealth() {
    return MAX_HEALTH;
  }

  @Override
  public int getBossHealth() {
    return getHealth();
  }

  @Override
  public String getEntityName() {
    return "The Ashen Warden";
  }

  @Override
  public String describe() {
    return "Warden: "
        + getHealth()
        + "/"
        + MAX_HEALTH
        + " health, phase "
        + getPhase()
        + ", "
        + getAction()
        + ", "
        + swarm.size()
        + " cinders";
  }

  @Override
  protected boolean canDespawn() {
    return false;
  }

  @Override
  public boolean canBePushed() {
    return false;
  }

  @Override
  public boolean canBreatheUnderwater() {
    return true;
  }

  @Override
  public void knockBack(Entity attacker, int damage, double x, double z) {
    /* Heavy construct. */
  }

  @Override
  protected void fall(float distance) {
    /* Leaps land by design. */
  }

  @Override
  protected String getHurtSound() {
    return "mob.irongolem.hit";
  }

  @Override
  protected String getDeathSound() {
    return "mob.irongolem.death";
  }

  @Override
  protected void playStepSound(int x, int y, int z, int block) {
    playSound("mob.irongolem.walk", 1, 0.6F);
  }

  public Action getAction() {
    return Action.values()[dataWatcher.getWatchableObjectInt(ACTION)];
  }

  public int getActionTicks() {
    return dataWatcher.getWatchableObjectInt(TIMER);
  }

  public int getPhase() {
    return dataWatcher.getWatchableObjectInt(PHASE);
  }

  /** Locked world point: sweep origin, slam or landing centre, or hook end. */
  public Vec3 getStrike() {
    return Vec3.createVectorHelper(
        BossCombat.readFloat(dataWatcher, STRIKE_X),
        BossCombat.readFloat(dataWatcher, STRIKE_Y),
        BossCombat.readFloat(dataWatcher, STRIKE_Z));
  }

  public float getStrikeYaw() {
    return (float) BossCombat.readFloat(dataWatcher, STRIKE_YAW);
  }

  public double getNovaRadius() {
    return BossCombat.readFloat(dataWatcher, NOVA_RADIUS);
  }

  public int getSwarmSize() {
    return swarm.size();
  }

  EntityPlayer getOpponent() {
    return opponent;
  }

  /** Harrowing actions cannot be damaged or interrupted. */
  public boolean isWarded() {
    Action action = getAction();
    return action == Action.KINDLE || action == Action.RECLAIM || action == Action.NOVA;
  }

  /** The guard drops during finisher recovery and is gone for good in phase three. */
  public boolean isExposed() {
    Action action = getAction();
    return getPhase() == 3 || action == Action.RECOVER || action == Action.STAGGER;
  }

  /** Whether the current strike's warning has locked, or its leap is in flight. */
  public boolean isCommitted() {
    Action action = getAction();
    return action == Action.AIRBORNE
        || (isStrike(action) && getActionTicks() <= commit(action, getPhase()));
  }

  public static boolean isStrike(Action action) {
    return windups(action) != null;
  }

  public static int windup(Action action, int phase) {
    return windups(action)[phase - 1];
  }

  public static int commit(Action action, int phase) {
    return commits(action)[phase - 1];
  }

  private static int[] windups(Action action) {
    switch (action) {
      case CLEAVE:
        return CLEAVE_WINDUP;
      case BACKHAND:
        return BACKHAND_WINDUP;
      case BASH:
        return BASH_WINDUP;
      case SLAM:
        return SLAM_WINDUP;
      case LEAP:
        return LEAP_WINDUP;
      case HOOK:
        return HOOK_WINDUP;
      default:
        return null;
    }
  }

  private static int[] commits(Action action) {
    switch (action) {
      case CLEAVE:
        return CLEAVE_COMMIT;
      case BACKHAND:
        return BACKHAND_WINDUP;
      case BASH:
        return BASH_WINDUP;
      case SLAM:
        return SLAM_COMMIT;
      case LEAP:
        return LEAP_COMMIT;
      case HOOK:
        return HOOK_COMMIT;
      default:
        throw new IllegalArgumentException("Not a strike: " + action);
    }
  }

  public static int guarded(int amount) {
    return Math.max(1, Math.round(amount * GUARD_FACTOR));
  }

  @Override
  public boolean attackEntityFrom(DamageSource source, int amount) {
    if (source == DamageSource.inWall || source == DamageSource.drown) {
      return false;
    }
    if (isWarded() && source != DamageSource.outOfWorld) {
      deflect(0.6F, 1.6F);
      return false;
    }
    // It can answer from anywhere it can be hurt: shots from beyond its hook's reach glance off.
    Entity shooter = source.getEntity();
    if (source.isProjectile() && shooter != null && getDistanceToEntity(shooter) > HOOK_RANGE) {
      deflect(0.6F, 1.6F);
      return false;
    }
    // Projectiles are judged by where they strike, not by where the shooter stands.
    Entity striker = source.getSourceOfDamage();
    boolean guarding = !isExposed() && striker != null && Math.abs(angleTo(striker)) <= GUARD_ARC;
    if (guarding) {
      deflect(0.4F, 1.8F);
      // The shield stops shots outright: the bow pays only from behind or into an opening.
      if (source.isProjectile()) {
        return false;
      }
      amount = guarded(amount);
      if (!worldObj.isRemote) {
        provoke();
      }
    } else if (getAction() == Action.STAGGER) {
      amount = Math.round(amount * STAGGER_MULTIPLIER);
    }
    boolean opening = isOpening();
    boolean landed = super.attackEntityFrom(source, amount);
    if (landed && !guarding && opening && !worldObj.isRemote) {
      buildPosture(amount);
    }
    return landed;
  }

  /** A clank, and a flare of the shield runes or ward on clients. */
  private void deflect(float volume, float pitch) {
    if (!worldObj.isRemote) {
      playSound("random.anvil_land", volume, pitch);
      worldObj.setEntityState(this, GUARD_EFFECT);
    }
  }

  /**
   * Hammering the raised shield is answered with a Shield Bash unless a strike is already locked.
   */
  private void provoke() {
    if (ticksExisted - lastGuardedTick > BASH_PROVOCATION_TICKS) {
      guardedStreak = 0;
    }
    lastGuardedTick = ticksExisted;
    Action action = getAction();
    boolean free =
        action == Action.HUNT || action == Action.CHAIN || (isStrike(action) && !isCommitted());
    if (++guardedStreak >= BASH_PROVOCATION && free && opponent != null) {
      guardedStreak = 0;
      lock(Action.BASH);
    }
  }

  /** Locked windups and recoveries: the moments a clean hit builds toward a posture break. */
  private boolean isOpening() {
    Action action = getAction();
    return action == Action.RECOVER || (isCommitted() && action != Action.AIRBORNE);
  }

  private void buildPosture(int amount) {
    postureIdle = 0;
    int posture = getPosture() + amount;
    if (posture < POSTURE_LIMIT) {
      dataWatcher.updateObject(POSTURE, Integer.valueOf(posture));
      return;
    }
    // Breaking posture cancels whatever it was winding up.
    dataWatcher.updateObject(POSTURE, Integer.valueOf(0));
    changeAction(Action.STAGGER, STAGGER_TICKS);
    playSound("random.anvil_break", 2, 0.6F);
    playSound("mob.irongolem.death", 2, 1.4F);
    BossLab.LOGGER.info("Warden posture broken: " + getPersistentID());
  }

  public int getPosture() {
    return dataWatcher.getWatchableObjectInt(POSTURE);
  }

  @Override
  public void onUpdate() {
    super.onUpdate();
    // The body faces where the Warden aims, never where vanilla's walk animation would turn it.
    renderYawOffset = rotationYaw;
    rotationYawHead = rotationYaw;
  }

  @Override
  public void moveEntityWithHeading(float strafe, float forward) {
    // Clients follow server positions; leaps are scripted flight through terrain.
    if (!worldObj.isRemote && getAction() != Action.AIRBORNE) {
      super.moveEntityWithHeading(strafe, forward);
    }
  }

  @Override
  public void onLivingUpdate() {
    super.onLivingUpdate();
    fallDistance = 0;
    if (worldObj.isRemote) {
      setEntityHealth(dataWatcher.getWatchableObjectInt(HEALTH));
      Action action = getAction();
      if (action == shownAction) {
        actionAge++;
      } else {
        previousAction = shownAction;
        shownAction = action;
        actionAge = 0;
        WardenEffects.changed(this, previousAction, action);
      }
      if (guardFlash > 0) {
        guardFlash--;
      }
      WardenEffects.ambient(this);
      return;
    }
    dataWatcher.updateObject(HEALTH, Integer.valueOf(getHealth()));
    dataWatcher.updateObject(TIMER, Integer.valueOf(remaining));
  }

  @Override
  protected void updateEntityActionState() {
    // Vanilla calls this on the server just before travel, so motion chosen here moves this tick
    // with collision, step-up and gravity.
    motionX = motionZ = 0;
    isJumping = false;
    if (worldObj.difficultySetting == 0) {
      setDead();
      return;
    }
    if (!BossCombat.isOpponent(this, opponent, ENGAGE_RANGE)) {
      opponent = worldObj.getClosestVulnerablePlayerToEntity(this, ENGAGE_RANGE);
    }
    if (!BossCombat.isOpponent(this, opponent, ENGAGE_RANGE)) {
      opponent = null;
      changeAction(Action.HUNT, SPAWN_DELAY);
      if (++absentTicks == RESET_TICKS) {
        setEntityHealth(MAX_HEALTH);
        setPhase(1);
        dataWatcher.updateObject(POSTURE, Integer.valueOf(0));
        dismissSwarm();
        BossLab.LOGGER.info("Warden recovered after disengagement: " + getPersistentID());
      }
      return;
    }
    absentTicks = 0;
    tickCombat();
  }

  private void tickCombat() {
    int phase = getPhase();
    observeOpponent();
    if (++postureIdle > POSTURE_GRACE && getPosture() > 0) {
      dataWatcher.updateObject(POSTURE, Integer.valueOf(getPosture() - 1));
    }
    if (!isWarded()) {
      // Phase changes interrupt anything, including a leap in flight.
      if (phase == 1 && getHealth() <= KINDLE_HEALTH) {
        changeAction(Action.KINDLE, KINDLE_TICKS);
        harrowingSound();
        return;
      }
      if (phase == 2 && getHealth() <= RECLAIM_HEALTH) {
        beginReclaim();
        return;
      }
      if (phase == 2) {
        conductSwarm();
      }
    }
    switch (getAction()) {
      case HUNT:
        hunt();
        break;
      case CHAIN:
        turnToward(opponent.posX, opponent.posZ, CHAIN_TURN_RATE[phase - 1]);
        approach(WALK_SPEED[phase - 1] * CHAIN_SPEED);
        if (--remaining <= 0) {
          nextStep();
        }
        break;
      case CLEAVE:
      case BACKHAND:
      case BASH:
      case SLAM:
      case LEAP:
      case HOOK:
        windUp(getAction());
        break;
      case AIRBORNE:
        fly();
        break;
      case REEL:
        reel();
        break;
      case RECOVER:
      case STAGGER:
        if (--remaining <= 0) {
          changeAction(Action.HUNT, COOLDOWN[phase - 1]);
        }
        break;
      case RECLAIM:
        reclaim();
        break;
      default:
        if (--remaining <= 0) {
          execute(getAction());
        }
        break;
    }
  }

  private void hunt() {
    int phase = getPhase();
    turnToward(opponent.posX, opponent.posZ, TURN_RATE[phase - 1]);
    approach(WALK_SPEED[phase - 1]);
    double horizontal = horizontalDistance(opponent);
    // Kiting and terrain both look like a lack of progress; a leap answers either.
    if (horizontal < closestApproach - 1) {
      closestApproach = horizontal;
      stuckTicks = 0;
    } else if (horizontal > OPEN_DISTANCE) {
      stuckTicks++;
    }
    if (hookCooldown > 0) {
      hookCooldown--;
    }
    if (--remaining > 0) {
      return;
    }
    remaining = 0;
    double rise = opponent.posY - posY;
    boolean visible = canEntityBeSeen(opponent);
    if (rise > REACH_HIGH && getDistanceToEntity(opponent) <= HOOK_RANGE && visible) {
      lock(Action.HOOK);
    } else if (horizontal <= OPEN_DISTANCE && rise <= REACH_HIGH) {
      comboStep = 0;
      comboLength = COMBO_LENGTH[phase - 1];
      nextStep();
    } else if (hookCooldown == 0
        && horizontal >= MEDIUM_DISTANCE
        && horizontal <= LEAP_DISTANCE
        && visible) {
      lock(Action.HOOK);
    } else if (horizontal > LEAP_DISTANCE || stuckTicks >= STUCK_TICKS) {
      lock(Action.LEAP);
    }
  }

  /** Chooses each combo step from the opponent's position at that moment. */
  private void nextStep() {
    double horizontal = horizontalDistance(opponent);
    if (++comboStep >= comboLength || horizontal > OPEN_DISTANCE) {
      // Backing away from a combo earns a Leap after you, not a Slam that falls short.
      Vec3 heading = ahead(feet(), commit(Action.SLAM, getPhase()));
      lock(
          horizontalDistance(heading.xCoord, heading.zCoord) <= SLAM_CHOICE
              ? Action.SLAM
              : Action.LEAP);
    } else {
      lock(Math.abs(angleTo(opponent)) > BEHIND_ANGLE ? Action.BACKHAND : Action.CLEAVE);
    }
  }

  private void lock(Action action) {
    changeAction(action, windup(action, getPhase()));
    if (action == Action.HOOK) {
      hookCooldown = HOOK_COOLDOWN;
    }
    aim(action);
    playSound(
        action == Action.HOOK ? "random.bow" : "mob.irongolem.throw",
        2,
        action == Action.SLAM || action == Action.LEAP ? 0.4F : 0.7F);
  }

  /** Tracks the opponent until the commit, then holds the locked warning still. */
  private void windUp(Action action) {
    if (--remaining <= 0) {
      execute(action);
    } else if (remaining >= commit(action, getPhase())) {
      if (action == Action.CLEAVE) {
        approach(WALK_SPEED[getPhase() - 1]);
      }
      aim(action);
    }
  }

  /** Aims at where the opponent is heading when the strike lands, measured from its commit. */
  private void aim(Action action) {
    int lead = commit(action, getPhase()) + (action == Action.LEAP ? LEAP_AIRTIME : 0);
    Vec3 heading = ahead(feet(), lead);
    double yaw = rotationYaw;
    double x = posX;
    double y = posY;
    double z = posZ;
    if (action == Action.CLEAVE) {
      turnToward(heading.xCoord, heading.zCoord, TURN_RATE[getPhase() - 1]);
      yaw = rotationYaw;
    } else if (action == Action.BASH) {
      // Provoked from the front, so this squares the shield up rather than spinning round.
      yaw = yawToward(heading.xCoord, heading.zCoord);
      rotationYaw = (float) yaw;
    } else if (action == Action.SLAM) {
      // The body turns at its usual rate, but the ground strike never lags behind the opponent.
      turnToward(heading.xCoord, heading.zCoord, TURN_RATE[getPhase() - 1]);
      yaw = yawToward(heading.xCoord, heading.zCoord);
      x += forwardX(yaw) * SLAM_REACH;
      z += forwardZ(yaw) * SLAM_REACH;
    } else if (action == Action.LEAP) {
      x = heading.xCoord;
      z = heading.zCoord;
      y = BossCombat.groundBelow(worldObj, x, opponent.posY, z);
      yaw = yawToward(x, z);
      rotationYaw = (float) yaw;
    } else if (action == Action.HOOK) {
      Vec3 start = chest();
      Vec3 target = ahead(BossCombat.center(opponent), lead);
      double length = start.distanceTo(target);
      Vec3 end =
          start.addVector(
              (target.xCoord - start.xCoord) / length * HOOK_RANGE,
              (target.yCoord - start.yCoord) / length * HOOK_RANGE,
              (target.zCoord - start.zCoord) / length * HOOK_RANGE);
      MovingObjectPosition block = BossCombat.trace(worldObj, start, end);
      if (block != null) {
        end = block.hitVec;
      }
      x = end.xCoord;
      y = end.yCoord;
      z = end.zCoord;
      yaw = yawToward(target.xCoord, target.zCoord);
      rotationYaw = (float) yaw;
    }
    BossCombat.writeFloat(dataWatcher, STRIKE_X, x);
    BossCombat.writeFloat(dataWatcher, STRIKE_Y, y);
    BossCombat.writeFloat(dataWatcher, STRIKE_Z, z);
    BossCombat.writeFloat(dataWatcher, STRIKE_YAW, yaw);
  }

  /** Smoothed horizontal velocity of the opponent, from its server positions. */
  private void observeOpponent() {
    double dx = opponent.posX - trackedX;
    double dz = opponent.posZ - trackedZ;
    if (opponent != tracked || dx * dx + dz * dz > TELEPORT_STEP * TELEPORT_STEP) {
      tracked = opponent;
      velocityX = velocityZ = 0;
    } else {
      velocityX += (dx - velocityX) * VELOCITY_SMOOTHING;
      velocityZ += (dz - velocityZ) * VELOCITY_SMOOTHING;
    }
    trackedX = opponent.posX;
    trackedZ = opponent.posZ;
  }

  private void forgetVelocity() {
    velocityX = velocityZ = 0;
    trackedX = opponent.posX;
    trackedZ = opponent.posZ;
  }

  /** The point displaced along the opponent's current heading for the given ticks. */
  Vec3 ahead(Vec3 point, int ticks) {
    double dx = velocityX * ticks;
    double dz = velocityZ * ticks;
    double length = Math.sqrt(dx * dx + dz * dz);
    double scale = length > LEAD_LIMIT ? LEAD_LIMIT / length : 1;
    return point.addVector(dx * scale, 0, dz * scale);
  }

  private Vec3 feet() {
    return Vec3.createVectorHelper(opponent.posX, opponent.posY, opponent.posZ);
  }

  private void execute(Action action) {
    int phase = getPhase() - 1;
    if (action == Action.CLEAVE || action == Action.BACKHAND) {
      boolean cleave = action == Action.CLEAVE;
      sweep(
          getStrikeYaw() + (cleave ? 0 : 180),
          cleave ? CLEAVE_ARC : BACKHAND_ARC,
          cleave ? CLEAVE_RADIUS : BACKHAND_RADIUS,
          cleave ? CLEAVE_DAMAGE : BACKHAND_DAMAGE);
      playSound("mob.enderdragon.wings", 2, 1.4F);
      changeAction(Action.CHAIN, COMBO_GAP[phase]);
    } else if (action == Action.BASH) {
      double yaw = getStrikeYaw();
      for (EntityPlayer player : sweep(yaw, BASH_ARC, BASH_RADIUS, BASH_DAMAGE)) {
        player.addVelocity(
            forwardX(yaw) * BASH_KNOCKBACK, BASH_LIFT, forwardZ(yaw) * BASH_KNOCKBACK);
        player.velocityChanged = true;
      }
      playSound("random.anvil_land", 2, 0.5F);
      changeAction(Action.RECOVER, BASH_RECOVERY);
    } else if (action == Action.SLAM) {
      impact(getStrike(), SLAM_RADIUS, SLAM_DAMAGE);
      changeAction(Action.RECOVER, FINISHER_RECOVERY[phase]);
    } else if (action == Action.LEAP) {
      travelStart = Vec3.createVectorHelper(posX, posY, posZ);
      Vec3 target = getStrike();
      rotationYaw = (float) yawToward(target.xCoord, target.zCoord);
      changeAction(Action.AIRBORNE, LEAP_AIRTIME);
      playSound("mob.enderdragon.wings", 3, 0.6F);
    } else if (action == Action.HOOK) {
      hook();
    } else if (action == Action.KINDLE) {
      for (int i = 0; i < KINDLE_CINDERS; i++) {
        raise(i * Math.PI * 2 / KINDLE_CINDERS);
      }
      setPhase(2);
      raiseTimer = RAISE_INTERVAL;
      diveTimer = DIVE_INTERVAL;
      changeAction(Action.HUNT, COOLDOWN[1]);
      BossLab.LOGGER.info("Warden kindled its swarm: " + getPersistentID());
    } else if (action == Action.NOVA) {
      for (Object value : worldObj.playerEntities) {
        EntityPlayer player = (EntityPlayer) value;
        if (BossCombat.isOpponent(this, player, ENGAGE_RANGE)
            && chest().distanceTo(BossCombat.center(player)) <= getNovaRadius()
            && BossCombat.unobstructed(worldObj, chest(), BossCombat.center(player))) {
          strike(player, this, NOVA_DAMAGE, true);
        }
      }
      worldObj.setEntityState(this, NOVA_EFFECT);
      playSound("random.explode", 4, 0.5F);
      playSound("random.anvil_break", 2, 0.5F);
      setPhase(3);
      changeAction(Action.RECOVER, NOVA_RECOVERY);
      BossLab.LOGGER.info("Warden guard shattered after absorbing " + ash + " cinders");
    }
  }

  private void fly() {
    double t = 1 - --remaining / (double) LEAP_AIRTIME;
    Vec3 target = getStrike();
    setPosition(
        travelStart.xCoord + (target.xCoord - travelStart.xCoord) * t,
        travelStart.yCoord
            + (target.yCoord - travelStart.yCoord) * t
            + 4 * t * (1 - t) * LEAP_HEIGHT,
        travelStart.zCoord + (target.zCoord - travelStart.zCoord) * t);
    if (remaining <= 0) {
      setPosition(target.xCoord, target.yCoord, target.zCoord);
      boolean landed = impact(target, LEAP_RADIUS, LEAP_DAMAGE);
      // A Leap that misses never buys a free opening: it throws the chain after the opponent.
      if (!landed && getDistanceToEntity(opponent) <= HOOK_RANGE && canEntityBeSeen(opponent)) {
        lock(Action.HOOK);
      } else {
        changeAction(Action.RECOVER, FINISHER_RECOVERY[getPhase() - 1]);
      }
    }
  }

  private void hook() {
    Vec3 start = chest();
    Vec3 end = getStrike();
    boolean hooked = false;
    for (Object value : worldObj.playerEntities) {
      EntityPlayer player = (EntityPlayer) value;
      if (BossCombat.isOpponent(this, player, ENGAGE_RANGE)
          && BossCombat.inCorridor(
              start, end, BossCombat.center(player), HOOK_RADIUS + BossCombat.PLAYER_HIT_RADIUS)
          && BossCombat.unobstructed(worldObj, start, BossCombat.center(player))) {
        strike(player, this, HOOK_DAMAGE, false);
        hooked |= player == opponent;
      }
    }
    if (!hooked) {
      changeAction(Action.RECOVER, HOOK_MISS_RECOVERY);
      return;
    }
    playSound("random.bowhit", 2, 0.5F);
    travelStart = Vec3.createVectorHelper(opponent.posX, opponent.posY, opponent.posZ);
    if (opponent.isEntityAlive() && reelDestination() != null) {
      changeAction(Action.REEL, REEL_TICKS);
    } else {
      lock(Action.SLAM);
    }
  }

  /** Reels the hooked opponent onto the ground in front of the Warden, then slams. */
  private void reel() {
    Vec3 end = reelDestination();
    if (end == null) {
      forgetVelocity();
      lock(Action.SLAM);
      return;
    }
    double t = 1 - --remaining / (double) REEL_TICKS;
    opponent.setPositionAndUpdate(
        travelStart.xCoord + (end.xCoord - travelStart.xCoord) * t,
        travelStart.yCoord + (end.yCoord - travelStart.yCoord) * t,
        travelStart.zCoord + (end.zCoord - travelStart.zCoord) * t);
    opponent.motionX = opponent.motionY = opponent.motionZ = 0;
    opponent.fallDistance = 0;
    if (remaining <= 0) {
      rotationYaw = (float) yawToward(opponent.posX, opponent.posZ);
      // The reel's own pull is not the opponent's heading.
      forgetVelocity();
      lock(Action.SLAM);
    }
  }

  private Vec3 reelDestination() {
    double yaw = rotationYaw;
    double x = posX + forwardX(yaw) * REEL_DISTANCE;
    double z = posZ + forwardZ(yaw) * REEL_DISTANCE;
    AxisAlignedBB space =
        AxisAlignedBB.getAABBPool().getAABB(x - 0.3, posY, z - 0.3, x + 0.3, posY + 1.8, z + 0.3);
    return worldObj.getCollidingBoundingBoxes(opponent, space).isEmpty()
        ? Vec3.createVectorHelper(x, posY, z)
        : null;
  }

  /** Strikes every opponent in the wedge and returns them. */
  private List<EntityPlayer> sweep(double yaw, double arc, double radius, int damage) {
    List<EntityPlayer> struck = new ArrayList<EntityPlayer>();
    for (Object value : worldObj.playerEntities) {
      EntityPlayer player = (EntityPlayer) value;
      if (BossCombat.isOpponent(this, player, ENGAGE_RANGE)
          && withinReach(player.posY - posY)
          && horizontalDistance(player) <= radius + BossCombat.PLAYER_HIT_RADIUS
          && Math.abs(MathHelper.wrapAngleTo180_double(yawToward(player.posX, player.posZ) - yaw))
              <= arc
          && BossCombat.unobstructed(worldObj, chest(), BossCombat.center(player))) {
        strike(player, this, damage, false);
        struck.add(player);
      }
    }
    return struck;
  }

  /** Strikes every opponent in the circle; returns whether the current opponent was caught. */
  private boolean impact(Vec3 point, double radius, int damage) {
    Vec3 origin = point.addVector(0, 1, 0);
    boolean caught = false;
    for (Object value : worldObj.playerEntities) {
      EntityPlayer player = (EntityPlayer) value;
      double dx = player.posX - point.xCoord;
      double dz = player.posZ - point.zCoord;
      if (BossCombat.isOpponent(this, player, ENGAGE_RANGE)
          && withinReach(player.posY - point.yCoord)
          && Math.sqrt(dx * dx + dz * dz) <= radius + BossCombat.PLAYER_HIT_RADIUS
          && BossCombat.unobstructed(worldObj, origin, BossCombat.center(player))) {
        strike(player, this, damage, false);
        caught |= player == opponent;
      }
    }
    worldObj.setEntityState(this, IMPACT_EFFECT);
    playSound("random.explode", 3, 0.7F);
    playSound("mob.zombie.woodbreak", 2, 0.5F);
    return caught;
  }

  private static boolean withinReach(double rise) {
    return rise >= REACH_LOW && rise <= REACH_HIGH;
  }

  /** The Warden keeps at most one cinder diving, so swarm pressure stays readable. */
  private void conductSwarm() {
    pruneSwarm();
    if (swarm.size() < SWARM_SIZE && --raiseTimer <= 0) {
      raise(rand.nextDouble() * Math.PI * 2);
      raiseTimer = RAISE_INTERVAL;
    }
    if (--diveTimer > 0) {
      return;
    }
    List<Cinder> ready = new ArrayList<Cinder>();
    for (Cinder cinder : swarm) {
      if (cinder.isAttacking()) {
        return;
      }
      if (cinder.getState() == Cinder.State.ORBIT) {
        ready.add(cinder);
      }
    }
    if (!ready.isEmpty()) {
      ready.get(rand.nextInt(ready.size())).mark(opponent);
      diveTimer = DIVE_INTERVAL;
    }
  }

  private void raise(double angle) {
    Cinder cinder = new Cinder(worldObj, this, angle);
    cinder.setPosition(posX + Math.cos(angle) * 2, posY + 3, posZ + Math.sin(angle) * 2);
    if (!worldObj.spawnEntityInWorld(cinder)) {
      throw new IllegalStateException("Cinder spawn rejected");
    }
    swarm.add(cinder);
    playSound("mob.blaze.breathe", 2, 0.6F);
  }

  private void beginReclaim() {
    pruneSwarm();
    ash = 0;
    for (Cinder cinder : swarm) {
      cinder.recall();
    }
    BossCombat.writeFloat(dataWatcher, NOVA_RADIUS, NOVA_BASE_RADIUS);
    changeAction(Action.RECLAIM, RECLAIM_LIMIT);
    harrowingSound();
  }

  private void reclaim() {
    pruneSwarm();
    if (!swarm.isEmpty() && --remaining > 0) {
      return;
    }
    // A safety limit only: stragglers still count, so shooting them in flight is what matters.
    for (Cinder cinder : swarm) {
      absorb(cinder);
    }
    pruneSwarm();
    changeAction(Action.NOVA, NOVA_WINDUP);
    playSound("mob.ghast.charge", 4, 0.5F);
  }

  void absorb(Cinder cinder) {
    if (cinder.isDead) {
      return;
    }
    cinder.setDead();
    ash++;
    BossCombat.writeFloat(dataWatcher, NOVA_RADIUS, NOVA_BASE_RADIUS + NOVA_GROWTH * ash);
    playSound("mob.ghast.fireball", 2, 0.6F);
  }

  private void pruneSwarm() {
    for (int i = swarm.size() - 1; i >= 0; i--) {
      if (!swarm.get(i).isEntityAlive()) {
        swarm.remove(i);
      }
    }
  }

  private void dismissSwarm() {
    for (Cinder cinder : swarm) {
      cinder.setDead();
    }
    swarm.clear();
  }

  private void harrowingSound() {
    playSound("mob.blaze.breathe", 4, 0.4F);
  }

  private void turnToward(double x, double z, float rate) {
    double delta = MathHelper.wrapAngleTo180_double(yawToward(x, z) - rotationYaw);
    rotationYaw += (float) Math.max(-rate, Math.min(rate, delta));
  }

  private void approach(double speed) {
    double dx = opponent.posX - posX;
    double dz = opponent.posZ - posZ;
    double horizontal = Math.sqrt(dx * dx + dz * dz);
    if (horizontal > CLOSE_DISTANCE) {
      motionX = dx / horizontal * speed;
      motionZ = dz / horizontal * speed;
      isJumping = isCollidedHorizontally;
    }
  }

  /** Signed degrees between the Warden's facing and the direction to the entity. */
  private double angleTo(Entity entity) {
    return MathHelper.wrapAngleTo180_double(yawToward(entity.posX, entity.posZ) - rotationYaw);
  }

  private double yawToward(double x, double z) {
    return Math.toDegrees(Math.atan2(z - posZ, x - posX)) - 90;
  }

  private static double forwardX(double yaw) {
    return -Math.sin(Math.toRadians(yaw));
  }

  private static double forwardZ(double yaw) {
    return Math.cos(Math.toRadians(yaw));
  }

  private double horizontalDistance(Entity entity) {
    return horizontalDistance(entity.posX, entity.posZ);
  }

  private double horizontalDistance(double x, double z) {
    double dx = x - posX;
    double dz = z - posZ;
    return Math.sqrt(dx * dx + dz * dz);
  }

  Vec3 chest() {
    return Vec3.createVectorHelper(posX, posY + CHEST_HEIGHT, posZ);
  }

  private void setPhase(int phase) {
    dataWatcher.updateObject(PHASE, Integer.valueOf(phase));
  }

  private void changeAction(Action action, int ticks) {
    if (action == Action.HUNT && getAction() != Action.HUNT) {
      closestApproach = Double.MAX_VALUE;
      stuckTicks = 0;
    }
    noClip = action == Action.AIRBORNE;
    dataWatcher.updateObject(ACTION, Integer.valueOf(action.ordinal()));
    remaining = ticks;
    dataWatcher.updateObject(TIMER, Integer.valueOf(ticks));
  }

  static void strike(EntityPlayer player, Entity source, int damage, boolean ash) {
    // Ash smothers regeneration, so a golden apple cannot out-heal standing in its blows.
    player.removePotionEffect(Potion.regeneration.id);
    player.attackEntityFrom(new WardenDamage(source, ash), damage);
  }

  @Override
  public void handleHealthUpdate(byte status) {
    if (status == IMPACT_EFFECT) {
      WardenEffects.impact(this, getStrike());
    } else if (status == NOVA_EFFECT) {
      WardenEffects.nova(this);
    } else if (status == GUARD_EFFECT) {
      guardFlash = GUARD_FLASH_TICKS;
      WardenEffects.deflect(this);
    } else {
      super.handleHealthUpdate(status);
    }
  }

  /** The action clients are showing; it lags the synchronized action by at most a tick. */
  Action getShownAction() {
    return shownAction;
  }

  Action getPreviousAction() {
    return previousAction;
  }

  /** Client ticks since the shown action began. */
  int getActionAge() {
    return actionAge;
  }

  /** From 1 just after a deflected hit, fading to 0. */
  float getGuardFlash(float partialTick) {
    return Math.max(0, guardFlash - partialTick) / GUARD_FLASH_TICKS;
  }

  @Override
  public void writeEntityToNBT(NBTTagCompound nbt) {
    super.writeEntityToNBT(nbt);
    nbt.setInteger("WardenPhase", getPhase());
  }

  @Override
  public void readEntityFromNBT(NBTTagCompound nbt) {
    super.readEntityFromNBT(nbt);
    setPhase(Math.max(1, Math.min(3, nbt.getInteger("WardenPhase"))));
    // Cinders are never saved; phase two raises a fresh swarm over time after reload.
    raiseTimer = RAISE_INTERVAL;
    diveTimer = DIVE_INTERVAL;
    // Reload cancels transient attacks and gives a fresh warning before any strike.
    changeAction(Action.HUNT, SPAWN_DELAY);
    dataWatcher.updateObject(HEALTH, Integer.valueOf(getHealth()));
  }

  @Override
  public void onDeath(DamageSource source) {
    super.onDeath(source);
    if (!worldObj.isRemote) {
      BossLab.LOGGER.info("Warden defeated: " + getPersistentID());
    }
  }

  @Override
  protected int getDropItemId() {
    return Item.netherStar.itemID;
  }

  @Override
  protected void dropFewItems(boolean playerKill, int looting) {
    if (playerKill) {
      dropItem(Item.netherStar.itemID, 1);
    }
  }

  private static final class WardenDamage extends EntityDamageSource {
    private final boolean ash;

    WardenDamage(Entity source, boolean ash) {
      super("warden", source);
      this.ash = ash;
      // Blows are tuned as raw threat; armour enchantments and effects still mitigate them.
      setDamageBypassesArmor();
      if (ash) {
        setMagicDamage();
      }
    }

    @Override
    public String getDeathMessage(EntityLiving victim) {
      return victim.getEntityName()
          + (ash ? " was reduced to ash by the Ashen Warden" : " was broken by the Ashen Warden");
    }
  }
}
