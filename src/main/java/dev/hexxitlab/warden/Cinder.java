package dev.hexxitlab.warden;

import dev.hexxitlab.BossCombat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** A Warden's swarm member: it orbits, dives when the Warden conducts it, and returns to it. */
public final class Cinder extends EntityLiving {
  public static final int MAX_HEALTH = 10;
  public static final int DAMAGE = 8;
  public static final int MARK_TICKS = 14;
  public static final double HIT_RADIUS = 0.6;
  private static final int BURN_SECONDS = 3;
  private static final double ORBIT_RADIUS = 4.5;
  private static final double ORBIT_HEIGHT = 2.5;
  private static final double ORBIT_SPEED = 0.5;
  private static final double ORBIT_TURN = 0.06;
  private static final double DIVE_SPEED = 1;
  public static final double DIVE_OVERSHOOT = 2;
  private static final double RECALL_SPEED = 0.3;
  private static final double ABSORB_DISTANCE = 1.5;
  private static final double OWNER_RANGE = 128;
  private static final int STATE = 20;
  private static final int TARGET_X = 21;
  private static final int TARGET_Y = 22;
  private static final int TARGET_Z = 23;

  public enum State {
    ORBIT,
    MARK,
    DIVE,
    RECALL
  }

  private Warden owner;
  private double orbitAngle;
  private int remaining;
  private double diveRemaining;
  private Vec3 diveDirection;
  private boolean diveHit;
  // Client presentation only: the state being shown and for how many ticks.
  private State shownState = State.ORBIT;
  private int stateAge;

  public Cinder(World world) {
    super(world);
    setSize(0.6F, 0.6F);
    setEntityHealth(MAX_HEALTH);
    isImmuneToFire = true;
    noClip = true;
    // Warnings extend far beyond the body; keep drawing them when the body is off-screen.
    ignoreFrustumCheck = true;
    texture = "/mob/fire.png";
  }

  Cinder(World world, Warden owner, double orbitAngle) {
    this(world);
    this.owner = owner;
    this.orbitAngle = orbitAngle;
  }

  @Override
  protected void entityInit() {
    super.entityInit();
    dataWatcher.addObject(STATE, Integer.valueOf(State.ORBIT.ordinal()));
    dataWatcher.addObject(TARGET_X, Integer.valueOf(0));
    dataWatcher.addObject(TARGET_Y, Integer.valueOf(0));
    dataWatcher.addObject(TARGET_Z, Integer.valueOf(0));
  }

  @Override
  public int getMaxHealth() {
    return MAX_HEALTH;
  }

  @Override
  public String getEntityName() {
    return "Cinder";
  }

  /** Never saved: a reloaded Warden raises a fresh swarm. */
  @Override
  public boolean addEntityID(NBTTagCompound nbt) {
    return false;
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
    /* Holds its formation. */
  }

  @Override
  public void moveEntityWithHeading(float strafe, float forward) {
    /* Flight is scripted. */
  }

  @Override
  protected void fall(float distance) {
    /* Flying. */
  }

  @Override
  public boolean attackEntityFrom(DamageSource source, int amount) {
    if (source == DamageSource.inWall || source == DamageSource.drown) {
      return false;
    }
    return super.attackEntityFrom(source, amount);
  }

  @Override
  protected String getHurtSound() {
    return "mob.blaze.hit";
  }

  @Override
  protected String getDeathSound() {
    return "mob.blaze.death";
  }

  public State getState() {
    return State.values()[dataWatcher.getWatchableObjectInt(STATE)];
  }

  /** The locked dive target, synchronized so the warning line matches the dive. */
  public Vec3 getTarget() {
    return Vec3.createVectorHelper(
        BossCombat.readFloat(dataWatcher, TARGET_X),
        BossCombat.readFloat(dataWatcher, TARGET_Y),
        BossCombat.readFloat(dataWatcher, TARGET_Z));
  }

  /** Client ticks the current state has been shown. */
  int getStateAge() {
    return stateAge;
  }

  boolean isAttacking() {
    State state = getState();
    return state == State.MARK || state == State.DIVE;
  }

  void mark(EntityPlayer player) {
    Vec3 center = BossCombat.center(player);
    double travel = center.distanceTo(Vec3.createVectorHelper(posX, posY, posZ)) / DIVE_SPEED;
    Vec3 target = owner.ahead(center, MARK_TICKS + (int) Math.ceil(travel));
    BossCombat.writeFloat(dataWatcher, TARGET_X, target.xCoord);
    BossCombat.writeFloat(dataWatcher, TARGET_Y, target.yCoord);
    BossCombat.writeFloat(dataWatcher, TARGET_Z, target.zCoord);
    remaining = MARK_TICKS;
    setState(State.MARK);
    playSound("mob.blaze.breathe", 1, 1.6F);
  }

  void recall() {
    setState(State.RECALL);
  }

  @Override
  public void onLivingUpdate() {
    super.onLivingUpdate();
    motionX = motionY = motionZ = 0;
    if (worldObj.isRemote) {
      State state = getState();
      stateAge = state == shownState ? stateAge + 1 : 0;
      shownState = state;
      WardenEffects.cinder(this);
    }
  }

  @Override
  protected void updateEntityActionState() {
    if (owner == null || !owner.isEntityAlive() || owner.worldObj != worldObj) {
      setDead();
      return;
    }
    State state = getState();
    if (state == State.MARK) {
      if (--remaining <= 0) {
        Vec3 position = Vec3.createVectorHelper(posX, posY, posZ);
        Vec3 target = getTarget();
        diveRemaining = position.distanceTo(target) + DIVE_OVERSHOOT;
        // 1.5.2's subtract returns the argument minus the receiver: this points at the target.
        diveDirection = position.subtract(target).normalize();
        diveHit = false;
        setState(State.DIVE);
        playSound("mob.ghast.fireball", 1, 1.4F);
      }
    } else if (state == State.DIVE) {
      dive();
    } else if (state == State.RECALL) {
      Vec3 chest = owner.chest();
      if (chest.distanceTo(Vec3.createVectorHelper(posX, posY, posZ)) <= ABSORB_DISTANCE) {
        owner.absorb(this);
      } else {
        moveToward(chest, RECALL_SPEED);
      }
    } else {
      EntityPlayer opponent = owner.getOpponent();
      Entity center = opponent != null ? opponent : owner;
      orbitAngle += ORBIT_TURN;
      moveToward(
          Vec3.createVectorHelper(
              center.posX + Math.cos(orbitAngle) * ORBIT_RADIUS,
              center.posY + ORBIT_HEIGHT,
              center.posZ + Math.sin(orbitAngle) * ORBIT_RADIUS),
          ORBIT_SPEED);
    }
  }

  private void dive() {
    Vec3 start = Vec3.createVectorHelper(posX, posY, posZ);
    double step = Math.min(DIVE_SPEED, diveRemaining);
    setPosition(
        posX + diveDirection.xCoord * step,
        posY + diveDirection.yCoord * step,
        posZ + diveDirection.zCoord * step);
    Vec3 end = Vec3.createVectorHelper(posX, posY, posZ);
    for (Object value : worldObj.playerEntities) {
      EntityPlayer player = (EntityPlayer) value;
      if (!diveHit
          && BossCombat.isOpponent(this, player, OWNER_RANGE)
          && BossCombat.inCorridor(
              start, end, BossCombat.center(player), HIT_RADIUS + BossCombat.PLAYER_HIT_RADIUS)) {
        Warden.strike(player, this, DAMAGE, true);
        player.setFire(BURN_SECONDS);
        diveHit = true;
      }
    }
    diveRemaining -= step;
    if (diveRemaining <= 0) {
      EntityPlayer opponent = owner.getOpponent();
      if (opponent != null) {
        // Rejoin the orbit from where the dive ended instead of snapping across it.
        orbitAngle = Math.atan2(posZ - opponent.posZ, posX - opponent.posX);
      }
      setState(State.ORBIT);
    }
  }

  private void moveToward(Vec3 target, double speed) {
    double dx = target.xCoord - posX;
    double dy = target.yCoord - posY;
    double dz = target.zCoord - posZ;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 0.01) {
      return;
    }
    double step = Math.min(speed, length);
    setPosition(posX + dx / length * step, posY + dy / length * step, posZ + dz / length * step);
    rotationYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
  }

  private void setState(State state) {
    dataWatcher.updateObject(STATE, Integer.valueOf(state.ordinal()));
  }
}
