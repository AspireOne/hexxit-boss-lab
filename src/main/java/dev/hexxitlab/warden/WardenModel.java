package dev.hexxitlab.warden;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * An armoured warden knight with a greatsword and tower shield. Each frame poses a hierarchical rig
 * from the synchronized combat state: windups coil, executions snap through, and states hold
 * readable postures. Glowing details are additive passes over the base texture.
 */
final class WardenModel extends ModelBase {
  private static final String EMBERS = "/mods/hexxitbosslab/textures/entity/warden_embers.png";
  private static final String RAGE = "/mods/hexxitbosslab/textures/entity/warden_rage.png";
  private static final String RUNES = "/mods/hexxitbosslab/textures/entity/warden_runes.png";
  private static final String CRACKS = "/mods/hexxitbosslab/textures/entity/warden_cracks.png";
  private static final int TEXTURE_SIZE = 128;
  private static final float PI = (float) Math.PI;
  // Leg segment lengths in model units, used to keep the lowest foot or knee on the ground.
  private static final float THIGH = 7;
  private static final float SHIN = 8;
  private static final float KNEE_PAD = 2.5F;
  private static final float LEG = THIGH + SHIN;
  private static final float HIP = 9;
  // Ticks over which an unplanned change of action crossfades into its new pose.
  private static final float BLEND_TICKS = 3;
  private static final float FULL_BRIGHT = 240;
  // Glow intensity by phase: a smoulder, kindled embers, then rage.
  private static final float[] EMBER_GLOW = {0.45F, 0.85F, 1};
  private static final float[][] EMBER_TINT = {
    {1, 0.5F, 0.15F}, {1, 0.6F, 0.2F}, {1, 0.28F, 0.12F}
  };
  private static final float[] RUNE_TINT = {1, 0.62F, 0.25F};
  private static final float[] FLARE_TINT = {1, 0.95F, 0.75F};
  private static final float[] CRACK_TINT = {1, 0.75F, 0.35F};
  // Broken posture blazes beyond the full pattern, cooling over the stagger.
  private static final float BROKEN_BLAZE = 0.8F;

  private final ModelRenderer hips = part(0, HIP, 0);
  private final ModelRenderer torso = part(0, HIP, 0);
  private final ModelRenderer head = part(0, -13, -0.5F);
  private final ModelRenderer cape = part(0, -12.5F, 4);
  private final ModelRenderer swordArm = part(-8.5F, -11, 0);
  private final ModelRenderer swordForearm = part(0, 5, 0);
  private final ModelRenderer sword = part(0, 6.5F, 0);
  private final ModelRenderer shieldArm = part(8.5F, -11, 0);
  private final ModelRenderer shieldForearm = part(0, 5, 0);
  private final ModelRenderer shield = part(0, 3, 0);
  private final ModelRenderer rightThigh = part(-3, HIP, 0);
  private final ModelRenderer rightShin = part(0, THIGH, 0);
  private final ModelRenderer leftThigh = part(3, HIP, 0);
  private final ModelRenderer leftShin = part(0, THIGH, 0);
  private final Pose pose = new Pose();
  private final Pose from = new Pose();
  private final Pose to = new Pose();
  private int shownEntity = -1;
  private Warden.Action shownAction;
  private float blendStart;
  private boolean blending;

  WardenModel() {
    textureWidth = textureHeight = TEXTURE_SIZE;
    box(hips, 88, 38, -5.5F, -1, -3.5F, 11, 2, 7);
    box(hips, 0, 38, -6.5F, 0, -3, 1, 6, 6);
    box(hips, 0, 38, 5.5F, 0, -3, 1, 6, 6);
    box(torso, 0, 22, -6.5F, -13, -4, 13, 8, 8);
    box(torso, 0, 59, -4.5F, -12, -5, 9, 6, 1);
    box(torso, 14, 38, -4.5F, -5, -3, 9, 5, 6);
    box(torso, 0, 50, -4, -14.5F, -3.5F, 8, 2, 7);
    box(torso, 98, 59, -1.5F, -10, -5.5F, 3, 3, 1);
    box(head, 42, 22, -3.5F, -7, -3.5F, 7, 7, 7);
    box(head, 74, 59, -3, -4, -4.5F, 6, 4, 1);
    box(head, 30, 66, -4, -6, -5, 8, 1, 2);
    box(head, 54, 50, -0.5F, -9, -3.5F, 1, 2, 7);
    box(cape, 6, 0, -6, 0, 0, 12, 20, 1);
    arm(swordArm, swordForearm, -1);
    arm(shieldArm, shieldForearm, 1);
    box(sword, 40, 59, -0.5F, -0.5F, -2.5F, 1, 1, 5);
    box(sword, 106, 59, -1, -1, 2.5F, 2, 2, 2);
    box(sword, 80, 38, -1, -4, -4.5F, 2, 8, 2);
    box(sword, 58, 0, -0.5F, -1.5F, -20.5F, 1, 3, 16);
    box(sword, 114, 59, -0.5F, -1, -22.5F, 1, 2, 2);
    box(sword, 50, 66, -0.5F, -0.5F, -24.5F, 1, 1, 2);
    box(shield, 32, 0, -6, -10, -3.5F, 12, 20, 1);
    box(shield, 64, 59, -2, -3, -4.5F, 4, 5, 1);
    box(shield, 0, 66, -6.5F, -10.5F, -4, 13, 1, 2);
    box(shield, 0, 66, -6.5F, 9.5F, -4, 13, 1, 2);
    box(shield, 0, 0, -6.5F, -10, -4, 1, 20, 2);
    box(shield, 0, 0, 5.5F, -10, -4, 1, 20, 2);
    leg(rightThigh, rightShin);
    leg(leftThigh, leftShin);
    torso.addChild(head);
    torso.addChild(cape);
    torso.addChild(swordArm);
    torso.addChild(shieldArm);
    swordArm.addChild(swordForearm);
    swordForearm.addChild(sword);
    shieldArm.addChild(shieldForearm);
    shieldForearm.addChild(shield);
    rightThigh.addChild(rightShin);
    leftThigh.addChild(leftShin);
  }

  private ModelRenderer part(float x, float y, float z) {
    ModelRenderer part = new ModelRenderer(this);
    part.setTextureSize(TEXTURE_SIZE, TEXTURE_SIZE);
    part.setRotationPoint(x, y, z);
    return part;
  }

  private static void box(
      ModelRenderer part, int u, int v, float x, float y, float z, int w, int h, int d) {
    part.setTextureOffset(u, v).addBox(x, y, z, w, h, d);
  }

  /** Upper arm with pauldron, and a gauntleted forearm; side is -1 for the right arm. */
  private static void arm(ModelRenderer upper, ModelRenderer forearm, int side) {
    upper.mirror = forearm.mirror = side > 0;
    box(upper, 64, 38, -2, -1, -2, 4, 6, 4);
    box(upper, 70, 22, side < 0 ? -4.5F : -2.5F, -3, -4, 7, 4, 8);
    box(upper, 86, 50, side < 0 ? -4 : -2, -4.5F, -3, 6, 2, 6);
    box(forearm, 70, 50, -2, 0, -2, 4, 5, 4);
    box(forearm, 20, 59, -2.5F, 2.5F, -2.5F, 5, 2, 5);
    box(forearm, 52, 59, -1.5F, 4.5F, -1.5F, 3, 3, 3);
  }

  private static void leg(ModelRenderer thigh, ModelRenderer shin) {
    thigh.mirror = shin.mirror = thigh.rotationPointX > 0;
    box(thigh, 100, 22, -2.5F, 0, -2.5F, 5, 7, 5);
    box(thigh, 110, 50, -2.5F, -1, -3.5F, 5, 7, 1);
    box(shin, 44, 38, -2.5F, 0, -2.5F, 5, 6, 5);
    box(shin, 88, 59, -1.5F, -1.5F, -3.5F, 3, 3, 2);
    box(shin, 30, 50, -2.5F, 6, -4, 5, 2, 7);
  }

  @Override
  public void render(
      Entity entity, float limb, float amount, float age, float yaw, float pitch, float scale) {
    Warden boss = (Warden) entity;
    float partialTick = age - entity.ticksExisted;
    animate(boss, limb, amount, partialTick);
    GL11.glPushMatrix();
    try {
      GL11.glTranslatef(0, pose.lift * scale, 0);
      GL11.glRotatef(pose.spin * 180 / PI, 0, 1, 0);
      // The hurt and death overlays redraw the model untextured in their own colour.
      boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
      draw(scale);
      if (textured) {
        glow(boss, scale, partialTick);
      }
    } finally {
      GL11.glPopMatrix();
    }
  }

  private void draw(float scale) {
    hips.render(scale);
    torso.render(scale);
    rightThigh.render(scale);
    leftThigh.render(scale);
  }

  /** Full-bright additive passes: embers by phase, shield runes by guard, cracks by posture. */
  private void glow(Warden boss, float scale, float partialTick) {
    int phase = boss.getPhase();
    float time = boss.ticksExisted + partialTick;
    float flare = boss.getGuardFlash(partialTick);
    Minecraft minecraft = Minecraft.getMinecraft();
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    float lightX = OpenGlHelper.lastBrightnessX;
    float lightY = OpenGlHelper.lastBrightnessY;
    try {
      GL11.glEnable(GL11.GL_BLEND);
      GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
      GL11.glDisable(GL11.GL_LIGHTING);
      GL11.glDepthMask(false);
      GL11.glDepthFunc(GL11.GL_LEQUAL);
      OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
      // The core breathes; it blazes while the guard is down, so the opening reads at a glance.
      float breath = 0.85F + 0.15F * (float) Math.sin(time * 0.12);
      float open = boss.isExposed() && phase < 3 ? 1.35F : 1;
      tint(EMBER_TINT[phase - 1], EMBER_GLOW[phase - 1] * breath * open);
      minecraft.renderEngine.bindTexture(phase == 3 ? RAGE : EMBERS);
      draw(scale);
      if (phase < 3) {
        float guard = boss.isExposed() || boss.isWarded() ? 0.08F : 0.55F;
        guard += 0.12F * (float) Math.sin(time * 0.2);
        float[] rune = flare > 0 ? mix(RUNE_TINT, FLARE_TINT, flare) : RUNE_TINT;
        tint(rune, Math.max(guard, flare * 1.6F));
        minecraft.renderEngine.bindTexture(RUNES);
        draw(scale);
      }
      float crack = crack(boss, partialTick);
      if (crack > 0) {
        // Crack pixels store when they appear: a higher posture reveals more of the pattern.
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 1 - Math.min(1, crack));
        tint(CRACK_TINT, crack > 1 ? crack : 0.8F);
        minecraft.renderEngine.bindTexture(CRACKS);
        draw(scale);
      }
    } finally {
      OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
      GL11.glPopAttrib();
      minecraft.renderEngine.bindTexture(boss.getTexture());
    }
  }

  /** Posture as a share of its limit; above 1 while staggered. */
  private static float crack(Warden boss, float partialTick) {
    if (boss.getShownAction() == Warden.Action.STAGGER) {
      float age = boss.getActionAge() + partialTick;
      return 1 + BROKEN_BLAZE * Math.max(0, 1 - age / Warden.STAGGER_TICKS);
    }
    return boss.getPosture() / (float) Warden.POSTURE_LIMIT;
  }

  private static void tint(float[] rgb, float intensity) {
    GL11.glColor4f(rgb[0] * intensity, rgb[1] * intensity, rgb[2] * intensity, 1);
  }

  private static float[] mix(float[] a, float[] b, float t) {
    return new float[] {
      a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t
    };
  }

  /** Poses the rig. Package-private so tools can pose the model without rendering it. */
  void animate(Warden boss, float limb, float amount, float partialTick) {
    Warden.Action action = boss.getShownAction();
    float age = boss.getActionAge() + partialTick;
    float now = boss.ticksExisted + partialTick;
    if (boss.entityId != shownEntity || action != shownAction) {
      // Strikes flow into their designed follow-through; anything else crossfades.
      blending = shownAction != null && boss.entityId == shownEntity && !flowsInto(shownAction);
      from.copy(pose);
      blendStart = now;
      shownEntity = boss.entityId;
      shownAction = action;
    }
    WardenPoses.pose(to, boss, action, age, partialTick);
    WardenPoses.walk(to, limb, amount, boss.getShownAction());
    WardenPoses.breathe(to, now, boss.getPhase());
    WardenPoses.recoil(
        to, boss.hurtTime > 0 ? (boss.hurtTime - partialTick) / boss.maxHurtTime : 0);
    float blend = blending ? Math.min(1, (now - blendStart) / BLEND_TICKS) : 1;
    pose.copy(to);
    if (blend < 1) {
      pose.blend(from, to, blend * blend * (3 - 2 * blend));
    }
    plant(pose);
    apply(pose);
    shield.showModel = boss.getPhase() < 3;
  }

  private static boolean flowsInto(Warden.Action previous) {
    return Warden.isStrike(previous) || previous == Warden.Action.AIRBORNE;
  }

  /** Lowers the body so the lowest foot, or a kneeling knee, rests on the ground. */
  private static void plant(Pose pose) {
    float reach =
        Math.max(reach(pose.rightHip, pose.rightKnee), reach(pose.leftHip, pose.leftKnee));
    pose.lift += LEG - reach;
  }

  private static float reach(float hip, float knee) {
    float thigh = THIGH * (float) Math.cos(hip);
    return Math.max(thigh + SHIN * (float) Math.cos(hip + knee), thigh + KNEE_PAD);
  }

  private void apply(Pose p) {
    hips.rotateAngleX = p.waistPitch * 0.3F;
    hips.rotateAngleY = p.waistYaw * 0.3F;
    torso.rotateAngleX = p.waistPitch;
    torso.rotateAngleY = p.waistYaw;
    torso.rotateAngleZ = p.waistRoll;
    head.rotateAngleX = p.headPitch;
    head.rotateAngleY = p.headYaw;
    cape.rotateAngleX = Math.max(0.05F, p.cape - p.waistPitch * 0.8F);
    swordArm.rotateAngleX = p.swordPitch;
    swordArm.rotateAngleY = p.swordYaw;
    swordArm.rotateAngleZ = p.swordRoll;
    swordForearm.rotateAngleX = p.swordElbow;
    sword.rotateAngleX = p.swordWrist;
    sword.rotateAngleZ = p.swordTwist;
    shieldArm.rotateAngleX = p.shieldPitch;
    shieldArm.rotateAngleY = p.shieldYaw;
    shieldArm.rotateAngleZ = p.shieldRoll;
    shieldForearm.rotateAngleX = p.shieldElbow;
    orientShield(p);
    rightThigh.rotateAngleX = p.rightHip;
    rightThigh.rotateAngleZ = p.rightSplay;
    rightShin.rotateAngleX = p.rightKnee;
    leftThigh.rotateAngleX = p.leftHip;
    leftThigh.rotateAngleZ = p.leftSplay;
    leftShin.rotateAngleX = p.leftKnee;
  }

  /**
   * Holds the shield upright and facing forward in the torso's frame, wherever the arm carries it,
   * unless the pose turns or tilts it. This undoes the arm's rotations exactly, then converts the
   * result into the Z, Y, X rotation order that model parts apply.
   */
  private void orientShield(Pose p) {
    float[] m = rotation(0, -p.shieldPitch - p.shieldElbow);
    m = multiply(m, rotation(1, -p.shieldYaw));
    m = multiply(m, rotation(2, -p.shieldRoll));
    m = multiply(m, rotation(1, p.shieldTurn));
    m = multiply(m, rotation(0, p.shieldTilt - p.waistPitch));
    shield.rotateAngleY = (float) Math.asin(Math.max(-1, Math.min(1, -m[6])));
    shield.rotateAngleX = (float) Math.atan2(m[7], m[8]);
    shield.rotateAngleZ = (float) Math.atan2(m[3], m[0]);
  }

  /** A 3x3 row-major rotation about the X (0), Y (1) or Z (2) axis. */
  private static float[] rotation(int axis, float angle) {
    float c = (float) Math.cos(angle);
    float s = (float) Math.sin(angle);
    if (axis == 0) {
      return new float[] {1, 0, 0, 0, c, -s, 0, s, c};
    }
    if (axis == 1) {
      return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
    }
    return new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
  }

  private static float[] multiply(float[] a, float[] b) {
    float[] m = new float[9];
    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 3; col++) {
        m[row * 3 + col] =
            a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col];
      }
    }
    return m;
  }

  /** Every joint angle of one frame, in radians; lift is in model units, positive downward. */
  static final class Pose {
    float lift;
    float spin;
    float waistPitch;
    float waistYaw;
    float waistRoll;
    float headPitch;
    float headYaw;
    float cape;
    float swordPitch;
    float swordYaw;
    float swordRoll;
    float swordElbow;
    float swordWrist;
    float swordTwist;
    float shieldPitch;
    float shieldYaw;
    float shieldRoll;
    float shieldElbow;
    float shieldTilt;
    float shieldTurn;
    float rightHip;
    float rightKnee;
    float rightSplay;
    float leftHip;
    float leftKnee;
    float leftSplay;

    void copy(Pose o) {
      blend(o, o, 0);
    }

    void blend(Pose a, Pose b, float t) {
      lift = mix(a.lift, b.lift, t);
      spin = mix(a.spin, b.spin, t);
      waistPitch = mix(a.waistPitch, b.waistPitch, t);
      waistYaw = mix(a.waistYaw, b.waistYaw, t);
      waistRoll = mix(a.waistRoll, b.waistRoll, t);
      headPitch = mix(a.headPitch, b.headPitch, t);
      headYaw = mix(a.headYaw, b.headYaw, t);
      cape = mix(a.cape, b.cape, t);
      swordPitch = mix(a.swordPitch, b.swordPitch, t);
      swordYaw = mix(a.swordYaw, b.swordYaw, t);
      swordRoll = mix(a.swordRoll, b.swordRoll, t);
      swordElbow = mix(a.swordElbow, b.swordElbow, t);
      swordWrist = mix(a.swordWrist, b.swordWrist, t);
      swordTwist = mix(a.swordTwist, b.swordTwist, t);
      shieldPitch = mix(a.shieldPitch, b.shieldPitch, t);
      shieldYaw = mix(a.shieldYaw, b.shieldYaw, t);
      shieldRoll = mix(a.shieldRoll, b.shieldRoll, t);
      shieldElbow = mix(a.shieldElbow, b.shieldElbow, t);
      shieldTilt = mix(a.shieldTilt, b.shieldTilt, t);
      shieldTurn = mix(a.shieldTurn, b.shieldTurn, t);
      rightHip = mix(a.rightHip, b.rightHip, t);
      rightKnee = mix(a.rightKnee, b.rightKnee, t);
      rightSplay = mix(a.rightSplay, b.rightSplay, t);
      leftHip = mix(a.leftHip, b.leftHip, t);
      leftKnee = mix(a.leftKnee, b.leftKnee, t);
      leftSplay = mix(a.leftSplay, b.leftSplay, t);
    }

    private static float mix(float a, float b, float t) {
      return a + (b - a) * t;
    }
  }
}
