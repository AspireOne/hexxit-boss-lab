package dev.hexxitlab.sovereign;

import dev.hexxitlab.BossLab;
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
public final class Sovereign extends EntityFlying implements IBossDisplayData {
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
  private static final double PLAYER_HIT_RADIUS = 0.45;
  private static final int IMPACT = 26;
  private static final int HEALTH = 20;
  private static final int ACTION = 21;
  private static final int TIMER = 22;
  private static final int AIM_X = 23;
  private static final int AIM_Y = 24;
  private static final int AIM_Z = 25;

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

  public Sovereign(World world) {
    super(world);
    setSize(2.4F, 3.8F);
    setEntityHealth(MAX_HEALTH);
    isImmuneToFire = true;
    noClip = true;
    experienceValue = 150;
    renderDistanceWeight = 8;
    texture = "/mob/enderman.png";
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
    return Vec3.createVectorHelper(readAim(AIM_X), readAim(AIM_Y), readAim(AIM_Z));
  }

  private double readAim(int index) {
    return Float.intBitsToFloat(dataWatcher.getWatchableObjectInt(index));
  }

  private void writeAim(int index, double value) {
    dataWatcher.updateObject(index, Integer.valueOf(Float.floatToIntBits((float) value)));
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
  public void onLivingUpdate() {
    super.onLivingUpdate();
    motionX = motionY = motionZ = 0;
    fallDistance = 0;
    if (worldObj.isRemote) {
      setEntityHealth(dataWatcher.getWatchableObjectInt(HEALTH));
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
    return player != null
        && player.isEntityAlive()
        && !player.capabilities.disableDamage
        && player.worldObj == worldObj
        && getDistanceSqToEntity(player) <= ENGAGE_RANGE * ENGAGE_RANGE;
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
              && center().distanceTo(playerCenter(player)) <= BLAST_RADIUS
              && unobstructed(center(), playerCenter(player))) {
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
      MovingObjectPosition block = trace(start, end);
      if (block != null) {
        end = block.hitVec;
      }
    }
    writeAim(AIM_X, end.xCoord - start.xCoord);
    writeAim(AIM_Y, end.yCoord - start.yCoord);
    writeAim(AIM_Z, end.zCoord - start.zCoord);
    dataWatcher.updateObject(IMPACT, Integer.valueOf(Action.HUNT.ordinal()));
    int windup = attack == Action.BLAST ? 36 : attack == Action.RUSH ? 26 : 24;
    changeAction(attack, windup - (getPhase() - 1) * 2);
    worldObj.playSoundAtEntity(this, "mob.wither.idle", 2, attack == Action.BLAST ? 0.5F : 1.5F);
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

  private static Vec3 playerCenter(EntityPlayer player) {
    return Vec3.createVectorHelper(player.posX, player.posY + 0.9, player.posZ);
  }

  private boolean unobstructed(Vec3 start, Vec3 end) {
    return trace(start, end) == null;
  }

  private MovingObjectPosition trace(Vec3 start, Vec3 end) {
    // Minecraft 1.5.2's DDA mutates its input vector. Never hand it locked attack geometry.
    return worldObj.rayTraceBlocks(
        Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
        Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
  }

  private void hitLine(Vec3 start, Vec3 end, double radius, int damage, boolean magic) {
    if (magic) {
      MovingObjectPosition block = trace(start, end);
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
      if (inCorridor(start, end, playerCenter(player), radius + PLAYER_HIT_RADIUS)
          && unobstructed(start, playerCenter(player))) {
        hurt(player, damage, magic);
        if (!magic) {
          rushHits.add(Integer.valueOf(player.entityId));
        }
      }
    }
  }

  /** Finite cylindrical strike with rounded ends; shared by lance and swept rush. */
  private static boolean inCorridor(Vec3 start, Vec3 end, Vec3 point, double radius) {
    double dx = end.xCoord - start.xCoord;
    double dy = end.yCoord - start.yCoord;
    double dz = end.zCoord - start.zCoord;
    double lengthSquared = dx * dx + dy * dy + dz * dz;
    double t =
        lengthSquared == 0
            ? 0
            : Math.max(
                0,
                Math.min(
                    1,
                    ((point.xCoord - start.xCoord) * dx
                            + (point.yCoord - start.yCoord) * dy
                            + (point.zCoord - start.zCoord) * dz)
                        / lengthSquared));
    double x = start.xCoord + t * dx - point.xCoord;
    double y = start.yCoord + t * dy - point.yCoord;
    double z = start.zCoord + t * dz - point.zCoord;
    return x * x + y * y + z * z <= radius * radius;
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
