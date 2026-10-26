package dev.hexxitlab.sovereign;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * A hooded, faceless sovereign held together by hex magic. Its gauntlets, sceptre, crown, halo and
 * shards float free of the body, so every state can be told apart by where they are. Each frame
 * poses the rig from the synchronized combat state; glowing details are additive passes over the
 * base texture, tinted by what it is doing.
 */
final class SovereignModel extends ModelBase {
  private static final String GLOW = "/mods/hexxitbosslab/textures/entity/sovereign_glow.png";
  private static final String HEART = "/mods/hexxitbosslab/textures/entity/sovereign_heart.png";
  private static final String FISSURES =
      "/mods/hexxitbosslab/textures/entity/sovereign_fissures.png";
  private static final int TEXTURE_SIZE = 128;
  private static final float PI = (float) Math.PI;
  // The body pivots on its core, which sits at the Sovereign's centre height.
  private static final float CORE_Y = 7;
  // The crown floats this far above the head's pivot; the halo turns behind the head.
  private static final float CROWN_FLOAT = 12;
  private static final float HALO_Y = -15;
  private static final float HALO_Z = 6.5F;
  private static final float HALO_RADIUS = 9;
  private static final float INNER_RADIUS = 5.5F;
  private static final int HALO_PLATES = 12;
  private static final int INNER_PLATES = 8;
  // The sceptre's orb, along its shaft from the grip.
  private static final float ORB = -15;
  private static final float BLEND_TICKS = 3;
  private static final float FULL_BRIGHT = 240;
  private static final float[] HEART_TINT = {1, 0.86F, 1};
  // Fissures start once a quarter of its health is gone and cover it near death.
  private static final float FISSURE_START = 0.25F;
  private static final float FISSURE_SPAN = 0.7F;
  private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

  private final ModelRenderer body = part(0, CORE_Y, 0);
  private final ModelRenderer rightPlate = part(-5, -0.5F, -5);
  private final ModelRenderer leftPlate = part(5, -0.5F, -5);
  private final ModelRenderer rightPauldron = part(-9.5F, -8, 0);
  private final ModelRenderer leftPauldron = part(9.5F, -8, 0);
  private final ModelRenderer head = part(0, -11, 0);
  private final ModelRenderer rightMask = part(-3.5F, -4, -3.5F);
  private final ModelRenderer leftMask = part(3.5F, -4, -3.5F);
  private final ModelRenderer rightHand = part(0, 0, 0);
  private final ModelRenderer leftHand = part(0, 0, 0);
  private final ModelRenderer sceptre = part(0, 0, 0);
  private final ModelRenderer sceptreSpin = part(0, 0, 0);
  private final ModelRenderer robe1 = part(0, 6, 0);
  private final ModelRenderer robe2 = part(0, 4, 0);
  private final ModelRenderer robe3 = part(0, 4, 0);
  private final ModelRenderer robe4 = part(0, 4, 0);
  private final ModelRenderer crownSegment = part(0, 0, 0);
  private final ModelRenderer haloPlate = part(0, 0, 0);
  private final ModelRenderer haloSpike = part(0, 0, 0);
  private final ModelRenderer shard = part(0, 0, 0);
  private final Pose pose = new Pose();
  private final Pose from = new Pose();
  private final Pose to = new Pose();
  private int shownEntity = -1;
  private Sovereign.Action shownAction;
  private float blendStart;
  private boolean blending;
  // What the current frame draws, set when it is posed.
  private int phase = 1;
  private float time;

  SovereignModel() {
    textureWidth = textureHeight = TEXTURE_SIZE;
    box(body, 4, 0, -6, -6, -4, 12, 8, 8);
    box(body, 34, 18, -7, -9, -4.5F, 14, 3, 9);
    box(body, 0, 44, -4.5F, 4, -3.5F, 9, 2, 7);
    box(body, 70, 32, -5, 2, -4, 10, 2, 8);
    box(body, 0, 53, -3, -11, -3, 6, 2, 6);
    box(body, 0, 61, -2, -3, -5, 4, 5, 1);
    plate(rightPlate, -1);
    plate(leftPlate, 1);
    pauldron(rightPauldron, -1);
    pauldron(leftPauldron, 1);
    box(head, 76, 0, -3.5F, -8, -3.5F, 7, 8, 7);
    box(head, 44, 0, -4.5F, -9, -2.5F, 9, 9, 7);
    mask(rightMask, -1);
    mask(leftMask, 1);
    hand(rightHand, -1);
    hand(leftHand, 1);
    box(sceptreSpin, 0, 0, -0.5F, -12, -0.5F, 1, 17, 1);
    box(sceptreSpin, 48, 61, -1, 5, -1, 2, 2, 2);
    box(sceptreSpin, 88, 61, -1, -13, -1, 2, 1, 2);
    box(sceptreSpin, 102, 53, -1.5F, -17, -1.5F, 3, 4, 3);
    box(sceptreSpin, 64, 61, -3, -16, -0.5F, 6, 2, 1);
    box(sceptreSpin, 42, 53, -0.5F, -16, -3, 1, 2, 6);
    box(sceptreSpin, 56, 61, -0.5F, -20, -0.5F, 1, 3, 1);
    box(robe1, 80, 18, -5.5F, 0, -4, 11, 4, 8);
    box(robe2, 38, 32, -4.5F, 0, -3.5F, 9, 4, 7);
    box(robe3, 32, 44, -3.5F, 0, -2.5F, 7, 4, 5);
    box(robe4, 88, 53, -2, 0, -1.5F, 4, 4, 3);
    box(crownSegment, 28, 61, -5, -3, -5, 9, 3, 1);
    box(crownSegment, 60, 61, -5, -6, -5, 1, 3, 1);
    box(crownSegment, 10, 61, -1, -8, -5, 2, 5, 1);
    box(crownSegment, 102, 61, -3, -5, -5, 1, 2, 1);
    box(crownSegment, 102, 61, 2, -5, -5, 1, 2, 1);
    box(crownSegment, 96, 61, -1, -6, -6, 2, 2, 1);
    box(haloPlate, 78, 61, -2, -1, -0.5F, 4, 2, 1);
    box(haloSpike, 16, 61, -0.5F, -5, -0.5F, 1, 4, 1);
    box(shard, 56, 53, -1, -3, -1, 2, 6, 2);
    box(shard, 20, 61, -0.5F, -7, -0.5F, 1, 4, 1);
    box(shard, 20, 61, -0.5F, 3, -0.5F, 1, 4, 1);
    body.addChild(rightPlate);
    body.addChild(leftPlate);
    body.addChild(rightPauldron);
    body.addChild(leftPauldron);
    body.addChild(head);
    head.addChild(rightMask);
    head.addChild(leftMask);
    body.addChild(rightHand);
    body.addChild(leftHand);
    body.addChild(sceptre);
    sceptre.addChild(sceptreSpin);
    body.addChild(robe1);
    robe1.addChild(robe2);
    robe2.addChild(robe3);
    robe3.addChild(robe4);
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

  /** A chest plate hinged at its outer edge; side is -1 for the right one. */
  private static void plate(ModelRenderer plate, int side) {
    plate.mirror = side > 0;
    box(plate, 100, 44, side < 0 ? 0 : -4, -4, -1, 4, 8, 1);
  }

  private static void pauldron(ModelRenderer pauldron, int side) {
    pauldron.mirror = side > 0;
    box(pauldron, 0, 18, -3.5F, -2, -5, 7, 4, 10);
    box(pauldron, 56, 44, -2.5F, -4, -3.5F, 5, 2, 7);
    box(pauldron, 114, 53, -1, -9, -1, 2, 5, 2);
    box(pauldron, 0, 32, -4, 1.5F, -5.5F, 8, 1, 11);
  }

  /** One half of the porcelain mask, hinged at its outer edge. */
  private static void mask(ModelRenderer mask, int side) {
    mask.mirror = side > 0;
    box(mask, 110, 44, side < 0 ? 0 : -3, -4, -1, 3, 8, 1);
  }

  /** A floating gauntlet: cuff above, clawed hand below. */
  private static void hand(ModelRenderer hand, int side) {
    hand.mirror = side > 0;
    box(hand, 80, 44, -2.5F, -5, -2.5F, 5, 4, 5);
    box(hand, 64, 53, -3, -6, -3, 6, 1, 6);
    box(hand, 24, 53, -2, -1, -2.5F, 4, 3, 5);
    box(hand, 24, 61, -2, 2, -2.5F, 1, 4, 1);
    box(hand, 24, 61, -0.5F, 2, -2.5F, 1, 4, 1);
    box(hand, 24, 61, 1, 2, -2.5F, 1, 4, 1);
  }

  @Override
  public void render(
      Entity entity, float limb, float amount, float age, float yaw, float pitch, float scale) {
    Sovereign boss = (Sovereign) entity;
    float partialTick = age - entity.ticksExisted;
    animate(boss, partialTick);
    GL11.glPushMatrix();
    try {
      GL11.glTranslatef(0, pose.lift * scale, 0);
      GL11.glRotatef(pose.turn * 180 / PI, 0, 1, 0);
      // The hurt and death overlays redraw the model untextured in their own colour.
      boolean textured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
      draw(scale);
      if (textured) {
        glow(boss, scale);
      }
    } finally {
      GL11.glPopMatrix();
    }
  }

  private void draw(float scale) {
    body.render(scale);
    GL11.glPushMatrix();
    try {
      body.postRender(scale);
      crown(scale);
      halo(scale);
      shards(scale);
    } finally {
      GL11.glPopMatrix();
    }
  }

  /** Four crown segments around the head: they part when it cracks, and fly apart on release. */
  private void crown(float scale) {
    GL11.glPushMatrix();
    try {
      head.postRender(scale);
      GL11.glTranslatef(0, -(CROWN_FLOAT + pose.crownLift) * scale, 0);
      GL11.glRotatef(pose.crownSpin * 180 / PI, 0, 1, 0);
      GL11.glRotatef(pose.crownTilt * 180 / PI, 1, 0, 0);
      for (int i = 0; i < 4; i++) {
        GL11.glPushMatrix();
        GL11.glRotatef(i * 90, 0, 1, 0);
        GL11.glTranslatef(0, 0, -pose.crownSpread * scale);
        if (phase == 3) {
          // Broken: each piece drifts on its own.
          GL11.glTranslatef(0, (float) Math.sin(time * 0.07 + i * 1.7) * scale, 0);
          GL11.glRotatef(8 * (float) Math.sin(time * 0.05 + i), 1, 0, 0);
        }
        crownSegment.render(scale);
        GL11.glPopMatrix();
      }
    } finally {
      GL11.glPopMatrix();
    }
  }

  /** A dark sun-wheel of rune tablets behind the head; a second ring turns inside it later on. */
  private void halo(float scale) {
    GL11.glPushMatrix();
    try {
      GL11.glTranslatef(0, HALO_Y * scale, HALO_Z * scale);
      GL11.glRotatef(pose.haloTilt * 180 / PI, 1, 0, 0);
      for (int i = 0; i < HALO_PLATES; i++) {
        // Phase three breaks the wheel: some tablets are gone, the rest shiver.
        if (phase == 3 && i % 4 == 1) {
          continue;
        }
        float shiver = phase == 3 ? 0.6F * (float) Math.sin(time * 0.9 + i * 2.1) : 0;
        GL11.glPushMatrix();
        GL11.glRotatef(pose.haloSpin * 180 / PI + i * 360F / HALO_PLATES, 0, 0, 1);
        GL11.glTranslatef(0, -(HALO_RADIUS * pose.haloScale + shiver) * scale, 0);
        haloPlate.render(scale);
        if (i % 2 == 0) {
          haloSpike.render(scale);
        }
        GL11.glPopMatrix();
      }
      if (phase >= 2) {
        for (int i = 0; i < INNER_PLATES; i++) {
          GL11.glPushMatrix();
          GL11.glRotatef(-pose.haloSpin * 270 / PI + i * 360F / INNER_PLATES, 0, 0, 1);
          GL11.glTranslatef(0, -INNER_RADIUS * pose.haloScale * scale, 0.5F * scale);
          haloPlate.render(scale);
          GL11.glPopMatrix();
        }
      }
    } finally {
      GL11.glPopMatrix();
    }
  }

  /**
   * Crystal shards in a ring about an axis through the core: upright, the ring orbits the body;
   * turned forward, it rings the line of an attack.
   */
  private void shards(float scale) {
    int count = 2 + phase;
    GL11.glPushMatrix();
    try {
      GL11.glRotatef(pose.shardAxis * 180 / PI, 1, 0, 0);
      GL11.glTranslatef(0, -pose.shardAlong * scale, 0);
      for (int i = 0; i < count; i++) {
        GL11.glPushMatrix();
        GL11.glRotatef(pose.shardSpin * 180 / PI + i * 360F / count, 0, 1, 0);
        GL11.glTranslatef(
            pose.shardRadius * scale, (float) Math.sin(time * 0.11 + i * 2.3) * 0.8F * scale, 0);
        GL11.glRotatef(pose.shardTilt * 180 / PI, 0, 0, 1);
        shard.render(scale);
        GL11.glPopMatrix();
      }
    } finally {
      GL11.glPopMatrix();
    }
  }

  /**
   * Full-bright additive passes: hex runes tinted by state, the heart, and fissures that spread as
   * it loses health.
   */
  private void glow(Sovereign boss, float scale) {
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
      GL11.glColor4f(pose.red * pose.glow, pose.green * pose.glow, pose.blue * pose.glow, 1);
      minecraft.renderEngine.bindTexture(GLOW);
      draw(scale);
      GL11.glColor4f(
          HEART_TINT[0] * pose.heart, HEART_TINT[1] * pose.heart, HEART_TINT[2] * pose.heart, 1);
      minecraft.renderEngine.bindTexture(HEART);
      draw(scale);
      float lost = 1 - boss.getHealth() / (float) Sovereign.MAX_HEALTH;
      float reveal = Math.min(1, (lost - FISSURE_START) / FISSURE_SPAN);
      if (reveal > 0) {
        // Fissure pixels store when they open: a lower threshold reveals more of the pattern.
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 1 - reveal);
        float bright = 0.5F + 0.5F * pose.glow;
        GL11.glColor4f(pose.red * bright, pose.green * bright, pose.blue * bright, 1);
        minecraft.renderEngine.bindTexture(FISSURES);
        draw(scale);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
      }
      flares(scale);
    } finally {
      OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
      GL11.glPopAttrib();
      minecraft.renderEngine.bindTexture(boss.getTexture());
    }
  }

  /** Soft glows facing the camera: the sceptre's orb, and the bared core. */
  private void flares(float scale) {
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glPushMatrix();
    try {
      body.postRender(scale);
      if (pose.plates > 0.2F) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0, -0.5F * scale, -5.5F * scale);
        flare(0.5F + 0.4F * pose.plates, HEART_TINT, 0.35F * pose.heart * pose.plates);
        GL11.glPopMatrix();
      }
      sceptre.postRender(scale);
      GL11.glTranslatef(0, ORB * scale, 0);
      flare(0.35F + 0.5F * pose.orb, new float[] {pose.red, pose.green, pose.blue}, pose.orb);
    } finally {
      GL11.glPopMatrix();
    }
  }

  /** A radial glow of the given radius in blocks, turned to face the camera. */
  private static void flare(float radius, float[] rgb, float alpha) {
    if (alpha <= 0.01F) {
      return;
    }
    GL11.glPushMatrix();
    try {
      // Keep the position but drop every rotation and scale, so the disc faces the eye.
      MATRIX.clear();
      GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
      float[] m = new float[16];
      MATRIX.get(m);
      for (int i = 0; i < 12; i++) {
        m[i] = i % 5 == 0 ? 1 : 0;
      }
      MATRIX.clear();
      MATRIX.put(m).flip();
      GL11.glLoadMatrix(MATRIX);
      GL11.glBegin(GL11.GL_TRIANGLE_FAN);
      GL11.glColor4f(rgb[0] * alpha, rgb[1] * alpha, rgb[2] * alpha, 1);
      GL11.glVertex3f(0, 0, 0);
      GL11.glColor4f(0, 0, 0, 1);
      for (int i = 0; i <= 20; i++) {
        double angle = i * Math.PI * 2 / 20;
        GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
      }
      GL11.glEnd();
    } finally {
      GL11.glPopMatrix();
    }
  }

  /** Poses the rig. Package-private so tools can pose the model without rendering it. */
  void animate(Sovereign boss, float partialTick) {
    Sovereign.Action action = boss.getShownAction();
    float age = boss.getActionAge() + partialTick;
    float now = boss.ticksExisted + partialTick;
    phase = boss.getPhase();
    time = now;
    if (boss.entityId != shownEntity || action != shownAction) {
      // A release flows on from its attack's peak; anything else crossfades.
      blending =
          shownAction != null
              && boss.entityId == shownEntity
              && !(action == Sovereign.Action.RECOVER && SovereignPoses.releases(shownAction));
      from.copy(pose);
      blendStart = now;
      shownEntity = boss.entityId;
      shownAction = action;
    }
    SovereignPoses.pose(to, boss, action, age, partialTick, now);
    aim(to, boss, partialTick);
    float blend = blending ? Math.min(1, (now - blendStart) / BLEND_TICKS) : 1;
    pose.copy(to);
    if (blend < 1) {
      pose.blend(from, to, blend * blend * (3 - 2 * blend));
    }
    apply(pose);
  }

  /** Turns and tilts the body towards the locked aim, by the pose's aim weight. */
  private static void aim(Pose p, Sovereign boss, float partialTick) {
    if (p.aim <= 0) {
      return;
    }
    Vec3 aim = boss.getAim();
    double horizontal = Math.sqrt(aim.xCoord * aim.xCoord + aim.zCoord * aim.zCoord);
    if (horizontal + Math.abs(aim.yCoord) < 1e-3) {
      return;
    }
    float aimYaw = (float) Math.toDegrees(Math.atan2(-aim.xCoord, aim.zCoord));
    float bodyYaw =
        boss.prevRenderYawOffset + (boss.renderYawOffset - boss.prevRenderYawOffset) * partialTick;
    p.turn = p.aim * MathHelper.wrapAngleTo180_float(aimYaw - bodyYaw) * PI / 180;
    float rise = (float) Math.atan2(aim.yCoord, horizontal);
    p.pitch -= p.aim * Math.max(-1.2F, Math.min(1.2F, rise));
  }

  private void apply(Pose p) {
    body.rotateAngleX = p.pitch;
    body.rotateAngleY = p.twist;
    body.rotateAngleZ = p.roll;
    head.rotateAngleX = p.headPitch;
    head.rotateAngleY = p.headYaw;
    head.rotateAngleZ = p.headRoll;
    rightMask.rotateAngleY = 0.7F * p.mask;
    leftMask.rotateAngleY = -0.7F * p.mask;
    rightPlate.rotateAngleY = 1.3F * p.plates;
    leftPlate.rotateAngleY = -1.3F * p.plates;
    rightPauldron.rotateAngleZ = 0.15F + p.shrug;
    leftPauldron.rotateAngleZ = -0.15F - p.shrug;
    place(rightHand, p.right);
    place(leftHand, p.left);
    place(sceptre, p.sceptre);
    sceptreSpin.rotateAngleY = p.sceptreSpin;
    // The robe trails like a tail: each segment lags the one above it.
    ModelRenderer[] robe = {robe1, robe2, robe3, robe4};
    for (int i = 0; i < robe.length; i++) {
      float wave = p.sway * 0.07F * (float) Math.sin(time * 0.12 - i * 0.9);
      robe[i].rotateAngleX = p.robe * (0.2F + 0.07F * i) + wave;
      robe[i].rotateAngleZ = p.robeRoll * (0.5F + 0.2F * i) + wave * 0.5F;
    }
  }

  private static void place(ModelRenderer part, Placement at) {
    part.setRotationPoint(at.x, at.y, at.z);
    part.rotateAngleX = at.pitch;
    part.rotateAngleY = at.yaw;
    part.rotateAngleZ = at.roll;
  }

  /** Where a floating piece is, relative to the core, and how it is turned. */
  static final class Placement {
    float x;
    float y;
    float z;
    float pitch;
    float yaw;
    float roll;

    void set(float x, float y, float z, float pitch, float yaw, float roll) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.pitch = pitch;
      this.yaw = yaw;
      this.roll = roll;
    }

    /** This placement mirrored across the body's centre line. */
    void mirror(Placement o) {
      set(-o.x, o.y, o.z, o.pitch, -o.yaw, -o.roll);
    }

    void blend(Placement a, Placement b, float t) {
      set(
          mix(a.x, b.x, t),
          mix(a.y, b.y, t),
          mix(a.z, b.z, t),
          mix(a.pitch, b.pitch, t),
          mix(a.yaw, b.yaw, t),
          mix(a.roll, b.roll, t));
    }
  }

  /**
   * Every value of one frame. Angles are radians; positions are model units relative to the core,
   * with y downward.
   */
  static final class Pose {
    float lift;
    float turn;
    float aim;
    float pitch;
    float roll;
    float twist;
    float headPitch;
    float headYaw;
    float headRoll;
    float mask;
    float plates;
    float shrug;
    float crownLift;
    float crownSpin;
    float crownTilt;
    float crownSpread;
    float haloSpin;
    float haloScale;
    float haloTilt;
    float shardAxis;
    float shardAlong;
    float shardRadius;
    float shardSpin;
    float shardTilt;
    float robe;
    float robeRoll;
    float sway;
    float sceptreSpin;
    float glow;
    float red;
    float green;
    float blue;
    float heart;
    float orb;
    final Placement right = new Placement();
    final Placement left = new Placement();
    final Placement sceptre = new Placement();

    void copy(Pose o) {
      blend(o, o, 0);
    }

    void blend(Pose a, Pose b, float t) {
      lift = mix(a.lift, b.lift, t);
      turn = mix(a.turn, b.turn, t);
      aim = mix(a.aim, b.aim, t);
      pitch = mix(a.pitch, b.pitch, t);
      roll = mix(a.roll, b.roll, t);
      twist = mix(a.twist, b.twist, t);
      headPitch = mix(a.headPitch, b.headPitch, t);
      headYaw = mix(a.headYaw, b.headYaw, t);
      headRoll = mix(a.headRoll, b.headRoll, t);
      mask = mix(a.mask, b.mask, t);
      plates = mix(a.plates, b.plates, t);
      shrug = mix(a.shrug, b.shrug, t);
      crownLift = mix(a.crownLift, b.crownLift, t);
      crownSpin = mix(a.crownSpin, b.crownSpin, t);
      crownTilt = mix(a.crownTilt, b.crownTilt, t);
      crownSpread = mix(a.crownSpread, b.crownSpread, t);
      haloSpin = mix(a.haloSpin, b.haloSpin, t);
      haloScale = mix(a.haloScale, b.haloScale, t);
      haloTilt = mix(a.haloTilt, b.haloTilt, t);
      shardAxis = mix(a.shardAxis, b.shardAxis, t);
      shardAlong = mix(a.shardAlong, b.shardAlong, t);
      shardRadius = mix(a.shardRadius, b.shardRadius, t);
      shardSpin = mix(a.shardSpin, b.shardSpin, t);
      shardTilt = mix(a.shardTilt, b.shardTilt, t);
      robe = mix(a.robe, b.robe, t);
      robeRoll = mix(a.robeRoll, b.robeRoll, t);
      sway = mix(a.sway, b.sway, t);
      sceptreSpin = mix(a.sceptreSpin, b.sceptreSpin, t);
      glow = mix(a.glow, b.glow, t);
      red = mix(a.red, b.red, t);
      green = mix(a.green, b.green, t);
      blue = mix(a.blue, b.blue, t);
      heart = mix(a.heart, b.heart, t);
      orb = mix(a.orb, b.orb, t);
      right.blend(a.right, b.right, t);
      left.blend(a.left, b.left, t);
      sceptre.blend(a.sceptre, b.sceptre, t);
    }
  }

  private static float mix(float a, float b, float t) {
    return a + (b - a) * t;
  }
}
