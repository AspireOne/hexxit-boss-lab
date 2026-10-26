package dev.hexxitlab.harness;

import dev.hexxitlab.BossCombat;
import dev.hexxitlab.BossLab;
import dev.hexxitlab.sovereign.Sovereign;
import dev.hexxitlab.warden.Cinder;
import dev.hexxitlab.warden.Warden;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Photographs the Warden and the Sovereign in every state, phase and warning from several angles,
 * by day and night and on several grounds. The bosses and Cinders are client-only puppets whose
 * synchronized state the gallery sets directly, so each picture is exact and repeatable; no combat
 * runs. The server builds a temporary platform, sets the time, and moves the camera player.
 */
final class GalleryScenario {
  private static final int PLATFORM_RADIUS = 14;
  private static final int PLATFORM_Y = 200;
  private static final int AIR_ABOVE = 14;
  private static final int DAY = 6000;
  private static final int NIGHT = 18000;
  // Client ticks after the server applies a shot before its transition or effect plays; the
  // camera must also hold still for some frames. Afterglows are brief, so they start only once
  // the picture is framed, and it is taken a fixed number of ticks later.
  private static final int SETTLE_TICKS = 12;
  // The run's tick budget: time to start, and a generous allowance per picture.
  private static final int START_TICKS = 600;
  private static final int SHOT_TICKS = 40;
  private static final int FRAMING_FRAMES = 6;
  private static final int EFFECT_LEAD = 2;
  // Normal field of view, so pictures compare between machines; the player's setting returns.
  private static final float NORMAL_FOV = 0;
  // Camera drift tolerated before the camera player is put back, and before a picture is refused.
  private static final double CAMERA_SLACK = 0.3;
  // Other mods' particles this close to the camera fill the picture; ours never come this near.
  private static final double CAMERA_CLEARANCE = 4;
  private static final double MODEL_DISTANCE = 4.5;
  private static final int PUPPET_ID = -4200;
  private static final int CINDER_ID = -4210;
  private static final double ORBIT_RADIUS = 4.5;
  private static final double ORBIT_HEIGHT = 2.5;
  private static final double SLAM_REACH = 3;
  private static final double LEAP_REACH = 8;
  private static final double HOOK_REACH = 14;
  private static final double AIRBORNE_HEIGHT = 3.5;
  private static final byte IMPACT_EFFECT = 20;
  private static final byte NOVA_EFFECT = 21;
  private static final byte GUARD_EFFECT = 22;
  private static final int SOVEREIGN_ID = -4220;
  // The Sovereign floats this far above the platform, as it hovers above a player's feet.
  private static final double SOVEREIGN_HOVER = 0.5;
  private static final double SOVEREIGN_LOOK = 2.4;
  private static final double SOVEREIGN_REACH = 30;
  private static final double SOVEREIGN_RUSH = 20;
  // Health within each phase: phase two and three show their spreading fissures.
  private static final int[] SOVEREIGN_HEALTH = {Sovereign.MAX_HEALTH, 2000, 900};
  private static final String OWN_PACKAGE = "dev.hexxitlab.";
  private static final int STONE = Block.stone.blockID;
  private static final int GRASS = Block.grass.blockID;
  private static final int SNOW = Block.blockSnow.blockID;

  /**
   * One picture: a puppet state and a camera. Angles are degrees from the puppet's front. A shot
   * poses either the Warden (action) or the Sovereign (sovereign); the other is null.
   */
  private static final class Shot {
    final String name;
    final Warden.Action action;
    final Sovereign.Action sovereign;
    Warden.Action previous = Warden.Action.HUNT;
    Sovereign.Action sovereignPrevious = Sovereign.Action.HUNT;
    // Sovereign only: its locked aim, smoothed flight, how far back a charge began, a phase it
    // crosses from, and frozen hurt and death animations.
    double aimX = SOVEREIGN_REACH;
    double aimY = -1.5;
    double aimZ;
    double driftX;
    double driftZ;
    double chargeBack;
    int phaseFrom;
    int hurt;
    int death;
    int timer;
    int age = 30;
    int phase = 1;
    int posture;
    double nova;
    // A transition starts from the previous action, so its afterglows play; captured at age.
    boolean transition;
    byte effect;
    boolean walking;
    int cinders;
    boolean marking;
    double azimuth;
    double distance = 9;
    double height = 2.2;
    double look = 1.8;
    // Looks at a point this far in front of the Warden instead of at its body.
    double lookAhead;
    int ground = STONE;
    boolean night;
    boolean hud;

    Shot(String name, Warden.Action action, int timer) {
      this.name = name;
      this.action = action;
      this.sovereign = null;
      this.timer = timer;
    }

    Shot(String name, Sovereign.Action sovereign, int timer) {
      this.name = name;
      this.action = null;
      this.sovereign = sovereign;
      this.timer = timer;
      this.look = SOVEREIGN_LOOK;
    }

    Shot from(Warden.Action previous, int age) {
      this.previous = previous;
      this.age = age;
      this.transition = true;
      return this;
    }

    Shot from(Sovereign.Action previous, int age) {
      this.sovereignPrevious = previous;
      this.age = age;
      this.transition = true;
      return this;
    }

    Shot aim(double x, double y, double z) {
      aimX = x;
      aimY = y;
      aimZ = z;
      return this;
    }

    Shot drift(double x, double z) {
      driftX = x;
      driftZ = z;
      return this;
    }

    Shot phase(int phase) {
      this.phase = phase;
      return this;
    }

    Shot camera(double azimuth, double distance, double height) {
      this.azimuth = azimuth;
      this.distance = distance;
      this.height = height;
      return this;
    }

    Shot ahead(double lookAhead) {
      this.lookAhead = lookAhead;
      this.look = 0;
      return this;
    }

    /** Looks this high above the platform, at a point ahead of the puppet. */
    Shot looking(double look) {
      this.look = look;
      return this;
    }

    Shot on(int ground) {
      this.ground = ground;
      return this;
    }

    Shot night() {
      this.night = true;
      return this;
    }

    Shot hud() {
      this.hud = true;
      return this;
    }
  }

  private final String run;
  private final File directory;
  private final List<Shot> shots = new ArrayList<Shot>();
  private volatile int shot;
  private volatile int appliedShot = -1;
  private volatile boolean finished;
  private int stage;
  private EntityPlayerMP player;
  private NBTTagCompound originalPlayer;
  private double cx;
  private double cz;
  private int groundShown = -1;
  private int clientTicks;
  private int framedFrames;
  private int shownShot = -1;
  // The server moves on to the next shot a tick later; until then, do not take this one again.
  private int capturedShot = -1;
  private Warden puppet;
  private Sovereign sovereign;
  private final List<Cinder> swarm = new ArrayList<Cinder>();
  private boolean originalHideGui;
  private float originalFov;
  private boolean clientPrepared;

  GalleryScenario(String run, File directory) {
    this.run = run;
    this.directory = directory;
    build();
  }

  private void build() {
    Warden.Action hunt = Warden.Action.HUNT;
    // The model from every side, and its phases.
    shots.add(new Shot("p1-front", hunt, 0).camera(0, MODEL_DISTANCE, 2.2));
    shots.add(new Shot("p1-three-quarter", hunt, 0).camera(35, MODEL_DISTANCE, 2.6));
    shots.add(new Shot("p1-side", hunt, 0).camera(90, MODEL_DISTANCE, 2.2));
    shots.add(new Shot("p1-back", hunt, 0).camera(180, MODEL_DISTANCE, 2.6));
    Shot walk = new Shot("p1-walk", hunt, 0).camera(60, MODEL_DISTANCE, 2.2);
    walk.walking = true;
    shots.add(walk);
    shots.add(new Shot("p2-front", hunt, 0).phase(2).camera(20, MODEL_DISTANCE, 2.2));
    shots.add(new Shot("p3-front", hunt, 0).phase(3).camera(20, MODEL_DISTANCE, 2.2));
    shots.add(new Shot("p3-three-quarter", hunt, 0).phase(3).camera(45, MODEL_DISTANCE, 2.8));
    shots.add(new Shot("p3-night", hunt, 0).phase(3).camera(20, 8, 2.2).night());
    shots.add(new Shot("p1-night", hunt, 0).camera(20, 8, 2.2).night());
    // Strikes: tracking against locked, the swing, and the landing.
    shots.add(new Shot("cleave-tracking", Warden.Action.CLEAVE, 16).camera(25, 9, 5).ahead(2.5));
    shots.add(new Shot("cleave-locked", Warden.Action.CLEAVE, 6).camera(25, 9, 5).ahead(2.5));
    shots.add(
        new Shot("cleave-locked-grass", Warden.Action.CLEAVE, 4)
            .camera(25, 9, 5)
            .ahead(2.5)
            .on(GRASS));
    shots.add(
        new Shot("cleave-locked-snow", Warden.Action.CLEAVE, 4)
            .camera(25, 9, 5)
            .ahead(2.5)
            .on(SNOW));
    shots.add(
        new Shot("cleave-locked-night", Warden.Action.CLEAVE, 4)
            .camera(25, 9, 5)
            .ahead(2.5)
            .night());
    shots.add(
        new Shot("cleave-swing", Warden.Action.CHAIN, 7)
            .from(Warden.Action.CLEAVE, 1)
            .camera(25, 9, 5)
            .ahead(2.5));
    shots.add(new Shot("cleave-from-above", Warden.Action.CLEAVE, 6).camera(200, 3, 12).ahead(2));
    shots.add(new Shot("backhand-locked", Warden.Action.BACKHAND, 6).camera(160, 9, 5).ahead(-2));
    shots.add(
        new Shot("backhand-whirl", Warden.Action.CHAIN, 7)
            .from(Warden.Action.BACKHAND, 1)
            .camera(160, 9, 5)
            .ahead(-2));
    shots.add(new Shot("bash-locked", Warden.Action.BASH, 5).camera(30, 7, 3.5).ahead(1.5));
    shots.add(
        new Shot("bash-thrust", Warden.Action.RECOVER, 14)
            .from(Warden.Action.BASH, 2)
            .camera(30, 7, 3.5)
            .ahead(1.5));
    shots.add(new Shot("slam-tracking", Warden.Action.SLAM, 21).camera(35, 10, 5).ahead(3));
    shots.add(new Shot("slam-locked", Warden.Action.SLAM, 8).camera(35, 10, 5).ahead(3));
    shots.add(
        new Shot("slam-locked-grass", Warden.Action.SLAM, 4).camera(35, 10, 5).ahead(3).on(GRASS));
    shots.add(
        new Shot("slam-locked-snow-night", Warden.Action.SLAM, 4)
            .camera(35, 10, 5)
            .ahead(3)
            .on(SNOW)
            .night());
    Shot impact =
        new Shot("slam-impact", Warden.Action.RECOVER, 48)
            .from(Warden.Action.SLAM, 2)
            .camera(35, 10, 5)
            .ahead(3);
    impact.effect = IMPACT_EFFECT;
    shots.add(impact);
    shots.add(
        new Shot("slam-planted-exposed", Warden.Action.RECOVER, 30)
            .from(Warden.Action.SLAM, 14)
            .camera(30, 8, 3)
            .ahead(1.5));
    shots.add(new Shot("leap-tracking", Warden.Action.LEAP, 12).camera(60, 9, 9).ahead(5));
    shots.add(new Shot("leap-locked", Warden.Action.LEAP, 3).camera(60, 9, 9).ahead(5));
    shots.add(new Shot("leap-airborne", Warden.Action.AIRBORNE, 8).camera(70, 11, 7).ahead(5));
    shots.add(new Shot("hook-tracking", Warden.Action.HOOK, 15).camera(55, 9, 5).ahead(5));
    shots.add(new Shot("hook-locked", Warden.Action.HOOK, 5).camera(55, 9, 5).ahead(5));
    shots.add(
        new Shot("hook-reel", Warden.Action.REEL, 3)
            .from(Warden.Action.HOOK, 2)
            .camera(70, 9, 4)
            .ahead(5));
    // Openings, the posture break, and the guard.
    shots.add(
        new Shot("exposed", Warden.Action.RECOVER, 20)
            .from(Warden.Action.HOOK, 6)
            .camera(25, MODEL_DISTANCE, 2.5));
    Shot posture = new Shot("posture-partial", hunt, 0).camera(15, MODEL_DISTANCE, 2.4).hud();
    posture.posture = Warden.POSTURE_LIMIT * 3 / 5;
    shots.add(posture);
    shots.add(
        new Shot("stagger", Warden.Action.STAGGER, 50)
            .from(Warden.Action.RECOVER, 30)
            .camera(25, MODEL_DISTANCE, 2.4)
            .hud());
    shots.add(
        new Shot("stagger-side", Warden.Action.STAGGER, 50)
            .from(Warden.Action.RECOVER, 30)
            .camera(90, MODEL_DISTANCE, 2.4));
    Shot guard = new Shot("guard-flash", hunt, 0).camera(15, MODEL_DISTANCE, 2.4);
    guard.effect = GUARD_EFFECT;
    shots.add(guard);
    // Harrowings: warded and channelling, then the nova.
    shots.add(
        new Shot("kindle", Warden.Action.KINDLE, 20)
            .from(Warden.Action.HUNT, 20)
            .camera(25, MODEL_DISTANCE, 2.6));
    Shot reclaim =
        new Shot("reclaim", Warden.Action.RECLAIM, 60)
            .from(Warden.Action.HUNT, 30)
            .phase(2)
            .camera(30, 22, 9);
    reclaim.nova = Warden.NOVA_BASE_RADIUS + Warden.NOVA_GROWTH * 2;
    shots.add(reclaim);
    Shot inside =
        new Shot("reclaim-inside", Warden.Action.RECLAIM, 60)
            .from(Warden.Action.HUNT, 30)
            .phase(2)
            .camera(20, 8, 2.2)
            .on(SNOW);
    inside.nova = reclaim.nova;
    shots.add(inside);
    Shot gather =
        new Shot("nova-windup", Warden.Action.NOVA, 8)
            .from(Warden.Action.RECLAIM, 22)
            .phase(2)
            .camera(30, 22, 9);
    gather.nova = reclaim.nova;
    shots.add(gather);
    Shot gatherNight =
        new Shot("nova-windup-night", Warden.Action.NOVA, 8)
            .from(Warden.Action.RECLAIM, 22)
            .phase(2)
            .camera(30, 22, 9)
            .night();
    gatherNight.nova = reclaim.nova;
    shots.add(gatherNight);
    Shot burst =
        new Shot("nova-burst", Warden.Action.RECOVER, 38)
            .from(Warden.Action.NOVA, 3)
            .phase(3)
            .camera(30, 22, 9);
    burst.nova = reclaim.nova;
    burst.effect = NOVA_EFFECT;
    shots.add(burst);
    shots.add(
        new Shot("p3-cleave-locked", Warden.Action.CLEAVE, 4).phase(3).camera(25, 9, 5).ahead(2.5));
    shots.add(
        new Shot("p3-slam-locked", Warden.Action.SLAM, 6).phase(3).camera(35, 10, 5).ahead(3));
    // The swarm.
    Shot orbit = new Shot("cinders-orbit", hunt, 0).phase(2).camera(30, 10, 4);
    orbit.cinders = 3;
    shots.add(orbit);
    Shot mark = new Shot("cinder-mark", hunt, 0).phase(2).camera(30, 10, 4);
    mark.cinders = 3;
    mark.marking = true;
    shots.add(mark);
    Shot markNight = new Shot("cinder-mark-night", hunt, 0).phase(2).camera(30, 10, 4).night();
    markNight.cinders = 3;
    markNight.marking = true;
    shots.add(markNight);
    buildSovereign();
  }

  private void buildSovereign() {
    Sovereign.Action hunt = Sovereign.Action.HUNT;
    Sovereign.Action lance = Sovereign.Action.LANCE;
    Sovereign.Action rush = Sovereign.Action.RUSH;
    Sovereign.Action blast = Sovereign.Action.BLAST;
    Sovereign.Action recover = Sovereign.Action.RECOVER;
    // The model from every side, its flight, and its phases.
    shots.add(new Shot("sov-p1-front", hunt, 0).camera(0, 7, 2.8));
    shots.add(new Shot("sov-p1-three-quarter", hunt, 0).camera(35, 7, 3.4));
    shots.add(new Shot("sov-p1-side", hunt, 0).camera(90, 7, 2.8));
    shots.add(new Shot("sov-p1-back", hunt, 0).camera(180, 7, 3.4));
    shots.add(new Shot("sov-p1-below", hunt, 0).camera(20, 6, 0.4));
    shots.add(new Shot("sov-p1-night", hunt, 0).camera(25, 9, 2.8).night());
    shots.add(new Shot("sov-pursuit", hunt, 0).drift(0.9, 0).camera(70, 8, 3));
    shots.add(new Shot("sov-flank", hunt, 0).drift(0, 0.5).camera(10, 8, 3));
    shots.add(new Shot("sov-p2-front", hunt, 0).phase(2).camera(20, 7, 2.8));
    Shot shift = new Shot("sov-p2-shift", hunt, 0).phase(2).camera(20, 9, 3);
    shift.phaseFrom = 1;
    shots.add(shift);
    shots.add(new Shot("sov-p3-front", hunt, 0).phase(3).camera(20, 7, 2.8));
    shots.add(new Shot("sov-p3-three-quarter", hunt, 0).phase(3).camera(45, 7, 3.6));
    shots.add(new Shot("sov-p3-night", hunt, 0).phase(3).camera(25, 9, 2.8).night());
    // The Hex Lance: early and late windup, from the side, at night, off-axis, and its release.
    shots.add(new Shot("sov-lance-early", lance, 20).camera(40, 9, 4).ahead(4).looking(1.6));
    shots.add(new Shot("sov-lance-late", lance, 3).camera(40, 9, 4).ahead(4).looking(1.6));
    shots.add(new Shot("sov-lance-side", lance, 6).camera(90, 11, 3).ahead(6).looking(1.6));
    shots.add(new Shot("sov-lance-night", lance, 4).camera(40, 9, 4).ahead(4).looking(1.6).night());
    shots.add(new Shot("sov-lance-offaxis", lance, 5).aim(16, 6, 12).camera(-30, 9, 4));
    shots.add(
        new Shot("sov-lance-release", recover, 19)
            .from(lance, 1)
            .camera(40, 9, 4)
            .ahead(4)
            .looking(1.6));
    // The Ruin Rush: windup, the charge in flight, and the stop.
    Sovereign.Action charge = Sovereign.Action.CHARGE;
    shots.add(
        new Shot("sov-rush-early", rush, 20)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .camera(40, 9, 4)
            .ahead(4)
            .looking(1.6));
    shots.add(
        new Shot("sov-rush-late", rush, 3)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .camera(40, 9, 4)
            .ahead(4)
            .looking(1.6));
    shots.add(
        new Shot("sov-rush-side", rush, 5)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .camera(90, 11, 3)
            .ahead(6)
            .looking(1.6));
    shots.add(
        new Shot("sov-rush-night", rush, 4)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .camera(40, 9, 4)
            .ahead(4)
            .looking(1.6)
            .night());
    Shot flight =
        new Shot("sov-charge", charge, 3)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .drift(2.5, 0)
            .camera(60, 11, 3)
            .ahead(-3)
            .looking(1.6);
    flight.chargeBack = 10;
    shots.add(flight);
    shots.add(
        new Shot("sov-charge-brake", recover, 23)
            .from(charge, 1)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .camera(50, 9, 3));
    // Crown Break: windup close and wide, by night, from inside, and its burst.
    shots.add(new Shot("sov-blast-early", blast, 30).camera(30, 9, 3.5));
    shots.add(new Shot("sov-blast-late", blast, 4).camera(30, 9, 3.5));
    shots.add(new Shot("sov-blast-wide", blast, 6).camera(30, 26, 12));
    shots.add(new Shot("sov-blast-wide-night", blast, 6).camera(30, 26, 12).night());
    shots.add(new Shot("sov-blast-inside", blast, 8).camera(200, 8, 2.2));
    shots.add(new Shot("sov-blast-release", recover, 26).from(blast, 2).camera(30, 26, 12));
    shots.add(new Shot("sov-blast-release-close", recover, 25).from(blast, 3).camera(30, 11, 4));
    // Openings, hits, the later phases' attacks, and death.
    shots.add(new Shot("sov-exposed", recover, 12).from(lance, 12).camera(25, 7, 3));
    shots.add(new Shot("sov-exposed-side", recover, 12).from(lance, 12).camera(90, 7, 3));
    shots.add(new Shot("sov-exposed-p3", recover, 12).from(lance, 12).phase(3).camera(25, 7, 3));
    Shot hurt = new Shot("sov-hurt", hunt, 0).camera(25, 7, 3);
    hurt.hurt = 7;
    shots.add(hurt);
    shots.add(
        new Shot("sov-p3-lance-late", lance, 3).phase(3).camera(40, 9, 4).ahead(4).looking(1.6));
    shots.add(
        new Shot("sov-p3-rush-late", rush, 3)
            .phase(3)
            .aim(SOVEREIGN_RUSH, -1, 0)
            .camera(40, 9, 4)
            .ahead(4)
            .looking(1.6));
    shots.add(new Shot("sov-p3-blast-late", blast, 4).phase(3).camera(30, 11, 4));
    Shot dying = new Shot("sov-death-early", hunt, 0).phase(3).camera(25, 9, 3.5);
    dying.death = 6;
    shots.add(dying);
    Shot dead = new Shot("sov-death-late", hunt, 0).phase(3).camera(25, 9, 3.5);
    dead.death = 16;
    shots.add(dead);
  }

  /** Server ticks the whole gallery may take: a picture takes about a second. */
  int tickLimit() {
    return START_TICKS + shots.size() * SHOT_TICKS;
  }

  // ---- Server: platform, time and camera position.

  boolean tick(MinecraftServer server, EntityPlayerMP current) {
    player = current;
    World world = player.worldObj;
    if (stage == 0) {
      originalPlayer = new NBTTagCompound();
      player.writeEntityToNBT(originalPlayer);
      world.getWorldInfo().setRaining(false);
      world.getWorldInfo().setThundering(false);
      player.capabilities.allowFlying = true;
      player.capabilities.isFlying = true;
      player.capabilities.disableDamage = true;
      player.sendPlayerAbilities();
      cx = Math.floor(player.posX) + 0.5;
      cz = Math.floor(player.posZ) + 0.5;
      platform(world, STONE, true);
      stage = 1;
      return false;
    }
    if (finished) {
      player.readEntityFromNBT(originalPlayer);
      player.sendPlayerAbilities();
      player.setPositionAndUpdate(player.posX, player.posY, player.posZ);
      BossLab.LOGGER.info("Gallery captured " + shots.size() + " pictures");
      return true;
    }
    int index = shot;
    Shot s = shots.get(index);
    if (index != appliedShot) {
      world.setWorldTime(s.night ? NIGHT : DAY);
      if (s.ground != groundShown) {
        platform(world, s.ground, false);
        groundShown = s.ground;
      }
      appliedShot = index;
    }
    // Held every tick: a camera beyond the platform would otherwise fall.
    if (!framed(player, s)) {
      player.motionX = player.motionY = player.motionZ = 0;
      player.setPositionAndUpdate(cameraX(s), cameraY(player, s), cameraZ(s));
    }
    player.fallDistance = 0;
    return false;
  }

  // The puppet faces +X; the camera orbits it from its front.
  private double cameraX(Shot s) {
    return cx + Math.cos(Math.toRadians(s.azimuth)) * s.distance;
  }

  private double cameraZ(Shot s) {
    return cz + Math.sin(Math.toRadians(s.azimuth)) * s.distance;
  }

  private static double cameraY(EntityPlayer viewer, Shot s) {
    return PLATFORM_Y + s.height - viewer.getEyeHeight();
  }

  private boolean framed(EntityPlayer viewer, Shot s) {
    return Math.abs(viewer.posX - cameraX(s)) < CAMERA_SLACK
        && Math.abs(viewer.posY - cameraY(viewer, s)) < CAMERA_SLACK
        && Math.abs(viewer.posZ - cameraZ(s)) < CAMERA_SLACK;
  }

  private void platform(World world, int block, boolean probe) {
    int x0 = (int) Math.floor(cx);
    int z0 = (int) Math.floor(cz);
    for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
      for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
        if (probe) {
          for (int dy = -1; dy < AIR_ABOVE; dy++) {
            require(
                world.isAirBlock(x0 + dx, PLATFORM_Y + dy, z0 + dz),
                "Gallery platform requires air");
          }
        }
        world.setBlock(x0 + dx, PLATFORM_Y - 1, z0 + dz, block);
      }
    }
  }

  // ---- Client: puppets and pictures.

  void clientTick(Minecraft minecraft) {
    if (minecraft.theWorld == null || appliedShot < 0 || finished) {
      return;
    }
    if (!clientPrepared) {
      originalHideGui = minecraft.gameSettings.hideGUI;
      originalFov = minecraft.gameSettings.fovSetting;
      clientPrepared = true;
    }
    int index = appliedShot;
    Shot s = shots.get(index);
    if (index != shownShot) {
      shownShot = index;
      clientTicks = 0;
      framedFrames = 0;
      minecraft.effectRenderer.clearEffects(minecraft.theWorld);
      respawn(minecraft, s);
    }
    // The server sends the time only once a second, and some overlays toggle the GUI back.
    minecraft.theWorld.setWorldTime(s.night ? NIGHT : DAY);
    minecraft.gameSettings.hideGUI = !s.hud;
    minecraft.gameSettings.fovSetting = NORMAL_FOV;
    minecraft.thePlayer.capabilities.isFlying = true;
    minecraft.thePlayer.motionX = minecraft.thePlayer.motionY = minecraft.thePlayer.motionZ = 0;
    clearAround(minecraft);
    clientTicks++;
    if (clientTicks > SHOT_TICKS * 5) {
      throw new IllegalStateException(
          "Gallery "
              + s.name
              + " was never captured: "
              + framedFrames
              + " framed frames, camera at "
              + minecraft.thePlayer.posX
              + ", "
              + minecraft.thePlayer.posY
              + ", "
              + minecraft.thePlayer.posZ
              + (sovereign != null
                  ? ", shown " + sovereignShown() + ", health " + sovereign.getHealth()
                  : ""));
    }
    drive(s);
  }

  private void respawn(Minecraft minecraft, Shot s) {
    minecraft.theWorld.removeEntityFromWorld(PUPPET_ID);
    minecraft.theWorld.removeEntityFromWorld(SOVEREIGN_ID);
    for (int i = 0; i < swarm.size(); i++) {
      minecraft.theWorld.removeEntityFromWorld(CINDER_ID - i);
    }
    swarm.clear();
    puppet = null;
    sovereign = null;
    if (s.sovereign != null) {
      respawnSovereign(minecraft, s);
      return;
    }
    puppet = new Warden(minecraft.theWorld);
    double y = PLATFORM_Y + (s.action == Warden.Action.AIRBORNE ? AIRBORNE_HEIGHT : 0);
    place(puppet, cx, y, cz, -90);
    minecraft.theWorld.addEntityToWorld(PUPPET_ID, puppet);
    for (int i = 0; i < s.cinders; i++) {
      Cinder cinder = new Cinder(minecraft.theWorld);
      minecraft.theWorld.addEntityToWorld(CINDER_ID - i, cinder);
      swarm.add(cinder);
    }
    watch(puppet, "PHASE", s.phase);
    watch(puppet, "POSTURE", s.posture);
    watch(puppet, "TIMER", s.timer);
    BossCombat.writeFloat(puppet.getDataWatcher(), field("NOVA_RADIUS"), s.nova);
    aim(s);
    if (s.transition) {
      // Starts in the previous action; drive switches it so the picture lands at the shot's age.
      watch(puppet, "ACTION", s.previous.ordinal());
      set(puppet, "shownAction", s.previous);
    } else {
      watch(puppet, "ACTION", s.action.ordinal());
      set(puppet, "shownAction", s.action);
      set(puppet, "previousAction", s.previous);
    }
  }

  /** Strike geometry in front of the puppet, as the server would lock it. */
  private void aim(Shot s) {
    double x = cx;
    double y = PLATFORM_Y;
    double z = cz;
    if (s.action == Warden.Action.SLAM || s.previous == Warden.Action.SLAM) {
      x += SLAM_REACH;
    } else if (s.action == Warden.Action.LEAP || s.action == Warden.Action.AIRBORNE) {
      x += LEAP_REACH;
    } else if (s.action == Warden.Action.HOOK || s.action == Warden.Action.REEL) {
      x += HOOK_REACH;
      y += Warden.CHEST_HEIGHT - 1;
    }
    BossCombat.writeFloat(puppet.getDataWatcher(), field("STRIKE_X"), x);
    BossCombat.writeFloat(puppet.getDataWatcher(), field("STRIKE_Y"), y);
    BossCombat.writeFloat(puppet.getDataWatcher(), field("STRIKE_Z"), z);
    BossCombat.writeFloat(puppet.getDataWatcher(), field("STRIKE_YAW"), -90);
  }

  private void respawnSovereign(Minecraft minecraft, Shot s) {
    sovereign = new Sovereign(minecraft.theWorld);
    place(sovereign, cx, PLATFORM_Y + SOVEREIGN_HOVER, cz, -90);
    minecraft.theWorld.addEntityToWorld(SOVEREIGN_ID, sovereign);
    sovereignWatch("HEALTH", sovereignHealth(s.phaseFrom != 0 ? s.phaseFrom : s.phase, s));
    sovereignWatch("TIMER", s.timer);
    BossCombat.writeFloat(sovereign.getDataWatcher(), sovereignField("AIM_X"), s.aimX);
    BossCombat.writeFloat(sovereign.getDataWatcher(), sovereignField("AIM_Y"), s.aimY);
    BossCombat.writeFloat(sovereign.getDataWatcher(), sovereignField("AIM_Z"), s.aimZ);
    if (s.transition) {
      // Starts in the previous action; driveSovereign switches it so the picture lands at age.
      sovereignWatch("ACTION", s.sovereignPrevious.ordinal());
      set(sovereign, "shownAction", s.sovereignPrevious);
    } else {
      sovereignWatch("ACTION", s.sovereign.ordinal());
      set(sovereign, "shownAction", s.sovereign);
      set(sovereign, "previousAction", s.sovereignPrevious);
    }
    if (s.chargeBack > 0) {
      set(
          sovereign,
          "chargeStart",
          Vec3.createVectorHelper(
              cx - s.chargeBack, PLATFORM_Y + SOVEREIGN_HOVER + s.chargeBack * 0.05, cz));
    }
  }

  private static int sovereignHealth(int phase, Shot s) {
    return s.death > 0 ? 0 : SOVEREIGN_HEALTH[phase - 1];
  }

  private void driveSovereign(Shot s) {
    if (sovereign == null) {
      return;
    }
    place(sovereign, cx, PLATFORM_Y + SOVEREIGN_HOVER, cz, -90);
    if (clientTicks == SETTLE_TICKS) {
      if (s.transition) {
        sovereignWatch("ACTION", s.sovereign.ordinal());
      }
      if (s.phaseFrom != 0) {
        sovereignWatch("HEALTH", sovereignHealth(s.phase, s));
      }
    }
    if (!s.transition || sovereignPosed(s)) {
      // Hold the pose still: freeze its age and timer.
      sovereignWatch("TIMER", s.timer);
      if (sovereignShown() == s.sovereign) {
        set(sovereign, "actionAge", Integer.valueOf(s.age));
      }
    }
    set(sovereign, "driftX", Double.valueOf(s.driftX));
    set(sovereign, "driftZ", Double.valueOf(s.driftZ));
    if (s.hurt > 0) {
      sovereign.hurtTime = s.hurt;
      sovereign.maxHurtTime = 10;
    }
    if (s.death > 0) {
      sovereign.deathTime = s.death;
    }
  }

  /** Whether a transition shot's puppet has reached its action and age. */
  private boolean sovereignPosed(Shot s) {
    int age = ((Integer) read(reflect(Sovereign.class, "actionAge"), sovereign)).intValue();
    return sovereignShown() == s.sovereign && age >= s.age;
  }

  private Sovereign.Action sovereignShown() {
    return (Sovereign.Action) read(reflect(Sovereign.class, "shownAction"), sovereign);
  }

  private void sovereignWatch(String name, int value) {
    sovereign.getDataWatcher().updateObject(sovereignField(name), Integer.valueOf(value));
  }

  private static int sovereignField(String name) {
    return constant(Sovereign.class, name);
  }

  private void drive(Shot s) {
    if (s.sovereign != null) {
      driveSovereign(s);
      return;
    }
    if (puppet == null) {
      return;
    }
    double y = PLATFORM_Y + (s.action == Warden.Action.AIRBORNE ? AIRBORNE_HEIGHT : 0);
    place(puppet, cx, y, cz, -90);
    if (s.transition && clientTicks == SETTLE_TICKS) {
      // The puppet's own client tick notices the change and plays its transition.
      watch(puppet, "ACTION", s.action.ordinal());
    }
    if (!s.transition || shownAction() == s.action && actionAge() >= s.age) {
      // Hold the pose still: freeze its age and timer.
      watch(puppet, "TIMER", s.timer);
      if (shownAction() == s.action) {
        set(puppet, "actionAge", Integer.valueOf(s.age));
      }
    }
    if (s.walking) {
      puppet.limbYaw = puppet.prevLimbYaw = 1;
      puppet.limbSwing += 0.6F;
    }
    if (s.effect != 0 && clientTicks == SETTLE_TICKS) {
      puppet.handleHealthUpdate(s.effect);
    }
    for (int i = 0; i < swarm.size(); i++) {
      Cinder cinder = swarm.get(i);
      double angle = i * Math.PI * 2 / swarm.size() + 0.6;
      boolean marked = s.marking && i == 0;
      double radius = marked ? ORBIT_RADIUS + 1 : ORBIT_RADIUS;
      place(
          cinder,
          cx + Math.cos(angle) * radius,
          PLATFORM_Y + ORBIT_HEIGHT + (marked ? 1 : 0),
          cz + Math.sin(angle) * radius,
          0);
      cinderWatch(cinder, "STATE", (marked ? Cinder.State.MARK : Cinder.State.ORBIT).ordinal());
      if (marked) {
        // Off to the side, so the dive corridor crosses the picture rather than facing it.
        double a = Math.toRadians(s.azimuth - 90);
        BossCombat.writeFloat(
            cinder.getDataWatcher(), cinderField("TARGET_X"), cx + Math.cos(a) * 4);
        BossCombat.writeFloat(cinder.getDataWatcher(), cinderField("TARGET_Y"), PLATFORM_Y + 0.9);
        BossCombat.writeFloat(
            cinder.getDataWatcher(), cinderField("TARGET_Z"), cz + Math.sin(a) * 4);
      }
    }
  }

  private Warden.Action shownAction() {
    return (Warden.Action) read(reflect(Warden.class, "shownAction"), puppet);
  }

  private int actionAge() {
    return ((Integer) read(reflect(Warden.class, "actionAge"), puppet)).intValue();
  }

  void render(Minecraft minecraft) throws IOException {
    if (finished
        || minecraft.thePlayer == null
        || shownShot < 0
        || shownShot != appliedShot
        || shownShot == capturedShot) {
      return;
    }
    Shot s = shots.get(shownShot);
    // Looks at the body, or at a ground warning ahead of it (negative is behind it).
    double tx = cx + s.lookAhead;
    double ty =
        PLATFORM_Y
            + s.look
            + (s.action == Warden.Action.AIRBORNE ? AIRBORNE_HEIGHT : 0)
            + (s.sovereign != null ? SOVEREIGN_HOVER : 0);
    face(minecraft.thePlayer, tx, ty, cz);
    if (!framed(minecraft.thePlayer, s)) {
      framedFrames = 0;
      return;
    }
    framedFrames++;
    boolean effect = s.effect != 0 || s.phaseFrom != 0;
    int ready = SETTLE_TICKS + Math.max(s.transition ? s.age + 1 : 0, effect ? EFFECT_LEAD : 0);
    if (framedFrames < FRAMING_FRAMES || clientTicks < ready) {
      return;
    }
    if (s.transition
        && (s.sovereign != null
            ? sovereign == null || !sovereignPosed(s)
            : shownAction() != s.action || actionAge() < s.age)) {
      return;
    }
    File evidence = new File(directory, "bosslab-evidence");
    if (!evidence.isDirectory() && !evidence.mkdirs()) {
      throw new IOException("Cannot create screenshot directory");
    }
    BossLab.LOGGER.info(
        "Gallery "
            + s.name
            + ": "
            + (clientTicks - SETTLE_TICKS)
            + " ticks after its cue; "
            + minecraft.debug);
    BossLab.LOGGER.info(
        ScreenShotHelper.func_74292_a(
            evidence,
            run + "-GALLERY-" + (shownShot < 10 ? "0" : "") + shownShot + "-" + s.name + ".png",
            minecraft.displayWidth,
            minecraft.displayHeight));
    capturedShot = shownShot;
    if (shownShot + 1 < shots.size()) {
      shot = shownShot + 1;
    } else {
      minecraft.theWorld.removeEntityFromWorld(PUPPET_ID);
      minecraft.theWorld.removeEntityFromWorld(SOVEREIGN_ID);
      for (int i = 0; i < swarm.size(); i++) {
        minecraft.theWorld.removeEntityFromWorld(CINDER_ID - i);
      }
      minecraft.gameSettings.hideGUI = originalHideGui;
      minecraft.gameSettings.fovSetting = originalFov;
      finished = true;
    }
  }

  /** Removes other mods' particles crowding the camera, such as trails from worn equipment. */
  private static void clearAround(Minecraft minecraft) {
    for (List<?> layer : particleLayers(minecraft.effectRenderer)) {
      for (Iterator<?> i = layer.iterator(); i.hasNext(); ) {
        EntityFX fx = (EntityFX) i.next();
        if (!fx.getClass().getName().startsWith(OWN_PACKAGE)
            && fx.getDistanceToEntity(minecraft.thePlayer) < CAMERA_CLEARANCE) {
          i.remove();
        }
      }
    }
  }

  /** The renderer's particle lists, found by type because field names are obfuscated at runtime. */
  private static List<?>[] particleLayers(EffectRenderer renderer) {
    for (Field f : EffectRenderer.class.getDeclaredFields()) {
      if (f.getType() == List[].class) {
        f.setAccessible(true);
        return (List<?>[]) read(f, renderer);
      }
    }
    throw new IllegalStateException("No particle layers in the effect renderer");
  }

  private static void place(EntityLiving entity, double x, double y, double z, float yaw) {
    entity.setPositionAndRotation(x, y, z, yaw, 0);
    entity.lastTickPosX = entity.prevPosX = x;
    entity.lastTickPosY = entity.prevPosY = y;
    entity.lastTickPosZ = entity.prevPosZ = z;
    entity.prevRotationYaw = entity.renderYawOffset = entity.prevRenderYawOffset = yaw;
    entity.rotationYawHead = entity.prevRotationYawHead = yaw;
  }

  private static void face(EntityLiving viewer, double x, double y, double z) {
    double dx = x - viewer.posX;
    double dz = z - viewer.posZ;
    double dy = y - (viewer.posY + viewer.getEyeHeight());
    viewer.rotationYaw = viewer.prevRotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    viewer.rotationPitch =
        viewer.prevRotationPitch =
            (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
  }

  private static int field(String name) {
    return constant(Warden.class, name);
  }

  private static int cinderField(String name) {
    return constant(Cinder.class, name);
  }

  private static int constant(Class<?> type, String name) {
    return ((Integer) read(reflect(type, name), null)).intValue();
  }

  private static void watch(Warden warden, String name, int value) {
    warden.getDataWatcher().updateObject(field(name), Integer.valueOf(value));
  }

  private static void cinderWatch(Cinder cinder, String name, int value) {
    cinder.getDataWatcher().updateObject(cinderField(name), Integer.valueOf(value));
  }

  /** The gallery reaches the puppets' private presentation state; it never ships. */
  private static Field reflect(Class<?> type, String name) {
    try {
      Field f = type.getDeclaredField(name);
      f.setAccessible(true);
      return f;
    } catch (NoSuchFieldException error) {
      throw new IllegalStateException(error);
    }
  }

  private static Object read(Field f, Object owner) {
    try {
      return f.get(owner);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException(error);
    }
  }

  private static void set(Entity entity, String name, Object value) {
    try {
      reflect(entity.getClass(), name).set(entity, value);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException(error);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
