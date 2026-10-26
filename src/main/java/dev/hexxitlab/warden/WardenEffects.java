package dev.hexxitlab.warden;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Client-only particles and afterglows for the Warden. Called only from client code paths, so a
 * server never loads it. Decorative particles follow the player's particle setting; warnings never
 * depend on them.
 */
final class WardenEffects {
  private static final int FLASH_TICKS = 7;
  private static final int SHOCKWAVE_TICKS = 10;
  private static final int SCORCH_TICKS = 120;
  private static final int SLASH_TICKS = 5;
  private static final int BURST_TICKS = 9;
  private static final int EMBER_LIFE = 30;
  private static final int DUST_PUFFS = 24;
  private static final double SCORCH_SHARE = 0.85;
  // Where the shield face sits, in blocks from the Warden's centre.
  private static final double SHIELD_REACH = 1.3;
  private static final double SHIELD_SIDE = 0.35;
  private static final double TORSO_WIDTH = 1.2;
  private static final double TORSO_HEIGHT = 1.4;
  private static final double FOOT_SPREAD = 0.45;
  private static final int IRON_BLOCK = 42;
  private static final int GOLD_BLOCK = 41;
  private static final float WALK_RATE = 0.6662F;

  private WardenEffects() {}

  /** Every client tick: embers by phase, harrowing columns, a smoking stagger, heavy footfalls. */
  static void ambient(Warden boss) {
    World world = boss.worldObj;
    Random rand = boss.getRNG();
    int phase = boss.getPhase();
    Warden.Action action = boss.getShownAction();
    int embers = phase == 3 ? 3 : phase == 2 ? 1 : rand.nextInt(4) == 0 ? 1 : 0;
    for (int i = density(embers); i > 0; i--) {
      double x = boss.posX + (rand.nextDouble() - 0.5) * TORSO_WIDTH;
      double y = boss.posY + Warden.CHEST_HEIGHT + (rand.nextDouble() - 0.5) * TORSO_HEIGHT;
      double z = boss.posZ + (rand.nextDouble() - 0.5) * TORSO_WIDTH;
      add(
          new Ember(
              world,
              x,
              y,
              z,
              0,
              0.02 + rand.nextDouble() * 0.04,
              0,
              size(rand),
              heat(phase),
              life(rand)));
    }
    if (boss.isWarded()) {
      harrowing(boss, action, rand);
    } else if (action == Warden.Action.STAGGER && rand.nextInt(2) == 0) {
      world.spawnParticle(
          "largesmoke",
          boss.posX + (rand.nextDouble() - 0.5),
          boss.posY + Warden.CHEST_HEIGHT * 0.8,
          boss.posZ + (rand.nextDouble() - 0.5),
          0,
          0.04,
          0);
    } else if (action == Warden.Action.AIRBORNE) {
      for (int i = density(4); i > 0; i--) {
        add(
            new Ember(
                world,
                boss.posX + (rand.nextDouble() - 0.5) * TORSO_WIDTH,
                boss.posY + rand.nextDouble() * Warden.CHEST_HEIGHT,
                boss.posZ + (rand.nextDouble() - 0.5) * TORSO_WIDTH,
                0,
                0,
                0,
                size(rand) * 1.4F,
                0.6F,
                life(rand)));
      }
      world.spawnParticle("largesmoke", boss.posX, boss.posY + 1, boss.posZ, 0, 0, 0);
    }
    if (boss.onGround && footfall(boss)) {
      dust(boss, 6);
    }
  }

  /** Embers spiral up around a warded Warden; a gathering nova draws them inward. */
  private static void harrowing(Warden boss, Warden.Action action, Random rand) {
    double turn = boss.ticksExisted * 0.35;
    for (int i = density(4); i > 0; i--) {
      double angle = turn + i * Math.PI / 2 + rand.nextDouble() * 0.3;
      if (action == Warden.Action.NOVA) {
        double reach = 3 + rand.nextDouble() * 2;
        double x = Math.cos(angle) * reach;
        double y = (rand.nextDouble() - 0.3) * 3;
        double z = Math.sin(angle) * reach;
        add(
            new Ember(
                boss.worldObj,
                boss.posX + x,
                boss.posY + Warden.CHEST_HEIGHT + y,
                boss.posZ + z,
                -x * 0.08,
                -y * 0.08,
                -z * 0.08,
                size(rand),
                1,
                12));
      } else {
        add(
            new Ember(
                boss.worldObj,
                boss.posX + Math.cos(angle) * 1.4,
                boss.posY + rand.nextDouble() * 0.5,
                boss.posZ + Math.sin(angle) * 1.4,
                -Math.sin(angle) * 0.06,
                0.12 + rand.nextDouble() * 0.06,
                Math.cos(angle) * 0.06,
                size(rand) * 1.2F,
                0.8F,
                life(rand)));
      }
    }
  }

  /** Marks the moment a strike lands, and the moments that change the fight. */
  static void changed(Warden boss, Warden.Action from, Warden.Action to) {
    World world = boss.worldObj;
    double yaw = boss.getStrikeYaw();
    if (to == Warden.Action.CHAIN && from == Warden.Action.CLEAVE) {
      swing(boss, yaw, Warden.CLEAVE_ARC, Warden.CLEAVE_RADIUS);
    } else if (to == Warden.Action.CHAIN && from == Warden.Action.BACKHAND) {
      swing(boss, yaw + 180, Warden.BACKHAND_ARC, Warden.BACKHAND_RADIUS);
    } else if (to == Warden.Action.RECOVER && from == Warden.Action.BASH) {
      add(
          afterglow(
              boss, Afterglow.Kind.SECTOR, yaw, Warden.BASH_ARC, Warden.BASH_RADIUS, FLASH_TICKS));
      sparks(boss, 10);
    } else if (to == Warden.Action.AIRBORNE) {
      dust(boss, 24);
      add(afterglow(boss, Afterglow.Kind.SHOCKWAVE, 0, 0, 1.5, SHOCKWAVE_TICKS));
    } else if (to == Warden.Action.STAGGER) {
      // Posture breaks: armour cracks blaze and shed sparks.
      sparks(boss, 24);
      Random rand = boss.getRNG();
      for (int i = density(20); i > 0; i--) {
        add(
            new Ember(
                world,
                boss.posX,
                boss.posY + Warden.CHEST_HEIGHT,
                boss.posZ,
                (rand.nextDouble() - 0.5) * 0.5,
                rand.nextDouble() * 0.3,
                (rand.nextDouble() - 0.5) * 0.5,
                size(rand) * 1.3F,
                1,
                life(rand)));
      }
      add(afterglow(boss, Afterglow.Kind.SHOCKWAVE, 0, 0, 2, SHOCKWAVE_TICKS));
    } else if (from == Warden.Action.KINDLE) {
      burst(boss, 40, 0.35, 0.9F);
    }
  }

  private static void swing(Warden boss, double yaw, double arc, double radius) {
    add(afterglow(boss, Afterglow.Kind.SLASH, yaw, arc, radius, SLASH_TICKS));
    add(afterglow(boss, Afterglow.Kind.SECTOR, yaw, arc, radius, FLASH_TICKS));
  }

  /** A Slam or Leap lands: flash, shockwave, flying debris of the ground itself, and a scorch. */
  static void impact(Warden boss, Vec3 point) {
    World world = boss.worldObj;
    double radius =
        boss.getShownAction() == Warden.Action.AIRBORNE ? Warden.LEAP_RADIUS : Warden.SLAM_RADIUS;
    add(
        new Afterglow(
            world,
            point.xCoord,
            point.yCoord,
            point.zCoord,
            Afterglow.Kind.DISC,
            0,
            0,
            radius,
            FLASH_TICKS));
    add(
        new Afterglow(
            world,
            point.xCoord,
            point.yCoord,
            point.zCoord,
            Afterglow.Kind.SHOCKWAVE,
            0,
            0,
            radius,
            SHOCKWAVE_TICKS));
    add(
        new Afterglow(
            world,
            point.xCoord,
            point.yCoord,
            point.zCoord,
            Afterglow.Kind.SCORCH,
            0,
            0,
            radius * SCORCH_SHARE,
            SCORCH_TICKS));
    Random rand = boss.getRNG();
    int bx = MathHelper.floor_double(point.xCoord);
    int by = MathHelper.floor_double(point.yCoord - 0.5);
    int bz = MathHelper.floor_double(point.zCoord);
    int ground = world.getBlockId(bx, by, bz);
    for (int i = density(48); i > 0; i--) {
      double angle = rand.nextDouble() * Math.PI * 2;
      double reach = rand.nextDouble() * radius;
      double x = point.xCoord + Math.cos(angle) * reach;
      double z = point.zCoord + Math.sin(angle) * reach;
      if (ground > 0 && Block.blocksList[ground] != null) {
        debris(
            world,
            x,
            point.yCoord + 0.1,
            z,
            ground,
            world.getBlockMetadata(bx, by, bz),
            angle,
            0.5);
      }
      if (i % 2 == 0) {
        add(
            new Ember(
                world,
                x,
                point.yCoord + 0.2,
                z,
                Math.cos(angle) * 0.15,
                0.15 + rand.nextDouble() * 0.2,
                Math.sin(angle) * 0.15,
                size(rand),
                0.9F,
                life(rand)));
      }
    }
    // A ring of pale dust thrown outward from the blow.
    for (int i = 0; i < DUST_PUFFS; i++) {
      double angle = i * Math.PI * 2 / DUST_PUFFS;
      world.spawnParticle(
          "explode",
          point.xCoord + Math.cos(angle) * radius * 0.6,
          point.yCoord + 0.2,
          point.zCoord + Math.sin(angle) * radius * 0.6,
          Math.cos(angle) * 0.2,
          0.05,
          Math.sin(angle) * 0.2);
    }
  }

  /** The Ash Nova bursts and the shield shatters into flying shards. */
  static void nova(Warden boss) {
    World world = boss.worldObj;
    double radius = boss.getNovaRadius();
    add(
        new Afterglow(
            world,
            boss.posX,
            boss.posY + Warden.CHEST_HEIGHT,
            boss.posZ,
            Afterglow.Kind.BURST,
            0,
            0,
            radius,
            BURST_TICKS));
    add(afterglow(boss, Afterglow.Kind.SHOCKWAVE, 0, 0, radius * 0.7, SHOCKWAVE_TICKS * 2));
    burst(boss, 140, 1.1, 1);
    Vec3 shield = shieldFace(boss);
    Random rand = boss.getRNG();
    for (int i = density(40); i > 0; i--) {
      double angle = rand.nextDouble() * Math.PI * 2;
      debris(
          world,
          shield.xCoord,
          shield.yCoord + (rand.nextDouble() - 0.5),
          shield.zCoord,
          i % 3 == 0 ? GOLD_BLOCK : IRON_BLOCK,
          0,
          angle,
          0.6);
    }
  }

  /**
   * A living ember: it sheds sparks, flares while marking, streaks while diving, and smokes home.
   */
  static void cinder(Cinder cinder) {
    World world = cinder.worldObj;
    Random rand = cinder.getRNG();
    Cinder.State state = cinder.getState();
    double y = cinder.posY + cinder.height / 2;
    int count = state == Cinder.State.DIVE ? 5 : state == Cinder.State.MARK ? 3 : 1;
    for (int i = density(count); i > 0; i--) {
      double spread = state == Cinder.State.MARK ? 0.6 : 0.25;
      add(
          new Ember(
              world,
              cinder.posX + (rand.nextDouble() - 0.5) * spread,
              y + (rand.nextDouble() - 0.5) * spread,
              cinder.posZ + (rand.nextDouble() - 0.5) * spread,
              (rand.nextDouble() - 0.5) * 0.04,
              0.02,
              (rand.nextDouble() - 0.5) * 0.04,
              size(rand) * (state == Cinder.State.MARK ? 1.3F : 1),
              state == Cinder.State.RECALL ? 0.3F : 1,
              EMBER_LIFE / 2));
    }
    if (state == Cinder.State.RECALL || state == Cinder.State.DIVE && rand.nextInt(2) == 0) {
      world.spawnParticle("smoke", cinder.posX, y, cinder.posZ, 0, 0.02, 0);
    }
  }

  /** A hit glances off the shield or the ward: sparks fly from where it struck. */
  static void deflect(Warden boss) {
    sparks(boss, 8);
  }

  private static void sparks(Warden boss, int count) {
    World world = boss.worldObj;
    Random rand = boss.getRNG();
    Vec3 origin =
        boss.getPhase() < 3 && !boss.isWarded()
            ? shieldFace(boss)
            : Vec3.createVectorHelper(boss.posX, boss.posY + Warden.CHEST_HEIGHT, boss.posZ);
    for (int i = density(count); i > 0; i--) {
      double vx = (rand.nextDouble() - 0.5) * 0.6;
      double vy = rand.nextDouble() * 0.4;
      double vz = (rand.nextDouble() - 0.5) * 0.6;
      world.spawnParticle("crit", origin.xCoord, origin.yCoord, origin.zCoord, vx, vy, vz);
      add(
          new Ember(
              world,
              origin.xCoord,
              origin.yCoord,
              origin.zCoord,
              vx * 0.5,
              vy * 0.5,
              vz * 0.5,
              size(rand) * 0.8F,
              1,
              EMBER_LIFE / 2));
    }
  }

  /** Embers flung outwards in every direction from the chest. */
  private static void burst(Warden boss, int count, double speed, float heat) {
    Random rand = boss.getRNG();
    for (int i = density(count); i > 0; i--) {
      double yaw = rand.nextDouble() * Math.PI * 2;
      double pitch = (rand.nextDouble() - 0.3) * Math.PI * 0.6;
      double v = speed * (0.5 + rand.nextDouble() * 0.5);
      add(
          new Ember(
              boss.worldObj,
              boss.posX,
              boss.posY + Warden.CHEST_HEIGHT,
              boss.posZ,
              Math.cos(yaw) * Math.cos(pitch) * v,
              Math.sin(pitch) * v,
              Math.sin(yaw) * Math.cos(pitch) * v,
              size(rand) * 1.5F,
              heat,
              life(rand)));
    }
  }

  /** Dust and grit of the ground under the Warden's feet. */
  private static void dust(Warden boss, int count) {
    World world = boss.worldObj;
    int bx = MathHelper.floor_double(boss.posX);
    int by = MathHelper.floor_double(boss.posY - 0.2);
    int bz = MathHelper.floor_double(boss.posZ);
    int ground = world.getBlockId(bx, by, bz);
    if (ground <= 0 || Block.blocksList[ground] == null) {
      return;
    }
    Random rand = boss.getRNG();
    for (int i = density(count); i > 0; i--) {
      double angle = rand.nextDouble() * Math.PI * 2;
      debris(
          world,
          boss.posX + Math.cos(angle) * FOOT_SPREAD,
          boss.posY + 0.1,
          boss.posZ + Math.sin(angle) * FOOT_SPREAD,
          ground,
          world.getBlockMetadata(bx, by, bz),
          angle,
          0.2);
    }
  }

  private static void debris(
      World world, double x, double y, double z, int block, int meta, double angle, double speed) {
    EntityDiggingFX piece =
        new EntityDiggingFX(
            world,
            x,
            y,
            z,
            Math.cos(angle) * speed,
            0.2 + speed * 0.6,
            Math.sin(angle) * speed,
            Block.blocksList[block],
            0,
            meta,
            Minecraft.getMinecraft().renderEngine);
    add(
        piece.func_70596_a(
            MathHelper.floor_double(x),
            MathHelper.floor_double(y - 0.5),
            MathHelper.floor_double(z)));
  }

  /** Whether a foot came down this tick. */
  private static boolean footfall(Warden boss) {
    float before = boss.limbSwing - boss.limbYaw;
    return boss.limbYaw > 0.1F
        && Math.floor(before * WALK_RATE / Math.PI)
            != Math.floor(boss.limbSwing * WALK_RATE / Math.PI);
  }

  private static Vec3 shieldFace(Warden boss) {
    double yaw = Math.toRadians(boss.rotationYaw);
    double fx = -Math.sin(yaw);
    double fz = Math.cos(yaw);
    return Vec3.createVectorHelper(
        boss.posX + fx * SHIELD_REACH + fz * SHIELD_SIDE,
        boss.posY + Warden.CHEST_HEIGHT,
        boss.posZ + fz * SHIELD_REACH - fx * SHIELD_SIDE);
  }

  private static Afterglow afterglow(
      Warden boss, Afterglow.Kind kind, double yaw, double arc, double radius, int life) {
    return new Afterglow(
        boss.worldObj, boss.posX, boss.posY, boss.posZ, kind, yaw, arc, radius, life);
  }

  private static float heat(int phase) {
    return phase == 3 ? 0.55F : 0.8F;
  }

  private static float size(Random rand) {
    return 0.35F + rand.nextFloat() * 0.35F;
  }

  private static int life(Random rand) {
    return EMBER_LIFE / 2 + rand.nextInt(EMBER_LIFE);
  }

  /** Scales a particle count by the player's particle setting. */
  private static int density(int count) {
    int setting = Minecraft.getMinecraft().gameSettings.particleSetting;
    return setting == 0 ? count : setting == 1 ? (count + 1) / 2 : count / 4;
  }

  private static void add(EntityFX effect) {
    Minecraft.getMinecraft().effectRenderer.addEffect(effect);
  }
}
