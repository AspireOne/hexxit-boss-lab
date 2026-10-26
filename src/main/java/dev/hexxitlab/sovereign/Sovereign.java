package dev.hexxitlab.sovereign;

import dev.hexxitlab.BossCombat;
import dev.hexxitlab.BossLab;
import dev.hexxitlab.LabBoss;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Server-owned combat. Each attack locks its direction before its visible windup begins. */
public final class Sovereign extends EntityFlying implements IBossDisplayData, LabBoss {
  public static final int MAX_HEALTH = 3600;
  public static final double BLAST_RADIUS = 18;
  public static final double LANCE_RADIUS = 1.2;
  public static final double RUSH_RADIUS = 1.8;
  public static final double CENTER_HEIGHT = 1.8;
  private static final double LANCE_RANGE = 64;
  private static final double RUSH_RANGE = 40;
  private static final double ENGAGE_RANGE = 128;
  private static final int RESET_TICKS = 300;
  private static final int LANCE_DAMAGE = 48;
  private static final int RUSH_DAMAGE = 100;
  private static final int BLAST_DAMAGE = 64;
  private static final double CLOSE_DISTANCE = 4.5;
  private static final double FLANK_SPEED = 0.55;
  private static final double LATERAL_TRIGGER_SPEED = 0.18;
  private static final double BLAST_TRIGGER_RANGE = 20;
  private static final double RUSH_SPEED = 3.2;
  private static final int IMPACT = 26;
  private static final int HEALTH = 20;
  private static final int ACTION = 21;
  private static final int TIMER = 22;
  private static final int AIM_X = 23;
  private static final int AIM_Y = 24;
  private static final int AIM_Z = 25;
  // Vanilla's hurt status; clients answer it with sparks.
  private static final byte HURT_EFFECT = 2;
  private static final double DRIFT_SMOOTHING = 0.35;

  public enum Action {
    HUNT,
    LANCE,
    RUSH,
    BLAST,
    CHARGE,
    RECOVER
  }

  private EntityPlayer opponent;
  private int remaining = 60;
  private int attackNumber;
  private int absentTicks;
  private double rushDistance;
  private double lastTargetX;
  private double lastTargetY;
  private double lastTargetZ;
  private boolean sampledTarget;
  private boolean previousBlast;
  private final Set<Integer> rushHits = new HashSet<Integer>();
  // Client presentation only: the action being shown, since when, what it followed, the phase
  // last shown, a smoothed flight velocity, and where a charge began.
  private Action shownAction = Action.HUNT;
  private Action previousAction = Action.HUNT;
  private int actionAge;
  private int shownPhase;
  private double driftX;
  private double driftY;
  private double driftZ;
  private Vec3 chargeStart;

  public Sovereign(World world) {
    super(world);
    setSize(2.4F, 3.8F);
    setEntityHealth(MAX_HEALTH);
    isImmuneToFire = true;
    noClip = true;
    experienceValue = 150;
    renderDistanceWeight = 8;
    // Warnings extend far beyond the body; keep drawing them when the body is off-screen.
    ignoreFrustumCheck = true;
    texture = "/mods/hexxitbosslab/textures/entity/sovereign.png";
  }

  @Override
  protected void entityInit() {
    super.entityInit();
    dataWatcher.addObject(HEALTH, Integer.valueOf(MAX_HEALTH));
    dataWatcher.addObject(ACTION, Integer.valueOf(Action.HUNT.ordinal()));
    dataWatcher.addObject(TIMER, Integer.valueOf(60));
    dataWatcher.addObject(AIM_X, Integer.valueOf(0));
    dataWatcher.addObject(AIM_Y, Integer.valueOf(0));
    dataWatcher.addObject(AIM_Z, Integer.valueOf(0));
    dataWatcher.addObject(IMPACT, Integer.valueOf(Action.HUNT.ordinal()));
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
    return "The Hexbound Sovereign";
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
  protected void updateEntityActionState() {
    /* Combat owns movement. */
  }

  @Override
  public void moveEntityWithHeading(float strafe, float forward) {
    /* No gravity or drift. */
  }

  @Override
  public void knockBack(Entity attacker, int damage, double x, double z) {
    /* Heavy construct. */
  }

  public Action getAction() {
    return Action.values()[dataWatcher.getWatchableObjectInt(ACTION)];
  }

  public int getActionTicks() {
    return dataWatcher.getWatchableObjectInt(TIMER);
  }

  public Action getImpact() {
    return Action.values()[dataWatcher.getWatchableObjectInt(IMPACT)];
  }

  public Vec3 getAim() {
    return Vec3.createVectorHelper(
        BossCombat.readFloat(dataWatcher, AIM_X),
        BossCombat.readFloat(dataWatcher, AIM_Y),
        BossCombat.readFloat(dataWatcher, AIM_Z));
  }

  @Override
  public String describe() {
    return "Sovereign: "
        + getHealth()
        + "/"
        + MAX_HEALTH
        + " health, phase "
        + getPhase()
        + ", "
        + getAction();
  }

  public int getPhase() {
    return getHealth() > MAX_HEALTH * 2 / 3 ? 1 : getHealth() > MAX_HEALTH / 3 ? 2 : 3;
  }

  @Override
  public boolean attackEntityFrom(DamageSource source, int amount) {
    if (source == DamageSource.inWall || source == DamageSource.drown) {
      return false;
    }
    return super.attackEntityFrom(source, amount);
  }

  @Override
  public void onUpdate() {
    super.onUpdate();
    if (worldObj.isRemote) {
      // The body faces where the Sovereign aims, never where vanilla's flight turn would put it.
      renderYawOffset = rotationYaw;
      rotationYawHead = rotationYaw;
    }
  }

  @Override
  public void onLivingUpdate() {
    super.onLivingUpdate();
    motionX = motionY = motionZ = 0;
    fallDistance = 0;
    if (worldObj.isRemote) {
      setEntityHealth(dataWatcher.getWatchableObjectInt(HEALTH));
      present();
      return;
    }
    if (getHealth() <= 0) {
      return;
    }
    if (worldObj.difficultySetting == 0) {
      setDead();
      return;
    }
    if (!validOpponent(opponent)) {
      opponent = worldObj.getClosestVulnerablePlayerToEntity(this, ENGAGE_RANGE);
      sampledTarget = false;
    }
    if (!validOpponent(opponent)) {
      changeAction(Action.HUNT, 60);
      if (++absentTicks == RESET_TICKS) {
        setEntityHealth(MAX_HEALTH);
        BossLab.LOGGER.info("Sovereign recovered after disengagement: " + getPersistentID());
      }
    } else {
      absentTicks = 0;
      faceEntity(opponent, 30, 30);
      tickCombat();
      lastTargetX = opponent.posX;
      lastTargetY = opponent.posY;
      lastTargetZ = opponent.posZ;
      sampledTarget = true;
    }
    dataWatcher.updateObject(HEALTH, Integer.valueOf(getHealth()));
    dataWatcher.updateObject(TIMER, Integer.valueOf(remaining));
  }

  private boolean validOpponent(EntityPlayer player) {
    return BossCombat.isOpponent(this, player, ENGAGE_RANGE);
  }

  private void tickCombat() {
    Action action = getAction();
    if (action == Action.HUNT) {
      pursue();
      if (--remaining <= 0 && canEntityBeSeen(opponent)) {
        prepareAttack();
      }
    } else if (action == Action.RECOVER) {
      if (--remaining <= 0) {
        changeAction(Action.HUNT, 20 - getPhase() * 4);
      }
    } else if (action == Action.CHARGE) {
      Vec3 aim = getAim();
      Vec3 start = center();
      double speed = Math.min(RUSH_SPEED, rushDistance);
      double length = aim.lengthVector();
      if (length > 0) {
        setPosition(
            posX + aim.xCoord / length * speed,
            posY + aim.yCoord / length * speed,
            posZ + aim.zCoord / length * speed);
        hitLine(start, center(), RUSH_RADIUS, RUSH_DAMAGE, false);
        rushDistance -= speed;
      }
      if (--remaining <= 0) {
        changeAction(Action.RECOVER, 24);
      }
    } else if (--remaining <= 0) {
      if (action == Action.LANCE) {
        Vec3 start = center();
        Vec3 aim = getAim();
        hitLine(
            start,
            start.addVector(aim.xCoord, aim.yCoord, aim.zCoord),
            LANCE_RADIUS,
            LANCE_DAMAGE,
            true);
        dataWatcher.updateObject(IMPACT, Integer.valueOf(Action.LANCE.ordinal()));
        worldObj.playSoundAtEntity(this, "mob.wither.shoot", 2, 0.6F);
        changeAction(Action.RECOVER, 20);
      } else if (action == Action.RUSH) {
        rushHits.clear();
        rushDistance = getAim().lengthVector();
        changeAction(Action.CHARGE, Math.max(1, (int) Math.ceil(rushDistance / RUSH_SPEED)));
        worldObj.playSoundAtEntity(this, "mob.enderdragon.wings", 3, 0.7F);
      } else {
        for (Object value : worldObj.playerEntities) {
          EntityPlayer player = (EntityPlayer) value;
          if (validOpponent(player)
              && center().distanceTo(BossCombat.center(player)) <= BLAST_RADIUS
              && BossCombat.unobstructed(worldObj, center(), BossCombat.center(player))) {
            hurt(player, BLAST_DAMAGE, true);
          }
        }
        worldObj.playSoundAtEntity(this, "random.explode", 3, 0.6F);
        dataWatcher.updateObject(IMPACT, Integer.valueOf(Action.BLAST.ordinal()));
        changeAction(Action.RECOVER, 28);
      }
    }
  }

  private void pursue() {
    double dx = opponent.posX - posX;
    double dy = opponent.posY + 0.5 - posY;
    double dz = opponent.posZ - posZ;
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance > CLOSE_DISTANCE) {
      double speed = distance > 28 ? 2.8 : 0.7 + getPhase() * 0.2;
      double step = Math.min(speed, distance - CLOSE_DISTANCE);
      setPosition(
          posX + dx / distance * step, posY + dy / distance * step, posZ + dz / distance * step);
    } else {
      // Flank within melee range rather than becoming a stationary archery target.
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      double sideX = horizontal < 0.01 ? 1 : -dz / horizontal;
      double sideZ = horizontal < 0.01 ? 0 : dx / horizontal;
      setPosition(posX + sideX * FLANK_SPEED, posY, posZ + sideZ * FLANK_SPEED);
    }
  }

  private void prepareAttack() {
    // A broad attack pressures fast lateral movement; never chain two blasts back-to-back.
    boolean close = getDistanceSqToEntity(opponent) < BLAST_TRIGGER_RANGE * BLAST_TRIGGER_RANGE;
    Action attack =
        !previousBlast && close && (attackNumber % 3 == 2 || movingLaterally())
            ? Action.BLAST
            : attackNumber % 2 == 0 ? Action.LANCE : Action.RUSH;
    previousBlast = attack == Action.BLAST;
    attackNumber = (attackNumber + 1) % 6;
    Vec3 start = center();
    // Sample positions ourselves: vanilla prevPos can already equal pos during an entity tick.
    double dx =
        opponent.posX
            + (sampledTarget ? clamp((opponent.posX - lastTargetX) * 6, 8) : 0)
            - start.xCoord;
    double dy =
        opponent.posY
            + 0.9
            + (sampledTarget ? clamp((opponent.posY - lastTargetY) * 6, 8) : 0)
            - start.yCoord;
    double dz =
        opponent.posZ
            + (sampledTarget ? clamp((opponent.posZ - lastTargetZ) * 6, 8) : 0)
            - start.zCoord;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (length < 0.01) {
      dx = 1;
      length = 1;
    }
    double range = attack == Action.LANCE ? LANCE_RANGE : Math.min(RUSH_RANGE, length + 6);
    Vec3 end = start.addVector(dx / length * range, dy / length * range, dz / length * range);
    // Lance stops at cover; rush is an ethereal movement but cannot hit through cover.
    if (attack == Action.LANCE) {
      MovingObjectPosition block = BossCombat.trace(worldObj, start, end);
      if (block != null) {
        end = block.hitVec;
      }
    }
    BossCombat.writeFloat(dataWatcher, AIM_X, end.xCoord - start.xCoord);
    BossCombat.writeFloat(dataWatcher, AIM_Y, end.yCoord - start.yCoord);
    BossCombat.writeFloat(dataWatcher, AIM_Z, end.zCoord - start.zCoord);
    dataWatcher.updateObject(IMPACT, Integer.valueOf(Action.HUNT.ordinal()));
    changeAction(attack, windup(attack, getPhase()));
    worldObj.playSoundAtEntity(this, "mob.wither.idle", 2, attack == Action.BLAST ? 0.5F : 1.5F);
  }

  /** Warning ticks before an attack fires: every attack's warning is locked from the start. */
  public static int windup(Action attack, int phase) {
    int windup = attack == Action.BLAST ? 36 : attack == Action.RUSH ? 26 : 24;
    return windup - (phase - 1) * 2;
  }

  private boolean movingLaterally() {
    if (!sampledTarget) {
      return false;
    }
    double dx = opponent.posX - posX;
    double dy = opponent.posY + 0.9 - (posY + CENTER_HEIGHT);
    double dz = opponent.posZ - posZ;
    double vx = opponent.posX - lastTargetX;
    double vy = opponent.posY - lastTargetY;
    double vz = opponent.posZ - lastTargetZ;
    double distanceSquared = dx * dx + dy * dy + dz * dz;
    double radial =
        distanceSquared < 0.01 ? 0 : (vx * dx + vy * dy + vz * dz) / Math.sqrt(distanceSquared);
    double lateralSquared = vx * vx + vy * vy + vz * vz - radial * radial;
    return lateralSquared >= LATERAL_TRIGGER_SPEED * LATERAL_TRIGGER_SPEED;
  }

  private static double clamp(double value, double limit) {
    return Math.max(-limit, Math.min(limit, value));
  }

  private Vec3 center() {
    return Vec3.createVectorHelper(posX, posY + CENTER_HEIGHT, posZ);
  }

  private void hitLine(Vec3 start, Vec3 end, double radius, int damage, boolean magic) {
    if (magic) {
      MovingObjectPosition block = BossCombat.trace(worldObj, start, end);
      if (block != null) {
        end = block.hitVec;
      }
    }
    for (Object value : worldObj.playerEntities) {
      EntityPlayer player = (EntityPlayer) value;
      if (!validOpponent(player)
          || (!magic && rushHits.contains(Integer.valueOf(player.entityId)))) {
        continue;
      }
      if (BossCombat.inCorridor(
              start, end, BossCombat.center(player), radius + BossCombat.PLAYER_HIT_RADIUS)
          && BossCombat.unobstructed(worldObj, start, BossCombat.center(player))) {
        hurt(player, damage, magic);
        if (!magic) {
          rushHits.add(Integer.valueOf(player.entityId));
        }
      }
    }
  }

  private void hurt(EntityPlayer player, int damage, boolean magic) {
    DamageSource source = magic ? new HexDamage(this) : DamageSource.causeMobDamage(this);
    player.attackEntityFrom(source, damage);
  }

  private void changeAction(Action action, int ticks) {
    dataWatcher.updateObject(ACTION, Integer.valueOf(action.ordinal()));
    remaining = ticks;
    dataWatcher.updateObject(TIMER, Integer.valueOf(ticks));
  }

  /** Client presentation: follows the shown action and phase, smooths flight, plays effects. */
  private void present() {
    Action action = getAction();
    if (action == shownAction) {
      actionAge++;
    } else {
      previousAction = shownAction;
      shownAction = action;
      actionAge = 0;
      if (action == Action.CHARGE) {
        chargeStart = Vec3.createVectorHelper(posX, posY, posZ);
      }
      SovereignEffects.changed(this, previousAction, action);
    }
    int phase = getPhase();
    if (shownPhase != 0 && phase != shownPhase) {
      SovereignEffects.phase(this);
    }
    shownPhase = phase;
    // Leaning follows its flight, not the uneven steps of position updates.
    driftX += (posX - prevPosX - driftX) * DRIFT_SMOOTHING;
    driftY += (posY - prevPosY - driftY) * DRIFT_SMOOTHING;
    driftZ += (posZ - prevPosZ - driftZ) * DRIFT_SMOOTHING;
    SovereignEffects.ambient(this);
  }

  @Override
  public void handleHealthUpdate(byte status) {
    super.handleHealthUpdate(status);
    if (status == HURT_EFFECT) {
      SovereignEffects.hurt(this);
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

  /** Smoothed client flight velocity, in blocks per tick. */
  Vec3 getDrift() {
    return Vec3.createVectorHelper(driftX, driftY, driftZ);
  }

  /** Where the shown charge began, or null before any charge. */
  Vec3 getChargeStart() {
    return chargeStart;
  }

  @Override
  public void writeEntityToNBT(NBTTagCompound nbt) {
    super.writeEntityToNBT(nbt);
    nbt.setInteger("SovereignAttack", attackNumber);
    nbt.setBoolean("SovereignPreviousBlast", previousBlast);
  }

  @Override
  public void readEntityFromNBT(NBTTagCompound nbt) {
    super.readEntityFromNBT(nbt);
    attackNumber = Math.max(0, nbt.getInteger("SovereignAttack")) % 6;
    previousBlast = nbt.getBoolean("SovereignPreviousBlast");
    // Reload cancels transient hazards and gives a fresh warning before any attack.
    changeAction(Action.HUNT, 60);
    dataWatcher.updateObject(HEALTH, Integer.valueOf(getHealth()));
  }

  @Override
  public void onDeath(DamageSource source) {
    super.onDeath(source);
    if (!worldObj.isRemote) {
      BossLab.LOGGER.info("Sovereign defeated: " + getPersistentID());
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

  private static final class HexDamage extends EntityDamageSource {
    HexDamage(Entity boss) {
      super("sovereign", boss);
      setDamageBypassesArmor();
      setMagicDamage();
    }

    @Override
    public String getDeathMessage(net.minecraft.entity.EntityLiving victim) {
      return victim.getEntityName() + " was unmade by the Hexbound Sovereign";
    }
  }
}
