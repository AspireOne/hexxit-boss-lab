package dev.hexxitlab.sovereign;

import dev.hexxitlab.BossCombat;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * Hex flourishes inside the Sovereign's warnings, and the light of its attacks, drawn relative to
 * its centre between {@code Telegraphs.begin} and {@code end}. Flourishes lie strictly inside the
 * hit geometry, so they never change what a warning promises; they tell the attacks apart at a
 * glance: rings spiral down a lance's line, chevrons race along a rush's route, and a sigil turns
 * under Crown Break. Each fills with heat behind the warning's own heat front.
 */
final class SovereignWarnings {
  private static final float[] HOT = {1, 0.94F, 1};
  private static final float[] SHADOW = {0.08F, 0, 0.12F};
  private static final double RING_SPACING = 2.5;
  private static final double CHEVRON_SPACING = 2;
  private static final double CHEVRON_DEPTH = 0.9;
  // Flourishes are inset from the warning's edge, which stays the only boundary drawn.
  private static final double INSET = 0.85;
  private static final double SIGIL_INSET = 0.92;
  private static final double SIGIL_INNER = 0.45;
  private static final int SIGIL_TICKS = 12;
  private static final double GROUND_LIFT = 0.05;
  private static final int TUBE_SIDES = 12;
  // A charge's glowing wake reaches back at most this far along its route.
  private static final double WAKE_LENGTH = 14;

  private SovereignWarnings() {}

  /** Hexagonal rings that turn and crawl down the line of a Hex Lance, around a sighting line. */
  static void hexRings(Vec3 aim, double radius, float[] rgb, float progress, float time) {
    double length = aim.lengthVector();
    if (length < 1e-3) {
      return;
    }
    double[][] frame = frame(aim);
    double[] d = frame[0];
    stroke(
        new double[][] {{0, 0, 0}, {d[0] * length, d[1] * length, d[2] * length}},
        false,
        mix(rgb, HOT, progress),
        0.25F + 0.6F * progress,
        1.5F);
    double crawl = time * 0.05 % RING_SPACING;
    for (double s = 1 + crawl; s < length - 0.3; s += RING_SPACING) {
      boolean filled = s / length <= progress;
      double turn = time * 0.06 + s * 0.4;
      double[][] ring = new double[6][];
      for (int k = 0; k < 6; k++) {
        double angle = turn + k * Math.PI / 3;
        ring[k] =
            along(frame, s, Math.cos(angle) * radius * INSET, Math.sin(angle) * radius * INSET);
      }
      stroke(ring, true, filled ? HOT : rgb, filled ? 0.9F : 0.6F, 2.5F);
    }
  }

  /** Chevrons that race along a Ruin Rush's route, crossed so they read from any side. */
  static void chevrons(Vec3 aim, double radius, float[] rgb, float progress, float time) {
    double length = aim.lengthVector();
    if (length < 1e-3) {
      return;
    }
    double[][] frame = frame(aim);
    double march = time * 0.12 % CHEVRON_SPACING;
    double arm = radius * INSET * 0.8;
    for (double s = 0.8 + march; s + CHEVRON_DEPTH < length; s += CHEVRON_SPACING) {
      boolean filled = (s + CHEVRON_DEPTH) / length <= progress;
      float[] tone = filled ? HOT : rgb;
      float alpha = filled ? 0.95F : 0.65F;
      stroke(
          new double[][] {
            along(frame, s, -arm, 0), along(frame, s + CHEVRON_DEPTH, 0, 0), along(frame, s, arm, 0)
          },
          false,
          tone,
          alpha,
          3);
      stroke(
          new double[][] {
            along(frame, s, 0, -arm), along(frame, s + CHEVRON_DEPTH, 0, 0), along(frame, s, 0, arm)
          },
          false,
          tone,
          alpha,
          3);
    }
  }

  /**
   * A turning hexagram where Crown Break's sphere meets the ground, inside the ring where a
   * standing body leaves it.
   */
  static void crownSigil(double radius, double floor, float[] rgb, float progress, float time) {
    double plane = floor - BossCombat.PLAYER_CENTER_HEIGHT;
    if (floor <= 0 || plane >= radius) {
      return;
    }
    double reach = Math.sqrt(radius * radius - plane * plane) * SIGIL_INSET;
    float[] tone = mix(rgb, HOT, progress * progress);
    float alpha = 0.35F + 0.55F * progress;
    GL11.glPushMatrix();
    try {
      GL11.glTranslated(0, -floor + GROUND_LIFT, 0);
      GL11.glRotatef(time * 0.4F, 0, 1, 0);
      for (int triangle = 0; triangle < 2; triangle++) {
        double[][] points = new double[3][];
        for (int k = 0; k < 3; k++) {
          double angle = triangle * Math.PI / 3 + k * Math.PI * 2 / 3;
          points[k] = new double[] {Math.cos(angle) * reach, 0, Math.sin(angle) * reach};
        }
        stroke(points, true, tone, alpha, 3);
      }
      double inner = reach * SIGIL_INNER;
      double[][] circle = new double[48][];
      for (int k = 0; k < circle.length; k++) {
        double angle = k * Math.PI * 2 / circle.length;
        circle[k] = new double[] {Math.cos(angle) * inner, 0, Math.sin(angle) * inner};
      }
      stroke(circle, true, tone, alpha, 2.5F);
      for (int k = 0; k < SIGIL_TICKS; k++) {
        double angle = k * Math.PI * 2 / SIGIL_TICKS;
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        stroke(
            new double[][] {{c * inner, 0, s * inner}, {c * inner * 1.25, 0, s * inner * 1.25}},
            false,
            tone,
            alpha,
            2.5F);
      }
    } finally {
      GL11.glPopMatrix();
    }
  }

  /** The Hex Lance's beam: a white-hot core in a violet sheath, thinning as it fades. */
  static void beam(Vec3 aim, double radius, float[] rgb, float fade) {
    float strength = 1 - fade;
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    tube(
        aim,
        radius * 0.75 * (1 - 0.6 * fade),
        radius * 0.75 * (1 - 0.6 * fade),
        rgb,
        0.5F,
        0.5F,
        strength);
    tube(aim, radius * 0.25, radius * 0.25 * (1 - fade), HOT, 0.9F, 0.9F, strength);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
  }

  /** A glowing wake behind a charge, from the Sovereign back towards where it began. */
  static void wake(Vec3 back, double radius, float[] rgb) {
    double length = back.lengthVector();
    if (length < 0.5) {
      return;
    }
    double reach = Math.min(length, WAKE_LENGTH);
    Vec3 span =
        Vec3.createVectorHelper(
            back.xCoord * reach / length,
            back.yCoord * reach / length,
            back.zCoord * reach / length);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    tube(span, radius * 0.7, radius * 0.15, rgb, 0.45F, 0, 1);
    tube(span, radius * 0.2, 0.05, HOT, 0.6F, 0, 1);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
  }

  /** An open tube from the origin along a span, tapering and fading from start to end. */
  private static void tube(
      Vec3 span,
      double startRadius,
      double endRadius,
      float[] rgb,
      float startAlpha,
      float endAlpha,
      float strength) {
    double length = span.lengthVector();
    if (length < 1e-3) {
      return;
    }
    double[][] frame = frame(span);
    GL11.glBegin(GL11.GL_QUAD_STRIP);
    for (int i = 0; i <= TUBE_SIDES; i++) {
      double angle = i * Math.PI * 2 / TUBE_SIDES;
      double c = Math.cos(angle);
      double s = Math.sin(angle);
      GL11.glColor4f(rgb[0], rgb[1], rgb[2], startAlpha * strength);
      vertex(along(frame, 0, c * startRadius, s * startRadius));
      GL11.glColor4f(rgb[0], rgb[1], rgb[2], endAlpha * strength);
      vertex(along(frame, length, c * endRadius, s * endRadius));
    }
    GL11.glEnd();
  }

  /** Direction, then two unit vectors across it; the first lies flat whenever it can. */
  private static double[][] frame(Vec3 aim) {
    Vec3 direction = aim.normalize();
    Vec3 reference =
        Math.abs(direction.yCoord) > 0.9
            ? Vec3.createVectorHelper(1, 0, 0)
            : Vec3.createVectorHelper(0, 1, 0);
    Vec3 side = direction.crossProduct(reference).normalize();
    Vec3 up = direction.crossProduct(side).normalize();
    return new double[][] {
      {direction.xCoord, direction.yCoord, direction.zCoord},
      {side.xCoord, side.yCoord, side.zCoord},
      {up.xCoord, up.yCoord, up.zCoord}
    };
  }

  /** A point s along the frame's direction, offset a across it flat and b across it upright. */
  private static double[] along(double[][] frame, double s, double a, double b) {
    double[] point = new double[3];
    for (int i = 0; i < 3; i++) {
      point[i] = frame[0][i] * s + frame[1][i] * a + frame[2][i] * b;
    }
    return point;
  }

  /** A dark under-stroke and a bright stroke, like the warnings' own edges. */
  private static void stroke(
      double[][] points, boolean closed, float[] rgb, float alpha, float width) {
    GL11.glLineWidth(width + 2.5F);
    line(points, closed, SHADOW, 0.4F * alpha);
    GL11.glLineWidth(width);
    line(points, closed, rgb, alpha);
  }

  private static void line(double[][] points, boolean closed, float[] rgb, float alpha) {
    GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha);
    GL11.glBegin(closed ? GL11.GL_LINE_LOOP : GL11.GL_LINE_STRIP);
    for (double[] p : points) {
      vertex(p);
    }
    GL11.glEnd();
  }

  private static void vertex(double[] p) {
    GL11.glVertex3d(p[0], p[1], p[2]);
  }

  private static float[] mix(float[] a, float[] b, float t) {
    return new float[] {
      a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t
    };
  }
}
