package dev.hexxitlab.warden;

/**
 * Keyframed body language for each Warden action. Windups coil toward a held peak, executions snap
 * through in a few ticks, and every state holds a silhouette that reads at a distance: the shield
 * squared in front while guarding, lowered while exposed, and gone once shattered.
 */
final class WardenPoses {
  private static final float PI = (float) Math.PI;
  // Ticks for a strike to sweep through after its windup, and for it to settle afterwards.
  private static final float SNAP = 2.5F;
  private static final float SETTLE = 6;
  // Ticks for a recovery or stagger to rise back into its stance before it ends.
  private static final float RISE = 10;
  private static final float WALK_RATE = 0.6662F;
  // How far each arm angles inward for both hands to meet on the grip.
  private static final float GRIP_ANGLE = 0.55F;

  private WardenPoses() {}

  static void pose(WardenModel.Pose p, Warden boss, Warden.Action action, float age, float tick) {
    int phase = boss.getPhase();
    float remaining = Math.max(0, boss.getActionTicks() - tick);
    Warden.Action previous = boss.getPreviousAction();
    ready(p, phase);
    switch (action) {
      case CLEAVE:
        cleave(p, phase, windup(action, phase, remaining), age);
        break;
      case BACKHAND:
        backhand(p, phase, windup(action, phase, remaining));
        break;
      case BASH:
        bash(p, windup(action, phase, remaining));
        break;
      case SLAM:
        raise(p, phase, windup(action, phase, remaining));
        break;
      case LEAP:
        crouch(p, phase, windup(action, phase, remaining));
        break;
      case AIRBORNE:
        airborne(p, phase, 1 - remaining / Warden.LEAP_AIRTIME);
        break;
      case HOOK:
        unleash(p, windup(action, phase, remaining));
        break;
      case REEL:
        reel(p, age);
        break;
      case CHAIN:
        followThrough(p, phase, previous, age);
        break;
      case RECOVER:
        recover(p, phase, previous, age, remaining);
        break;
      case STAGGER:
        stagger(p, phase, age, remaining);
        break;
      case KINDLE:
        kindle(p, age);
        break;
      case RECLAIM:
        reclaim(p, age);
        break;
      case NOVA:
        gather(p, phase, clamp(1 - remaining / Warden.NOVA_WINDUP), age);
        break;
      default:
        break;
    }
  }

  /** Windup progress from 0 to 1. */
  private static float windup(Warden.Action action, int phase, float remaining) {
    return clamp(1 - remaining / Warden.windup(action, phase));
  }

  /** Guard stance: shield squared in front, sword resting back over the right shoulder. */
  private static void ready(WardenModel.Pose p, int phase) {
    p.lift = 0;
    p.spin = 0;
    p.waistPitch = 0.12F;
    p.waistYaw = 0;
    p.waistRoll = 0;
    p.headPitch = -0.1F;
    p.headYaw = 0;
    p.cape = 0.1F;
    p.rightHip = 0.2F;
    p.rightKnee = 0.3F;
    p.rightSplay = 0.08F;
    p.leftHip = -0.3F;
    p.leftKnee = 0.35F;
    p.leftSplay = -0.08F;
    p.shieldTilt = 0;
    p.shieldTurn = 0;
    p.swordTwist = 0;
    if (phase < 3) {
      p.swordPitch = -0.3F;
      p.swordYaw = 0.1F;
      p.swordRoll = 0.15F;
      p.swordElbow = -1.6F;
      p.swordWrist = -0.6F;
      guard(p);
    } else {
      // Guard shattered: both hands on the hilt, point levelled at its prey.
      p.waistPitch = 0.25F;
      p.headPitch = -0.15F;
      p.swordPitch = -0.9F;
      p.swordYaw = -GRIP_ANGLE;
      p.swordRoll = 0;
      p.swordElbow = -0.5F;
      p.swordWrist = 1.05F;
      twoHanded(p);
    }
  }

  private static void guard(WardenModel.Pose p) {
    p.shieldPitch = -0.55F;
    p.shieldYaw = 0.45F;
    p.shieldRoll = -0.1F;
    p.shieldElbow = -0.95F;
  }

  /**
   * The off hand joins the sword hand on the grip. Arms pitch first, so a yaw sweeps a raised arm
   * horizontally: both arms angle inward by the grip angle to meet.
   */
  private static void twoHanded(WardenModel.Pose p) {
    p.shieldPitch = p.swordPitch;
    p.shieldYaw = p.swordYaw + 2 * GRIP_ANGLE;
    p.shieldRoll = -p.swordRoll;
    p.shieldElbow = p.swordElbow;
  }

  /** Shield lowered to its side: the guard is visibly down. */
  private static void lowered(WardenModel.Pose p) {
    p.shieldPitch = 0.1F;
    p.shieldYaw = 0;
    p.shieldRoll = -0.3F;
    p.shieldElbow = -0.25F;
    p.shieldTilt = 0.35F;
    p.shieldTurn = -0.5F;
  }

  private static void cleave(WardenModel.Pose p, int phase, float progress, float age) {
    // Coils fast while tracking, then strains at the peak once it locks.
    float coil = ease(Math.min(1, progress * 1.4F));
    p.waistYaw += 0.5F * coil;
    p.waistPitch += 0.05F * coil;
    p.headYaw -= 0.45F * coil;
    // Arm swept out and back at shoulder height, the blade levelled behind it.
    p.swordPitch = mix(p.swordPitch, -1.35F, coil);
    p.swordYaw = mix(p.swordYaw, 2.0F, coil);
    p.swordRoll = mix(p.swordRoll, 0, coil);
    p.swordElbow = mix(p.swordElbow, -0.45F, coil);
    p.swordWrist = mix(p.swordWrist, 1.55F, coil);
    stance(p, coil, 0.45F);
    if (phase == 3) {
      twoHanded(p);
    } else {
      p.shieldYaw += 0.35F * coil;
    }
    strain(p, progress, age);
  }

  private static void backhand(WardenModel.Pose p, int phase, float progress) {
    float coil = ease(Math.min(1, progress * 1.6F));
    // Coiled the other way: the sword cocked across its body, pointing back past its left side.
    p.waistYaw -= 0.7F * coil;
    p.headYaw += 0.9F * coil;
    p.swordPitch = mix(p.swordPitch, -1.2F, coil);
    p.swordYaw = mix(p.swordYaw, -2.3F, coil);
    p.swordRoll = mix(p.swordRoll, 0, coil);
    p.swordElbow = mix(p.swordElbow, -0.3F, coil);
    p.swordWrist = mix(p.swordWrist, 1.6F, coil);
    stance(p, coil, 0.6F);
    if (phase == 3) {
      twoHanded(p);
    }
  }

  private static void bash(WardenModel.Pose p, float progress) {
    float coil = ease(Math.min(1, progress * 1.5F));
    p.waistYaw -= 0.45F * coil;
    p.waistPitch += 0.1F * coil;
    p.shieldPitch = mix(p.shieldPitch, -0.25F, coil);
    p.shieldYaw = mix(p.shieldYaw, 0.75F, coil);
    p.shieldElbow = mix(p.shieldElbow, -1.55F, coil);
    stance(p, coil, 0.5F);
  }

  /** Slam windup, and the overhead raise carried into a leap. */
  private static void raise(WardenModel.Pose p, int phase, float progress) {
    float up = ease(Math.min(1, progress * 1.6F)) * 0.85F + 0.15F * ease(progress);
    p.waistPitch = mix(p.waistPitch, -0.25F, up);
    p.headPitch = mix(p.headPitch, 0.1F, up);
    p.swordPitch = mix(p.swordPitch, -2.95F, up);
    p.swordYaw = mix(p.swordYaw, -0.25F, up);
    p.swordRoll = mix(p.swordRoll, 0.1F, up);
    p.swordElbow = mix(p.swordElbow, -0.55F, up);
    p.swordWrist = mix(p.swordWrist, 1.7F, up);
    p.cape = mix(p.cape, 0.35F, up);
    if (phase == 3) {
      twoHanded(p);
    } else {
      p.shieldPitch = mix(p.shieldPitch, -0.4F, up);
    }
    stance(p, up, 0.3F);
  }

  private static void crouch(WardenModel.Pose p, int phase, float progress) {
    float coil = ease(progress);
    p.waistPitch = mix(p.waistPitch, 0.55F, coil);
    p.headPitch = mix(p.headPitch, -0.55F, coil);
    p.swordPitch = mix(p.swordPitch, 0.2F, coil);
    p.swordRoll = mix(p.swordRoll, 0.35F, coil);
    p.cape = mix(p.cape, 0.5F, coil);
    if (phase == 3) {
      p.shieldPitch = mix(p.shieldPitch, 0.5F, coil);
      p.shieldRoll = mix(p.shieldRoll, -0.35F, coil);
      p.shieldElbow = mix(p.shieldElbow, -0.4F, coil);
    }
    p.rightHip = mix(p.rightHip, -0.5F, coil);
    p.rightKnee = mix(p.rightKnee, 1.3F, coil);
    p.leftHip = mix(p.leftHip, -1.0F, coil);
    p.leftKnee = mix(p.leftKnee, 1.4F, coil);
  }

  private static void airborne(WardenModel.Pose p, int phase, float flight) {
    raise(p, phase, 1);
    float tuck = (float) Math.sin(flight * PI);
    p.rightHip = -0.4F - 0.6F * tuck;
    p.rightKnee = 0.6F + 0.9F * tuck;
    p.leftHip = -0.9F - 0.5F * tuck;
    p.leftKnee = 0.7F + 0.8F * tuck;
    p.cape = 0.4F + 0.9F * flight;
    // Plunging: the body tips forward as it falls.
    p.waistPitch = mix(-0.25F, 0.3F, flight * flight);
  }

  private static void unleash(WardenModel.Pose p, float progress) {
    // The chain looses from the ember core in its chest: chest thrust out, arms flung back.
    float coil = ease(Math.min(1, progress * 1.3F));
    p.waistPitch = mix(p.waistPitch, -0.3F, coil);
    p.headPitch = mix(p.headPitch, 0.15F, coil);
    p.swordPitch = mix(p.swordPitch, 0.55F, coil);
    p.swordRoll = mix(p.swordRoll, 0.75F, coil);
    p.swordElbow = mix(p.swordElbow, -0.35F, coil);
    p.swordWrist = mix(p.swordWrist, 1.2F, coil);
    p.shieldPitch = mix(p.shieldPitch, 0.45F, coil);
    p.shieldYaw = mix(p.shieldYaw, 0, coil);
    p.shieldRoll = mix(p.shieldRoll, -0.75F, coil);
    p.shieldElbow = mix(p.shieldElbow, -0.3F, coil);
    p.shieldTurn = -0.6F * coil;
    p.cape = mix(p.cape, 0.3F, coil);
    stance(p, coil, 0.35F);
  }

  private static void reel(WardenModel.Pose p, float age) {
    unleash(p, 1);
    float pull = ease(clamp(age / 3));
    p.waistPitch = mix(p.waistPitch, -0.45F, pull);
    p.leftHip = mix(p.leftHip, -0.7F, pull);
    p.leftKnee = mix(p.leftKnee, 0.2F, pull);
    p.rightHip = mix(p.rightHip, 0.35F, pull);
    p.rightKnee = mix(p.rightKnee, 0.7F, pull);
  }

  /** The swing of a strike that just landed, starting from its windup's peak. */
  private static void followThrough(
      WardenModel.Pose p, int phase, Warden.Action previous, float age) {
    if (previous == Warden.Action.CLEAVE) {
      sweep(p, phase, age);
    } else if (previous == Warden.Action.BACKHAND) {
      whirl(p, phase, age);
    }
  }

  private static void sweep(WardenModel.Pose p, int phase, float age) {
    WardenModel.Pose peak = new WardenModel.Pose();
    peak.copy(p);
    cleave(peak, phase, 1, 0);
    WardenModel.Pose end = new WardenModel.Pose();
    end.copy(p);
    end.waistYaw = -0.85F;
    end.waistPitch = 0.3F;
    end.headYaw = 0.4F;
    end.swordPitch = -1.5F;
    end.swordYaw = -0.9F;
    end.swordRoll = 0;
    end.swordElbow = -0.1F;
    end.swordWrist = 1.6F;
    end.rightHip = -0.1F;
    end.rightKnee = 0.6F;
    end.leftHip = -0.55F;
    end.leftKnee = 0.6F;
    if (phase == 3) {
      twoHanded(end);
    } else {
      end.shieldYaw = 0.15F;
    }
    snap(p, peak, end, age);
  }

  private static void whirl(WardenModel.Pose p, int phase, float age) {
    WardenModel.Pose peak = new WardenModel.Pose();
    peak.copy(p);
    backhand(peak, phase, 1);
    WardenModel.Pose end = new WardenModel.Pose();
    end.copy(p);
    // A full turn: the blade sweeps the whole rear arc and it ends facing forward again.
    end.spin = 2 * PI;
    end.swordPitch = -1.5F;
    end.swordYaw = 1.57F;
    end.swordRoll = 0;
    end.swordElbow = -0.1F;
    end.swordWrist = 1.55F;
    end.waistPitch = 0.25F;
    if (phase == 3) {
      twoHanded(end);
    }
    snap(p, peak, end, age * 0.8F);
    if (age > SNAP / 0.8F) {
      p.spin = 0;
    }
  }

  /** Sweeps from a peak to an end pose in a few ticks, then settles back towards the stance. */
  private static void snap(
      WardenModel.Pose p, WardenModel.Pose peak, WardenModel.Pose end, float age) {
    WardenModel.Pose stance = new WardenModel.Pose();
    stance.copy(p);
    if (age < SNAP) {
      float t = age / SNAP;
      p.blend(peak, end, 1 - (1 - t) * (1 - t));
    } else {
      p.blend(end, stance, ease(clamp((age - SNAP) / SETTLE)));
    }
  }

  private static void recover(
      WardenModel.Pose p, int phase, Warden.Action previous, float age, float remaining) {
    WardenModel.Pose stance = new WardenModel.Pose();
    stance.copy(p);
    if (previous == Warden.Action.SLAM || previous == Warden.Action.AIRBORNE) {
      WardenModel.Pose peak = new WardenModel.Pose();
      peak.copy(p);
      if (previous == Warden.Action.AIRBORNE) {
        airborne(peak, phase, 1);
      } else {
        raise(peak, phase, 1);
      }
      WardenModel.Pose planted = new WardenModel.Pose();
      planted.copy(p);
      planted(planted, phase, age);
      if (age < SNAP) {
        float t = age / SNAP;
        p.blend(peak, planted, t * t);
      } else {
        p.copy(planted);
      }
    } else if (previous == Warden.Action.BASH) {
      thrust(p, age);
    } else if (previous == Warden.Action.NOVA) {
      roar(p, age);
    } else {
      exposed(p, phase, age);
    }
    // Rises back into its stance as the opening closes.
    p.blend(p, stance, ease(clamp(1 - remaining / RISE)));
  }

  /** The blade driven into the ground ahead, the body bowed over it: an opening. */
  private static void planted(WardenModel.Pose p, int phase, float age) {
    float heave = 0.04F * (float) Math.sin(age * 0.25);
    p.waistPitch = 0.7F + heave;
    p.headPitch = 0.05F;
    p.swordPitch = -1.05F;
    p.swordYaw = -0.2F;
    p.swordRoll = 0.1F;
    p.swordElbow = -0.25F;
    p.swordWrist = 1.5F;
    p.rightHip = 0.3F;
    p.rightKnee = 0.9F;
    p.leftHip = -0.9F;
    p.leftKnee = 1.0F;
    p.cape = 0.05F;
    if (phase == 3) {
      twoHanded(p);
    } else {
      lowered(p);
    }
  }

  private static void thrust(WardenModel.Pose p, float age) {
    float out = age < SNAP ? 1 - (1 - age / SNAP) * (1 - age / SNAP) : 1;
    float lower = ease(clamp((age - SETTLE) / SETTLE));
    p.waistYaw = 0.45F * out;
    p.waistPitch = mix(0.12F, 0.4F, out);
    p.shieldPitch = -1.45F;
    p.shieldYaw = 0.25F;
    p.shieldElbow = -0.1F;
    p.leftHip = mix(-0.3F, -0.8F, out);
    p.leftKnee = mix(0.35F, 0.6F, out);
    WardenModel.Pose down = new WardenModel.Pose();
    down.copy(p);
    lowered(down);
    p.blend(p, down, lower);
  }

  private static void roar(WardenModel.Pose p, float age) {
    float burst = age < SNAP ? age / SNAP : 1;
    float slump = ease(clamp((age - 12) / 10));
    p.waistPitch = mix(-0.4F, 0.35F, slump);
    p.headPitch = mix(-0.6F, 0.3F, slump);
    p.swordPitch = mix(-1.2F, 0.05F, slump) * burst;
    p.swordRoll = mix(1.2F, 0.2F, slump);
    p.swordElbow = -0.2F;
    p.swordWrist = mix(0.8F, 2.2F, slump);
    p.shieldPitch = mix(-1.2F, 0.05F, slump) * burst;
    p.shieldRoll = mix(-1.2F, -0.2F, slump);
    p.shieldYaw = 0;
    p.shieldElbow = -0.2F;
  }

  /** A generic opening: shield lowered, sword point resting on the ground, shoulders heaving. */
  private static void exposed(WardenModel.Pose p, int phase, float age) {
    float heave = 0.05F * (float) Math.sin(age * 0.3);
    p.waistPitch = 0.35F + heave;
    p.headPitch = 0.2F;
    p.swordPitch = -0.25F;
    p.swordYaw = 0;
    p.swordRoll = 0.15F;
    p.swordElbow = -0.35F;
    p.swordWrist = 1.2F;
    if (phase == 3) {
      twoHanded(p);
    } else {
      lowered(p);
    }
  }

  /** Posture broken: it reels, drops to one knee leaning on its sword, then rises. */
  private static void stagger(WardenModel.Pose p, int phase, float age, float remaining) {
    WardenModel.Pose stance = new WardenModel.Pose();
    stance.copy(p);
    WardenModel.Pose reeling = new WardenModel.Pose();
    reeling.copy(p);
    reeling.waistPitch = -0.45F;
    reeling.headPitch = -0.5F;
    reeling.swordPitch = 0.4F;
    reeling.swordRoll = 0.7F;
    reeling.swordElbow = -0.3F;
    reeling.shieldPitch = 0.3F;
    reeling.shieldRoll = -0.8F;
    reeling.shieldElbow = -0.3F;
    reeling.rightHip = 0.45F;
    reeling.rightKnee = 0.2F;
    reeling.leftHip = -0.1F;
    reeling.leftKnee = 0.1F;
    WardenModel.Pose kneel = new WardenModel.Pose();
    kneel.copy(p);
    kneel.waistPitch = 0.5F + 0.04F * (float) Math.sin(age * 0.2);
    kneel.headPitch = 0.45F;
    kneel.swordPitch = -1.2F;
    kneel.swordYaw = 0.2F;
    kneel.swordRoll = 0.1F;
    kneel.swordElbow = -0.2F;
    kneel.swordWrist = 2.47F;
    kneel.rightHip = 0.15F;
    kneel.rightKnee = 1.5F;
    kneel.leftHip = -1.3F;
    kneel.leftKnee = 1.35F;
    kneel.cape = 0.02F;
    if (phase == 3) {
      kneel.shieldPitch = 0.1F;
      kneel.shieldRoll = -0.2F;
      kneel.shieldElbow = -0.3F;
    } else {
      lowered(kneel);
      kneel.shieldTilt = 0.7F;
    }
    if (age < 4) {
      p.blend(stance, reeling, ease(age / 4));
    } else {
      p.blend(reeling, kneel, ease(clamp((age - 4) / 8)));
    }
    p.blend(p, stance, ease(clamp(1 - remaining / RISE)));
  }

  /** Harrowing: sword raised point-up in front of its visor, embers gathering. */
  private static void kindle(WardenModel.Pose p, float age) {
    float rise = ease(clamp(age / 8));
    p.lift = -1.5F * rise;
    p.waistPitch = mix(p.waistPitch, -0.1F, rise);
    p.headPitch = mix(p.headPitch, -0.2F, rise);
    p.swordPitch = mix(p.swordPitch, -1.25F, rise);
    p.swordYaw = mix(p.swordYaw, -0.55F, rise);
    p.swordRoll = mix(p.swordRoll, 0, rise);
    p.swordElbow = mix(p.swordElbow, -0.95F, rise);
    p.swordWrist = mix(p.swordWrist, 0.6F, rise);
    p.shieldPitch = mix(p.shieldPitch, -0.3F, rise);
    p.shieldRoll = mix(p.shieldRoll, -0.45F, rise);
  }

  /** Harrowing: arms outstretched, recalling its swarm while it hovers. */
  private static void reclaim(WardenModel.Pose p, float age) {
    float open = ease(clamp(age / 8));
    p.lift = (-1.5F - 0.6F * (float) Math.sin(age * 0.15)) * open;
    p.waistPitch = mix(p.waistPitch, -0.15F, open);
    p.headPitch = mix(p.headPitch, -0.45F, open);
    p.swordPitch = mix(p.swordPitch, -0.2F, open);
    p.swordYaw = 0;
    p.swordRoll = mix(p.swordRoll, 1.25F, open);
    p.swordElbow = mix(p.swordElbow, -0.2F, open);
    p.swordWrist = mix(p.swordWrist, 2.2F, open);
    p.shieldPitch = mix(p.shieldPitch, -0.2F, open);
    p.shieldYaw = 0;
    p.shieldRoll = mix(p.shieldRoll, -1.25F, open);
    p.shieldElbow = mix(p.shieldElbow, -0.2F, open);
    p.shieldTurn = -1.2F * open;
    p.cape = mix(p.cape, 0.5F, open);
    p.rightHip = p.leftHip = 0.05F;
    p.rightKnee = p.leftKnee = 0.25F;
  }

  /** The nova gathers: it hunches around its blazing core, trembling harder until it bursts. */
  private static void gather(WardenModel.Pose p, int phase, float progress, float age) {
    reclaim(p, 8);
    float curl = ease(Math.min(1, progress * 2));
    p.lift = mix(p.lift, 0, curl);
    p.waistPitch = mix(p.waistPitch, 0.45F, curl);
    p.headPitch = mix(p.headPitch, 0.35F, curl);
    p.swordPitch = mix(p.swordPitch, -1.1F, curl);
    p.swordYaw = mix(p.swordYaw, -0.8F, curl);
    p.swordRoll = mix(p.swordRoll, 0.1F, curl);
    p.swordElbow = mix(p.swordElbow, -1.2F, curl);
    p.swordWrist = mix(p.swordWrist, 0.4F, curl);
    p.shieldPitch = mix(p.shieldPitch, -1.1F, curl);
    p.shieldYaw = mix(p.shieldYaw, 0.8F, curl);
    p.shieldRoll = mix(p.shieldRoll, -0.1F, curl);
    p.shieldElbow = mix(p.shieldElbow, -1.2F, curl);
    p.shieldTurn = mix(p.shieldTurn, 0, curl);
    stance(p, curl, 0.7F);
    float shake = 0.03F * progress * (float) Math.sin(age * 3.1);
    p.waistYaw += shake;
    p.waistRoll += shake;
  }

  /** Braces the legs: rear leg back, lead leg forward, knees bending by depth. */
  private static void stance(WardenModel.Pose p, float amount, float depth) {
    p.rightHip = mix(p.rightHip, 0.45F, amount);
    p.rightKnee = mix(p.rightKnee, 0.3F + depth, amount);
    p.leftHip = mix(p.leftHip, -0.45F - depth * 0.3F, amount);
    p.leftKnee = mix(p.leftKnee, 0.3F + depth, amount);
  }

  /** A locked strike strains at its peak. */
  private static void strain(WardenModel.Pose p, float progress, float age) {
    float shake = progress > 0.6F ? 0.02F * (float) Math.sin(age * 2.7) : 0;
    p.waistYaw += shake;
    p.swordPitch += shake;
  }

  /** Heavy steps: a short stride with bent knees and a lurching sway. */
  static void walk(WardenModel.Pose p, float limb, float amount, Warden.Action action) {
    if (amount <= 0.01F) {
      return;
    }
    float swing = (float) Math.cos(limb * WALK_RATE);
    float lift = (float) Math.sin(limb * WALK_RATE);
    float stride = 0.6F * Math.min(1, amount);
    p.rightHip += swing * stride;
    p.leftHip -= swing * stride;
    p.rightKnee += Math.max(0, lift) * stride * 1.3F;
    p.leftKnee += Math.max(0, -lift) * stride * 1.3F;
    p.waistRoll += swing * 0.06F * Math.min(1, amount);
    p.waistYaw -= swing * 0.08F * Math.min(1, amount);
    p.cape += Math.abs(swing) * 0.15F * amount;
    if (action == Warden.Action.HUNT || action == Warden.Action.CHAIN) {
      p.swordPitch -= swing * 0.12F * amount;
    }
  }

  /** Idle breathing; rage breathes harder and faster. */
  static void breathe(WardenModel.Pose p, float time, int phase) {
    float breath = (float) Math.sin(time * (phase == 3 ? 0.16F : 0.09F));
    float depth = phase == 3 ? 0.045F : 0.025F;
    p.waistPitch += breath * depth;
    p.headPitch -= breath * depth * 0.6F;
    p.swordRoll += breath * depth * 0.5F;
    p.shieldRoll -= breath * depth * 0.5F;
    p.cape += breath * depth;
  }

  /** A jolt backwards when hit, fading over the hurt animation. */
  static void recoil(WardenModel.Pose p, float hurt) {
    float jolt = hurt * hurt;
    p.waistPitch -= 0.22F * jolt;
    p.headPitch -= 0.3F * jolt;
    p.swordPitch += 0.18F * jolt;
    p.shieldPitch += 0.12F * jolt;
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
