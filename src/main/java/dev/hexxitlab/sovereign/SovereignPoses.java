package dev.hexxitlab.sovereign;

import net.minecraft.util.Vec3;

/**
 * Body language for each Sovereign action. Each attack has its own silhouette from its first tick:
 * the Hex Lance levels the sceptre along its line, the Ruin Rush rears back behind a drill of
 * shards, and Crown Break lifts the crown and spreads the arms. A recovery bares the core.
 */
final class SovereignPoses {
  private static final float PI = (float) Math.PI;
  private static final float HALF_PI = PI / 2;
  // Ticks for a release to snap out of its peak, then settle into the exposed slump.
  private static final float SNAP = 2.5F;
  private static final float SETTLE = 8;
  // Ticks for a recovery to rise back into its hover before it ends.
  private static final float RISE = 8;
  private static final float DEATH_TICKS = 20;
  // Hover in model units, negative upwards, and its slow bob.
  private static final float HOVER = -2;
  private static final float BOB_RATE = 0.08F;
  // The shards' ring: orbiting the waist, or levelled along an attack.
  private static final float ORBIT_RADIUS = 16;
  private static final float ORBIT_HEIGHT = -4;
  // How far a charge pitches the body forward, head first.
  private static final float DIVE = 1.1F;
  // Glow intensity by phase while hunting; the tints are in SovereignRenderer.
  private static final float[] IDLE_GLOW = {0.55F, 0.75F, 0.95F};

  private SovereignPoses() {}

  /** Whether a recovery after this action begins with its release. */
  static boolean releases(Sovereign.Action action) {
    return action == Sovereign.Action.LANCE
        || action == Sovereign.Action.CHARGE
        || action == Sovereign.Action.BLAST;
  }

  static void pose(
      SovereignModel.Pose p,
      Sovereign boss,
      Sovereign.Action action,
      float age,
      float tick,
      float time) {
    int phase = boss.getPhase();
    float remaining = Math.max(0, boss.getActionTicks() - tick);
    hover(p, phase, time);
    switch (action) {
      case HUNT:
        drift(p, boss);
        break;
      case LANCE:
        lance(p, windup(action, phase, remaining), age, time);
        break;
      case RUSH:
        rush(p, windup(action, phase, remaining), age, time);
        break;
      case CHARGE:
        charge(p, time);
        break;
      case BLAST:
        blast(p, windup(action, phase, remaining), age, time);
        break;
      case RECOVER:
        recover(p, phase, boss.getPreviousAction(), age, remaining, time);
        break;
      default:
        break;
    }
    if (boss.deathTime > 0) {
      death(p, (boss.deathTime + tick) / DEATH_TICKS, time);
    }
    flinch(p, boss.hurtTime > 0 ? (boss.hurtTime - tick) / boss.maxHurtTime : 0);
  }

  private static float windup(Sovereign.Action action, int phase, float remaining) {
    return clamp(1 - remaining / Sovereign.windup(action, phase));
  }

  /** The resting hover: sceptre upright at its right hand, shards orbiting its waist. */
  private static void hover(SovereignModel.Pose p, int phase, float time) {
    float bob = (float) Math.sin(time * BOB_RATE);
    p.lift = HOVER + bob * 0.8F;
    p.turn = 0;
    p.aim = 0;
    p.pitch = 0.06F;
    p.roll = 0;
    p.twist = 0;
    p.headPitch = 0.12F;
    p.headYaw = 0;
    p.headRoll = 0;
    p.mask = phase == 3 ? 1 : 0;
    p.plates = phase == 3 ? 0.12F : 0;
    p.shrug = 0;
    // The crown floats a beat behind the body.
    p.crownLift = 0.6F * (float) Math.sin(time * BOB_RATE - 0.8);
    p.crownSpin = crownTurn(phase, time);
    p.crownTilt = 0.04F * (float) Math.sin(time * 0.05);
    p.crownSpread = phase == 3 ? 2.5F : 0;
    p.haloSpin = time * 0.006F * phase;
    p.haloScale = 1;
    p.haloTilt = 0;
    p.shardAxis = 0;
    p.shardAlong = ORBIT_HEIGHT;
    p.shardRadius = ORBIT_RADIUS;
    p.shardSpin = shardTurn(phase, time);
    p.shardTilt = 0.3F;
    p.robe = 0.08F;
    p.robeRoll = 0;
    p.sway = 1;
    p.sceptreSpin = time * 0.02F;
    tint(p, SovereignRenderer.idle(phase), IDLE_GLOW[phase - 1]);
    p.heart = 0.35F + 0.15F * phase;
    p.orb = 0.25F;
    float hands = (float) Math.sin(time * BOB_RATE + 1.5);
    p.right.set(-12, 1 + 0.6F * hands, -2, -0.25F, 0.2F, 0.15F);
    p.left.set(12, 0.5F - 0.6F * hands, -1, -0.3F, -0.25F, -0.2F);
    p.sceptre.set(-11.5F, 3 + 0.6F * hands, -5.5F, 0.18F, 0, 0.12F);
    if (phase == 3) {
      // Unbound: it twitches as the hex strains to hold it together.
      float twitch = (float) (Math.sin(time * 0.9) * Math.sin(time * 0.23));
      p.headRoll += 0.1F * twitch;
      p.lift += 0.4F * (float) Math.sin(time * 0.7);
      p.left.pitch += 0.25F * twitch;
    }
  }

  private static float crownTurn(int phase, float time) {
    return phase == 3 ? time * 0.015F : 0.08F * (float) Math.sin(time * 0.03);
  }

  private static float shardTurn(int phase, float time) {
    return time * (0.03F + 0.01F * phase);
  }

  /** Leans into its flight and banks into a flank; the robe trails behind. */
  private static void drift(SovereignModel.Pose p, Sovereign boss) {
    Vec3 v = boss.getDrift();
    double yaw = Math.toRadians(boss.rotationYaw);
    float forward = (float) (-Math.sin(yaw) * v.xCoord + Math.cos(yaw) * v.zCoord);
    float right = (float) (-Math.cos(yaw) * v.xCoord - Math.sin(yaw) * v.zCoord);
    p.pitch += limit(forward * 0.4F, 0.35F);
    p.roll -= limit(right * 0.4F, 0.3F);
    p.robe += limit(forward * 0.6F, 0.6F);
    p.robeRoll += limit(right * 0.5F, 0.4F);
    p.sway += Math.min(1.5F, Math.abs(forward) + Math.abs(right));
  }

  /** Hex Lance: the sceptre floats onto the line of fire and spins up, flanked by both hands. */
  private static void lance(SovereignModel.Pose p, float progress, float age, float time) {
    float c = ease(Math.min(1, progress * 1.5F));
    p.aim = 1;
    p.pitch = mix(p.pitch, -0.08F, c);
    p.headPitch = mix(p.headPitch, 0, c);
    p.shrug = 0.25F * c;
    SovereignModel.Placement right = new SovereignModel.Placement();
    right.set(-5, 1, -5, -1.4F, 0.35F, 0.1F);
    p.right.blend(p.right, right, c);
    p.left.mirror(p.right);
    SovereignModel.Placement levelled = new SovereignModel.Placement();
    levelled.set(0, 0, -9, HALF_PI, 0, 0);
    p.sceptre.blend(p.sceptre, levelled, c);
    p.sceptreSpin += 0.1F * age + 0.012F * age * age;
    p.shardAxis = mix(p.shardAxis, HALF_PI, c);
    p.shardAlong = mix(p.shardAlong, 18, c);
    p.shardRadius = mix(p.shardRadius, 7 - 2.5F * progress, c);
    p.shardTilt = mix(p.shardTilt, -0.1F, c);
    p.shardSpin += 0.006F * age * age;
    p.haloSpin += 0.002F * age * age;
    p.haloScale = mix(1, 0.85F, c);
    tint(p, SovereignRenderer.LANCE, mix(p.glow, 0.8F + 0.9F * progress, c));
    p.heart += 0.3F * c;
    p.orb = 0.4F + 1.1F * progress;
    strain(p, progress, time);
  }

  /** Ruin Rush: it rears back, arms swept behind, while its shards drill along the route. */
  private static void rush(SovereignModel.Pose p, float progress, float age, float time) {
    float c = ease(Math.min(1, progress * 1.4F));
    p.aim = 1;
    p.lift = mix(p.lift, HOVER - 3, c);
    p.pitch = mix(p.pitch, -0.4F, c);
    p.headPitch = mix(p.headPitch, 0.35F, c);
    p.shrug = 0.35F * c;
    SovereignModel.Placement swept = new SovereignModel.Placement();
    swept.set(-9, 5, 8, 0.9F, 0.3F, 0.4F);
    p.right.blend(p.right, swept, c);
    p.left.mirror(p.right);
    // Couched along its route, against the body's lean.
    SovereignModel.Placement couched = new SovereignModel.Placement();
    couched.set(-4, 3, -6, HALF_PI + 0.4F, 0, 0);
    p.sceptre.blend(p.sceptre, couched, c);
    p.shardAxis = mix(p.shardAxis, HALF_PI + 0.4F, c);
    p.shardAlong = mix(p.shardAlong, 16, c);
    p.shardRadius = mix(p.shardRadius, 4.5F, c);
    p.shardTilt = mix(p.shardTilt, -0.25F, c);
    p.shardSpin += 0.01F * age * age;
    p.robe = mix(p.robe, -0.35F, c);
    p.sway = mix(p.sway, 2.5F, c);
    p.haloTilt = -0.5F * c;
    tint(p, SovereignRenderer.RUSH, mix(p.glow, 0.8F + 0.9F * progress, c));
    p.heart += 0.3F * c;
    strain(p, progress, time);
  }

  /** In flight: head first along its route behind the sceptre, the shards a drill at its tip. */
  private static void charge(SovereignModel.Pose p, float time) {
    p.aim = 1;
    p.lift = HOVER;
    p.pitch = DIVE;
    p.headPitch = -0.6F;
    p.shrug = 0.4F;
    p.right.set(-7, 12, 3, 0.4F, 0.2F, 0.3F);
    p.left.mirror(p.right);
    p.sceptre.set(0, -22, -1, HALF_PI - DIVE, 0, 0);
    p.sceptreSpin = time * 0.6F;
    p.shardAxis = HALF_PI - DIVE;
    p.shardAlong = 26;
    p.shardRadius = 3.5F;
    p.shardTilt = -0.25F;
    p.shardSpin = time * 0.5F;
    p.robe = 0.55F;
    p.sway = 2.5F;
    p.haloTilt = -1;
    p.haloScale = 0.8F;
    p.crownLift = -2;
    p.crownTilt = -0.35F;
    tint(p, SovereignRenderer.RUSH, 1.8F);
    p.heart = 1;
    p.orb = 1;
  }

  /** Crown Break: it rises, spreads its arms, and the crown climbs, spins and starts to part. */
  private static void blast(SovereignModel.Pose p, float progress, float age, float time) {
    float c = ease(Math.min(1, progress * 1.3F));
    p.lift = mix(p.lift, HOVER - 4, c);
    p.pitch = mix(p.pitch, -0.12F, c);
    p.headPitch = mix(p.headPitch, -0.55F, c);
    p.shrug = 0.45F * c;
    SovereignModel.Placement raised = new SovereignModel.Placement();
    raised.set(-13, -13, -2, PI, 0.2F, -0.3F);
    p.right.blend(p.right, raised, c);
    p.left.mirror(p.right);
    SovereignModel.Placement lifted = new SovereignModel.Placement();
    lifted.set(-15, -9, -5, 0.15F, 0, 0.35F);
    p.sceptre.blend(p.sceptre, lifted, c);
    p.sceptreSpin += 0.2F * age * c;
    p.crownLift = mix(p.crownLift, 9, c);
    p.crownSpin += 0.004F * age * age;
    p.crownSpread = mix(p.crownSpread, 2.5F + 1.5F * progress, c);
    p.haloScale = mix(1, 1.45F, c);
    p.haloSpin += 0.002F * age * age;
    p.shardAlong = mix(p.shardAlong, 22, c);
    p.shardRadius = mix(p.shardRadius, 13, c);
    p.shardTilt = mix(p.shardTilt, -0.25F, c);
    p.shardSpin += 0.003F * age * age;
    p.plates = mix(p.plates, 0.35F, c);
    p.robe = mix(p.robe, -0.15F, c);
    p.sway = mix(p.sway, 2.5F, c);
    tint(p, SovereignRenderer.BLAST, mix(p.glow, 0.8F + 1.0F * progress, c));
    p.heart += 0.6F * progress;
    p.orb = 0.6F * c;
    float tremble = progress > 0.5F ? 0.05F * (progress - 0.5F) * 2 : 0;
    p.roll += tremble * (float) Math.sin(time * 3.1);
    p.twist += tremble * (float) Math.sin(time * 2.3);
  }

  /** A release snaps out of its attack's peak, then it sags into an exposed slump and rises. */
  private static void recover(
      SovereignModel.Pose p,
      int phase,
      Sovereign.Action previous,
      float age,
      float remaining,
      float time) {
    SovereignModel.Pose stance = new SovereignModel.Pose();
    stance.copy(p);
    SovereignModel.Pose slump = new SovereignModel.Pose();
    slump.copy(p);
    exposed(slump, phase, time);
    if (releases(previous) && age < SNAP + SETTLE) {
      SovereignModel.Pose peak = new SovereignModel.Pose();
      peak.copy(stance);
      SovereignModel.Pose release = new SovereignModel.Pose();
      if (previous == Sovereign.Action.LANCE) {
        lance(peak, 1, 0, time);
        release.copy(peak);
        fire(release);
      } else if (previous == Sovereign.Action.CHARGE) {
        charge(peak, time);
        release.copy(peak);
        brake(release);
      } else {
        blast(peak, 1, 0, time);
        release.copy(peak);
        breakCrown(release);
      }
      if (age < SNAP) {
        float t = age / SNAP;
        p.blend(peak, release, 1 - (1 - t) * (1 - t));
      } else {
        p.blend(release, slump, ease((age - SNAP) / SETTLE));
      }
    } else {
      p.copy(slump);
    }
    p.blend(p, stance, ease(clamp(1 - remaining / RISE)));
  }

  /** The lance fires: the sceptre kicks back and up, the body jolts, the hands fling apart. */
  private static void fire(SovereignModel.Pose p) {
    p.sceptre.z += 6;
    p.sceptre.pitch -= 0.5F;
    p.pitch -= 0.3F;
    p.headPitch -= 0.3F;
    p.right.set(-13, -1, -3, -1.2F, 0.6F, 0.4F);
    p.left.mirror(p.right);
    p.shardRadius = 11;
    p.glow = 2.2F;
    p.orb = 2;
  }

  /** The charge stops dead: it flares upright, robe swinging forward, hands thrown ahead. */
  private static void brake(SovereignModel.Pose p) {
    p.pitch = -0.55F;
    p.headPitch = 0.3F;
    p.robe = -0.7F;
    p.right.set(-8, -2, -10, -1.6F, 0.3F, 0.2F);
    p.left.mirror(p.right);
    p.sceptre.set(-6, 0, -8, 0.4F, 0, 0.3F);
    p.shardAxis = 0;
    p.shardAlong = 0;
    p.shardRadius = 14;
    p.haloTilt = 0;
    p.glow = 2;
  }

  /** The crown bursts apart, the arms are flung wide and the head thrown back. */
  private static void breakCrown(SovereignModel.Pose p) {
    p.crownSpread = 16;
    p.crownLift = 14;
    p.headPitch = -0.75F;
    p.pitch = -0.25F;
    p.right.set(-19, -4, -1, PI * 0.6F, 0.3F, -0.9F);
    p.left.mirror(p.right);
    p.haloScale = 1.9F;
    p.shardRadius = 24;
    p.plates = 1;
    p.glow = 2.4F;
    p.heart = 2;
  }

  /** Drained and open: sunk low, slumped forward, hands hanging, the core bared and blazing. */
  private static void exposed(SovereignModel.Pose p, int phase, float time) {
    float heave = 0.04F * (float) Math.sin(time * 0.2);
    p.aim = 0;
    p.lift = 3 + 0.5F * (float) Math.sin(time * 0.1);
    p.pitch = 0.22F + heave;
    p.headPitch = 0.35F;
    p.headRoll = 0.1F;
    p.plates = 1;
    p.shrug = -0.2F;
    p.crownLift = -3.5F;
    p.crownTilt = 0.2F;
    p.crownSpread = phase == 3 ? 1.5F : 0.6F;
    p.haloTilt = 0.2F;
    p.shardAxis = 0;
    p.shardAlong = -14;
    p.shardRadius = 11;
    p.shardTilt = 1.1F;
    p.robe = 0.05F;
    p.sway = 0.5F;
    p.right.set(-11, 10, 0, 0.1F, 0.2F, 0.2F);
    p.left.mirror(p.right);
    p.sceptre.set(-14, 9, -4, 0.8F, 0, 0.5F);
    p.glow = 0.12F;
    p.heart = 1.4F + 0.3F * (float) Math.sin(time * 0.35);
    p.orb = 0;
  }

  /** It rises, arches back, and comes apart: crown, hands and shards drift away as it flares. */
  private static void death(SovereignModel.Pose p, float progress, float time) {
    float d = clamp(progress);
    p.aim = 0;
    p.lift = HOVER - 6 * d;
    p.pitch = -0.5F * d;
    p.headPitch = -0.9F * d;
    p.plates = 1;
    p.mask = 1;
    p.right.set(-12 - 10 * d, -6 * d, -2, PI * 0.5F * d, 0.3F, -0.6F * d);
    p.left.mirror(p.right);
    p.sceptre.set(-14 - 8 * d, 4, -4, 0.3F + 1.2F * d, 0, 0.6F * d);
    p.crownLift = 6 + 20 * d;
    p.crownSpread = 3 + 10 * d;
    p.crownSpin += 6 * d;
    p.haloScale = 1 + 1.2F * d;
    p.shardRadius = ORBIT_RADIUS + 30 * d;
    p.glow = 1 + 2.5F * d;
    p.heart = 1 + 3 * d;
    p.roll += 0.06F * d * (float) Math.sin(time * 2.9);
    p.twist += 0.06F * d * (float) Math.sin(time * 2.1);
  }

  /** A jolt and a flare when struck, fading over the hurt animation. */
  private static void flinch(SovereignModel.Pose p, float hurt) {
    float jolt = hurt * hurt;
    p.pitch -= 0.18F * jolt;
    p.headPitch -= 0.25F * jolt;
    p.glow += 0.6F * jolt;
  }

  /** A locked attack strains near its release. */
  private static void strain(SovereignModel.Pose p, float progress, float time) {
    if (progress > 0.6F) {
      float shake = 0.025F * (float) Math.sin(time * 2.7);
      p.roll += shake;
      p.headRoll -= shake;
    }
  }

  private static void tint(SovereignModel.Pose p, float[] rgb, float glow) {
    p.red = rgb[0];
    p.green = rgb[1];
    p.blue = rgb[2];
    p.glow = glow;
  }

  private static float limit(float value, float limit) {
    return Math.max(-limit, Math.min(limit, value));
  }

  private static float ease(float t) {
    return t * t * (3 - 2 * t);
  }

  private static float clamp(float t) {
    return Math.max(0, Math.min(1, t));
  }

  private static float mix(float a, float b, float t) {
    return a + (b - a) * t;
  }
}
