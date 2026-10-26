package dev.hexxitlab.sovereign;

import dev.hexxitlab.BossCombat;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Client-only particles and afterglows for the Sovereign. Called only from client code paths, so a
 * server never loads it. Decorative particles follow the player's particle setting; warnings never
 * depend on them.
 */
final class SovereignEffects {
  private static final int WISP_LIFE = 24;
  private static final int RING_TICKS = 8;
  private static final int BURST_TICKS = 9;
  private static final int SHOCKWAVE_TICKS = 16;
  private static final int SCORCH_TICKS = 120;
  private static final int DEATH_TICKS = 20;
  // Where the poses put the sceptre's orb when the lance fires, and the crown at Crown Break's
  // peak, in blocks from the centre.
  private static final double ORB_REACH = 2.5;
  private static final double CROWN_RISE = 3.8;
  private static final double BODY_WIDTH = 1.2;
  private static final double SCORCH_RADIUS = 4;
  private static final float[] SMOULDER = {0.3F, 0.12F, 0.45F};
  private static final float[] SOOT = {0.08F, 0.03F, 0.1F};
  private static final float[] WHITE = {1, 0.9F, 1};

  private SovereignEffects() {}

  /** Every client tick: hex motes off its robe, and the gathering of whatever it winds up. */
  static void ambient(Sovereign boss) {
    World world = boss.worldObj;
    Random rand = boss.getRNG();
    int phase = boss.getPhase();
    Sovereign.Action action = boss.getShownAction();
    float[] tint = tint(boss);
    Vec3 centre = centre(boss);
    if (boss.deathTime > 0) {
      dying(boss, centre, rand);
      return;
    }
    int motes = phase == 3 ? 2 : phase == 2 ? 1 : rand.nextInt(3) == 0 ? 1 : 0;
    for (int i = density(motes); i > 0; i--) {
      add(
          new Wisp(
              world,
              boss.posX + (rand.nextDouble() - 0.5) * BODY_WIDTH,
              boss.posY + rand.nextDouble() * 0.8,
              boss.posZ + (rand.nextDouble() - 0.5) * BODY_WIDTH,
              0,
              0.03 + rand.nextDouble() * 0.03,
              0,
              size(rand),
              tint,
              life(rand)));
    }
    Vec3 aim = direction(boss);
    if (action == Sovereign.Action.LANCE) {
      // Light gathers into the sceptre's orb on the line of fire.
      gather(boss, offset(centre, aim, ORB_REACH), 2.5, 3, tint, rand);
    } else if (action == Sovereign.Action.RUSH) {
      gather(boss, offset(centre, aim, 1.8), 3, 3, tint, rand);
    } else if (action == Sovereign.Action.BLAST) {
      gather(
          boss,
          Vec3.createVectorHelper(centre.xCoord, centre.yCoord + CROWN_RISE, centre.zCoord),
          5,
          4,
          tint,
          rand);
    } else if (action == Sovereign.Action.CHARGE) {
      for (int i = density(6); i > 0; i--) {
        add(
            new Wisp(
                world,
                centre.xCoord + (rand.nextDouble() - 0.5) * 2,
                centre.yCoord + (rand.nextDouble() - 0.5) * 2,
                centre.zCoord + (rand.nextDouble() - 0.5) * 2,
                -aim.xCoord * 0.1,
                -aim.yCoord * 0.1,
                -aim.zCoord * 0.1,
                size(rand) * 1.5F,
                tint,
                life(rand)));
      }
    } else if (action == Sovereign.Action.RECOVER && rand.nextInt(2) == 0) {
      // Drained: dim motes spill from the open core and sink.
      add(
          new Wisp(
              world,
              centre.xCoord + (rand.nextDouble() - 0.5) * 0.6,
              centre.yCoord,
              centre.zCoord + (rand.nextDouble() - 0.5) * 0.6,
              (rand.nextDouble() - 0.5) * 0.04,
              -0.03,
              (rand.nextDouble() - 0.5) * 0.04,
              size(rand),
              SMOULDER,
              life(rand)));
    }
  }

  /** Motes drawn in from around a point, and swallowed by it. */
  private static void gather(
      Sovereign boss, Vec3 point, double reach, int count, float[] tint, Random rand) {
    for (int i = density(count); i > 0; i--) {
      double yaw = rand.nextDouble() * Math.PI * 2;
      double pitch = (rand.nextDouble() - 0.5) * Math.PI;
      add(
          new Wisp(
                  boss.worldObj,
                  point.xCoord + Math.cos(yaw) * Math.cos(pitch) * reach,
                  point.yCoord + Math.sin(pitch) * reach,
                  point.zCoord + Math.sin(yaw) * Math.cos(pitch) * reach,
                  -Math.sin(yaw) * 0.08,
                  0,
                  Math.cos(yaw) * 0.08,
                  size(rand),
                  tint,
                  WISP_LIFE)
              .toward(point.xCoord, point.yCoord, point.zCoord, 0.03));
    }
  }

  /** Marks each attack as it begins and as it lands. */
  static void changed(Sovereign boss, Sovereign.Action from, Sovereign.Action to) {
    World world = boss.worldObj;
    Vec3 centre = centre(boss);
    Vec3 aim = direction(boss);
    Random rand = boss.getRNG();
    if (to == Sovereign.Action.LANCE
        || to == Sovereign.Action.RUSH
        || to == Sovereign.Action.BLAST) {
      // A pulse of its colour: an attack has begun.
      add(
          new HexFlash(
              world,
              centre.xCoord,
              centre.yCoord,
              centre.zCoord,
              HexFlash.Kind.RING,
              SovereignRenderer.color(to),
              2.5,
              RING_TICKS));
    } else if (from == Sovereign.Action.LANCE && to == Sovereign.Action.RECOVER) {
      Vec3 muzzle = offset(centre, aim, ORB_REACH);
      ring(world, muzzle, aim, SovereignRenderer.LANCE, 2);
      spray(world, muzzle, aim, 0.6, 30, SovereignRenderer.LANCE, rand);
      Vec3 end = centre.addVector(boss.getAim().xCoord, boss.getAim().yCoord, boss.getAim().zCoord);
      burst(world, end, 24, 0.25, SovereignRenderer.LANCE, rand);
      debris(world, offset(end, aim, 0.1), aim, 16, rand);
    } else if (from == Sovereign.Action.RUSH && to == Sovereign.Action.CHARGE) {
      ring(world, centre, aim, SovereignRenderer.RUSH, 3);
      spray(world, centre, scale(aim, -1), 0.4, 30, SovereignRenderer.RUSH, rand);
    } else if (from == Sovereign.Action.CHARGE && to == Sovereign.Action.RECOVER) {
      ring(world, centre, aim, SovereignRenderer.RUSH, 2.5);
      spray(world, centre, aim, 0.5, 30, SovereignRenderer.RUSH, rand);
    } else if (from == Sovereign.Action.BLAST && to == Sovereign.Action.RECOVER) {
      crownBreak(boss, centre, rand);
    }
  }

  /** The crown bursts: a white-hot sphere, a shockwave and scorch below, and flying gold. */
  private static void crownBreak(Sovereign boss, Vec3 centre, Random rand) {
    World world = boss.worldObj;
    add(
        new HexFlash(
            world,
            centre.xCoord,
            centre.yCoord,
            centre.zCoord,
            HexFlash.Kind.BURST,
            SovereignRenderer.BLAST,
            Sovereign.BLAST_RADIUS,
            BURST_TICKS));
    double ground = BossCombat.groundBelow(world, boss.posX, centre.yCoord, boss.posZ);
    add(
        new HexFlash(
            world,
            boss.posX,
            ground,
            boss.posZ,
            HexFlash.Kind.SHOCKWAVE,
            SovereignRenderer.BLAST,
            Sovereign.BLAST_RADIUS,
            SHOCKWAVE_TICKS));
    add(
        new HexFlash(
            world,
            boss.posX,
            ground,
            boss.posZ,
            HexFlash.Kind.SCORCH,
            SOOT,
            SCORCH_RADIUS,
            SCORCH_TICKS));
    burst(world, centre, 120, 1.2, SovereignRenderer.BLAST, rand);
    Vec3 crown = Vec3.createVectorHelper(centre.xCoord, centre.yCoord + CROWN_RISE, centre.zCoord);
    shards(world, crown, Block.blockGold.blockID, 24, rand);
  }

  /** It crosses a phase: a shockwave of its colour, and in phase three its crown and mask crack. */
  static void phase(Sovereign boss) {
    World world = boss.worldObj;
    Vec3 centre = centre(boss);
    Random rand = boss.getRNG();
    float[] tint = SovereignRenderer.idle(boss.getPhase());
    add(
        new HexFlash(
            world,
            centre.xCoord,
            centre.yCoord,
            centre.zCoord,
            HexFlash.Kind.SHOCKWAVE,
            tint,
            7,
            SHOCKWAVE_TICKS));
    burst(world, centre, 60, 0.6, tint, rand);
    if (boss.getPhase() == 3) {
      shards(
          world,
          Vec3.createVectorHelper(centre.xCoord, centre.yCoord + 2.4, centre.zCoord),
          Block.blockGold.blockID,
          16,
          rand);
      shards(
          world,
          Vec3.createVectorHelper(centre.xCoord, centre.yCoord + 1.5, centre.zCoord),
          Block.blockNetherQuartz.blockID,
          16,
          rand);
    }
  }

  /** Struck: sparks of hex light burst from the core. */
  static void hurt(Sovereign boss) {
    burst(boss.worldObj, centre(boss), 10, 0.35, tint(boss), boss.getRNG());
  }

  /** Coming apart: light pours out, and at the end it bursts. */
  private static void dying(Sovereign boss, Vec3 centre, Random rand) {
    burst(boss.worldObj, centre, 4, 0.3, WHITE, rand);
    if (boss.deathTime == DEATH_TICKS - 1) {
      add(
          new HexFlash(
              boss.worldObj,
              centre.xCoord,
              centre.yCoord,
              centre.zCoord,
              HexFlash.Kind.BURST,
              SovereignRenderer.idle(3),
              6,
              BURST_TICKS));
      burst(boss.worldObj, centre, 150, 1, SovereignRenderer.idle(3), rand);
      shards(boss.worldObj, centre, Block.obsidian.blockID, 30, rand);
      shards(boss.worldObj, centre, Block.blockGold.blockID, 16, rand);
    }
  }

  private static void ring(World world, Vec3 at, Vec3 facing, float[] rgb, double radius) {
    add(
        new HexFlash(
                world, at.xCoord, at.yCoord, at.zCoord, HexFlash.Kind.RING, rgb, radius, RING_TICKS)
            .facing(facing));
  }

  /** Motes thrown in a cone along a direction. */
  private static void spray(
      World world, Vec3 at, Vec3 direction, double speed, int count, float[] rgb, Random rand) {
    for (int i = density(count); i > 0; i--) {
      double v = speed * (0.4 + rand.nextDouble() * 0.6);
      add(
          new Wisp(
              world,
              at.xCoord,
              at.yCoord,
              at.zCoord,
              (direction.xCoord + (rand.nextDouble() - 0.5) * 0.8) * v,
              (direction.yCoord + (rand.nextDouble() - 0.5) * 0.8) * v,
              (direction.zCoord + (rand.nextDouble() - 0.5) * 0.8) * v,
              size(rand),
              rgb,
              life(rand)));
    }
  }

  /** Motes flung outwards in every direction. */
  private static void burst(World world, Vec3 at, int count, double speed, float[] rgb, Random r) {
    for (int i = density(count); i > 0; i--) {
      double yaw = r.nextDouble() * Math.PI * 2;
      double pitch = (r.nextDouble() - 0.5) * Math.PI;
      double v = speed * (0.4 + r.nextDouble() * 0.6);
      add(
          new Wisp(
              world,
              at.xCoord,
              at.yCoord,
              at.zCoord,
              Math.cos(yaw) * Math.cos(pitch) * v,
              Math.sin(pitch) * v,
              Math.sin(yaw) * Math.cos(pitch) * v,
              size(r),
              rgb,
              life(r)));
    }
  }

  /** Fragments of a block flung outwards, such as the crown's gold. */
  private static void shards(World world, Vec3 at, int block, int count, Random rand) {
    for (int i = density(count); i > 0; i--) {
      double angle = rand.nextDouble() * Math.PI * 2;
      piece(world, at.xCoord, at.yCoord, at.zCoord, block, 0, angle, 0.5);
    }
  }

  /** Chips of the block a beam struck, if it struck one. */
  private static void debris(World world, Vec3 at, Vec3 aim, int count, Random rand) {
    int x = MathHelper.floor_double(at.xCoord);
    int y = MathHelper.floor_double(at.yCoord);
    int z = MathHelper.floor_double(at.zCoord);
    int block = world.getBlockId(x, y, z);
    if (block <= 0 || Block.blocksList[block] == null) {
      return;
    }
    for (int i = density(count); i > 0; i--) {
      double angle = Math.atan2(-aim.zCoord, -aim.xCoord) + (rand.nextDouble() - 0.5) * 2;
      piece(
          world,
          at.xCoord,
          at.yCoord,
          at.zCoord,
          block,
          world.getBlockMetadata(x, y, z),
          angle,
          0.3);
    }
  }

  private static void piece(
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
            MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z)));
  }

  /** The colour it glows with now: its attack's, or its phase's while it hunts. */
  private static float[] tint(Sovereign boss) {
    Sovereign.Action action = boss.getShownAction();
    if (action == Sovereign.Action.LANCE
        || action == Sovereign.Action.RUSH
        || action == Sovereign.Action.CHARGE
        || action == Sovereign.Action.BLAST) {
      return SovereignRenderer.color(action);
    }
    return SovereignRenderer.idle(boss.getPhase());
  }

  private static Vec3 centre(Sovereign boss) {
    return Vec3.createVectorHelper(boss.posX, boss.posY + Sovereign.CENTER_HEIGHT, boss.posZ);
  }

  /** The locked aim's direction, or where it faces when there is none. */
  private static Vec3 direction(Sovereign boss) {
    Vec3 aim = boss.getAim();
    if (aim.lengthVector() > 1e-3) {
      return aim.normalize();
    }
    double yaw = Math.toRadians(boss.rotationYaw);
    return Vec3.createVectorHelper(-Math.sin(yaw), 0, Math.cos(yaw));
  }

  private static Vec3 offset(Vec3 from, Vec3 direction, double distance) {
    return from.addVector(
        direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance);
  }

  private static Vec3 scale(Vec3 v, double factor) {
    return Vec3.createVectorHelper(v.xCoord * factor, v.yCoord * factor, v.zCoord * factor);
  }

  private static float size(Random rand) {
    return 0.35F + rand.nextFloat() * 0.35F;
  }

  private static int life(Random rand) {
    return WISP_LIFE / 2 + rand.nextInt(WISP_LIFE);
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
