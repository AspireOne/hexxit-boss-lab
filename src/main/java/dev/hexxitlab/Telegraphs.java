package dev.hexxitlab;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Client-only warning geometry, drawn relative to the current GL origin between {@link #begin} and
 * {@link #end}. Every shape is exactly the strike's hit geometry. A tracking warning is faint with
 * a crawling dashed edge; a locked one is solid, pulses, and fills from its origin to its edge as
 * the strike lands; an impact flashes and fades. Solid geometry keeps warnings visible even with
 * particles disabled, and a dark under-stroke keeps edges legible on snow as well as stone at
 * night.
 */
public final class Telegraphs {
  public enum Style {
    TRACKING,
    LOCKED,
    IMPACT
  }

  private static final int SEGMENTS = 96;
  private static final int RINGS = 10;
  private static final int TUBE_SIDES = 12;
  private static final int DOME_LATITUDES = 18;
  private static final int DOME_LONGITUDES = 36;
  private static final float FULL_BRIGHT = 240;
  private static final float[] SHADOW = {0.1F, 0.02F, 0};
  private static final float[] HOT = {1, 0.92F, 0.65F};
  private static final float DASH = 0.7F;
  private static final float DASH_SPEED = 0.08F;
  private static final float PULSE_RATE = 0.35F;
  // A glowing band inside each ground edge, in blocks.
  private static final double RIM = 0.4;
  private static final double CHAIN_LINK = 0.42;
  private static final double CHAIN_WIDTH = 0.2;
  private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

  private Telegraphs() {}

  /** How a warning looks this frame. Progress runs from 0 to 1: to impact, or through a fade. */
  public static final class Warning {
    final float[] rgb;
    final Style style;
    final float progress;
    final float time;

    public Warning(float[] rgb, Style style, float progress, float time) {
      this.rgb = rgb.clone();
      this.style = style;
      this.progress = Math.max(0, Math.min(1, progress));
      this.time = time;
    }

    float pulse() {
      return 0.75F + 0.25F * (float) Math.abs(Math.sin(time * PULSE_RATE));
    }

    /** Overall strength: faint while tracking, strong when locked, fading after impact. */
    float strength() {
      switch (style) {
        case TRACKING:
          return 0.45F;
        case LOCKED:
          return pulse();
        default:
          return 1 - progress;
      }
    }
  }

  /** Untextured, unlit, full-bright, translucent drawing with depth testing but no depth writes. */
  public static void begin() {
    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
    GL11.glDisable(GL11.GL_TEXTURE_2D);
    GL11.glDisable(GL11.GL_LIGHTING);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glDisable(GL11.GL_ALPHA_TEST);
    GL11.glEnable(GL11.GL_BLEND);
    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    GL11.glDepthMask(false);
    GL11.glEnable(GL11.GL_LINE_SMOOTH);
    GL11.glShadeModel(GL11.GL_SMOOTH);
    // Ground fills sit just above the terrain; pull them forward to avoid flicker at distance.
    GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    GL11.glPolygonOffset(-1, -4);
    OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, FULL_BRIGHT, FULL_BRIGHT);
  }

  public static void end() {
    GL11.glPopAttrib();
  }

  /** Flat wedge on the ground, centred on a Minecraft yaw, with its arc measured to either side. */
  public static void sector(double yaw, double arc, double radius, Warning warning) {
    ground(yaw, arc, radius, true, warning);
  }

  public static void disc(double radius, Warning warning) {
    ground(0, 180, radius, false, warning);
  }

  private static void ground(double yaw, double arc, double radius, boolean wedge, Warning w) {
    int segments = Math.max(8, (int) Math.ceil(SEGMENTS * arc / 180));
    float strength = w.strength();
    float fill = w.style == Style.TRACKING ? 0.16F : w.style == Style.LOCKED ? 0.3F : 0.55F;
    float[] tone = w.style == Style.IMPACT ? HOT : w.rgb;
    // Mottled, edge-weighted fill reads as scorched ground rather than a flat sticker.
    for (int ring = 0; ring < RINGS; ring++) {
      double r0 = radius * ring / RINGS;
      double r1 = radius * (ring + 1) / RINGS;
      GL11.glBegin(GL11.GL_QUAD_STRIP);
      for (int i = 0; i <= segments; i++) {
        double angle = Math.toRadians(yaw - arc + 2 * arc * i / segments);
        groundVertex(angle, r1, radius, i, ring + 1, tone, fill * strength, w);
        groundVertex(angle, r0, radius, i, ring, tone, fill * strength, w);
      }
      GL11.glEnd();
    }
    if (w.style == Style.LOCKED) {
      // The fill races from the origin to the edge: when it arrives, the strike lands.
      double front = radius * w.progress;
      band(yaw, arc, Math.max(0, front - RIM * 2), front, segments, HOT, 0, 0.55F);
      band(yaw, arc, 0, front, segments, HOT, 0.14F, 0.14F);
    }
    if (w.style != Style.TRACKING) {
      band(yaw, arc, radius - RIM, radius, segments, tone, 0, 0.7F * strength);
    }
    edge(outline(yaw, arc, radius, wedge, segments), true, w);
  }

  private static void groundVertex(
      double angle, double r, double radius, int i, int ring, float[] rgb, float alpha, Warning w) {
    float share = (float) (r / radius);
    float shimmer = w.style == Style.LOCKED ? 0.12F * (float) Math.sin(r * 2.5 - w.time * 0.6) : 0;
    float a = alpha * (0.45F + 0.55F * share * share) * (0.75F + 0.5F * noise(i, ring) + shimmer);
    GL11.glColor4f(rgb[0], rgb[1], rgb[2], a);
    GL11.glVertex3d(-Math.sin(angle) * r, 0, Math.cos(angle) * r);
  }

  /** A ring band on the ground between two radii, fading from inner to outer opacity. */
  private static void band(
      double yaw,
      double arc,
      double inner,
      double outer,
      int segments,
      float[] rgb,
      float innerAlpha,
      float outerAlpha) {
    if (outer <= inner) {
      return;
    }
    GL11.glBegin(GL11.GL_QUAD_STRIP);
    for (int i = 0; i <= segments; i++) {
      double angle = Math.toRadians(yaw - arc + 2 * arc * i / segments);
      double x = -Math.sin(angle);
      double z = Math.cos(angle);
      GL11.glColor4f(rgb[0], rgb[1], rgb[2], outerAlpha);
      GL11.glVertex3d(x * outer, 0, z * outer);
      GL11.glColor4f(rgb[0], rgb[1], rgb[2], innerAlpha);
      GL11.glVertex3d(x * inner, 0, z * inner);
    }
    GL11.glEnd();
  }

  private static double[][] outline(
      double yaw, double arc, double radius, boolean wedge, int segments) {
    int count = segments + 1 + (wedge ? 2 : 0);
    double[][] points = new double[count][];
    int n = 0;
    if (wedge) {
      points[n++] = new double[] {0, 0, 0};
    }
    for (int i = 0; i <= segments; i++) {
      double angle = Math.toRadians(yaw - arc + 2 * arc * i / segments);
      points[n++] = new double[] {-Math.sin(angle) * radius, 0, Math.cos(angle) * radius};
    }
    if (wedge) {
      points[n] = new double[] {0, 0, 0};
    }
    return points;
  }

  /**
   * The exact edge: a dark under-stroke and a bright stroke, which crawls in dashes while tracking.
   * Lines keep their pixel width, so edges stay legible from far away and seen edge-on.
   */
  private static void edge(double[][] points, boolean closed, Warning w) {
    boolean dashed = w.style == Style.TRACKING;
    float strength = w.strength();
    float[] bright = brighten(w.style == Style.IMPACT ? HOT : w.rgb);
    GL11.glLineWidth(dashed ? 4 : 7);
    polyline(points, closed, dashed, w.time, SHADOW, 0.5F * strength);
    GL11.glLineWidth(dashed ? 2 : 3.5F);
    polyline(points, closed, dashed, w.time, bright, Math.min(1, 1.25F * strength));
  }

  private static void polyline(
      double[][] points, boolean closed, boolean dashed, float time, float[] rgb, float alpha) {
    GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha);
    if (!dashed) {
      GL11.glBegin(closed ? GL11.GL_LINE_LOOP : GL11.GL_LINE_STRIP);
      for (double[] p : points) {
        GL11.glVertex3d(p[0], p[1], p[2]);
      }
      GL11.glEnd();
      return;
    }
    // Dashes are cut by distance along the outline, shifting with time so they crawl.
    GL11.glBegin(GL11.GL_LINES);
    double travelled = -time * DASH_SPEED * DASH * 2;
    int count = closed ? points.length : points.length - 1;
    for (int i = 0; i < count; i++) {
      double[] a = points[i];
      double[] b = points[(i + 1) % points.length];
      double length = Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2]));
      double s = 0;
      while (s < length) {
        double position = travelled + s;
        double phase = position - Math.floor(position / (DASH * 2)) * DASH * 2;
        double step = Math.min(length - s, (phase < DASH ? DASH : DASH * 2) - phase);
        if (phase < DASH && length > 0) {
          vertex(a, b, s / length);
          vertex(a, b, (s + step) / length);
        }
        s += Math.max(step, 1e-3);
      }
      travelled += length;
    }
    GL11.glEnd();
  }

  private static void vertex(double[] a, double[] b, double t) {
    GL11.glVertex3d(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t);
  }

  /**
   * A straight strike from the origin along aim: a translucent tube of the hit radius, edged along
   * its length and capped where it stops. A chain strike shows its links inside the tube.
   */
  public static void corridor(Vec3 aim, double radius, boolean chain, Warning w) {
    double length = aim.lengthVector();
    if (length < 1e-3) {
      return;
    }
    Vec3 direction = aim.normalize();
    Vec3 reference =
        Math.abs(direction.yCoord) > 0.9
            ? Vec3.createVectorHelper(1, 0, 0)
            : Vec3.createVectorHelper(0, 1, 0);
    Vec3 side = direction.crossProduct(reference).normalize();
    Vec3 up = direction.crossProduct(side).normalize();
    float strength = w.strength();
    float[] tone = w.style == Style.IMPACT ? HOT : w.rgb;
    float[] eye = eyeMatrix();
    int steps = Math.max(2, (int) Math.ceil(length));
    for (int s = 0; s < steps; s++) {
      double t0 = (double) s / steps;
      double t1 = (double) (s + 1) / steps;
      GL11.glBegin(GL11.GL_QUAD_STRIP);
      for (int i = 0; i <= TUBE_SIDES; i++) {
        double angle = i * Math.PI * 2 / TUBE_SIDES;
        double nx = side.xCoord * Math.cos(angle) + up.xCoord * Math.sin(angle);
        double ny = side.yCoord * Math.cos(angle) + up.yCoord * Math.sin(angle);
        double nz = side.zCoord * Math.cos(angle) + up.zCoord * Math.sin(angle);
        tubeVertex(aim, t0, nx, ny, nz, radius, tone, strength, w, eye);
        tubeVertex(aim, t1, nx, ny, nz, radius, tone, strength, w, eye);
      }
      GL11.glEnd();
    }
    for (int i = 0; i < 4; i++) {
      double angle = i * Math.PI / 2;
      double dx = (side.xCoord * Math.cos(angle) + up.xCoord * Math.sin(angle)) * radius;
      double dy = (side.yCoord * Math.cos(angle) + up.yCoord * Math.sin(angle)) * radius;
      double dz = (side.zCoord * Math.cos(angle) + up.zCoord * Math.sin(angle)) * radius;
      edge(
          new double[][] {{dx, dy, dz}, {aim.xCoord + dx, aim.yCoord + dy, aim.zCoord + dz}},
          false,
          w);
    }
    double[][] cap = new double[SEGMENTS / 3][];
    for (int i = 0; i < cap.length; i++) {
      double angle = i * Math.PI * 2 / cap.length;
      cap[i] =
          new double[] {
            aim.xCoord + (side.xCoord * Math.cos(angle) + up.xCoord * Math.sin(angle)) * radius,
            aim.yCoord + (side.yCoord * Math.cos(angle) + up.yCoord * Math.sin(angle)) * radius,
            aim.zCoord + (side.zCoord * Math.cos(angle) + up.zCoord * Math.sin(angle)) * radius
          };
    }
    edge(cap, true, w);
    if (chain) {
      chain(Vec3.createVectorHelper(0, 0, 0), aim, links(w), w.style == Style.TRACKING ? 2 : 3);
    }
  }

  private static void tubeVertex(
      Vec3 aim,
      double t,
      double nx,
      double ny,
      double nz,
      double radius,
      float[] rgb,
      float strength,
      Warning w,
      float[] eye) {
    double x = aim.xCoord * t + nx * radius;
    double y = aim.yCoord * t + ny * radius;
    double z = aim.zCoord * t + nz * radius;
    // Faces seen edge-on are more opaque, so the tube's exact outline reads against the sky.
    float rim = rim(eye, x, y, z, nx, ny, nz);
    float alpha = (0.06F + 0.4F * rim * rim) * strength;
    float[] tone = rgb;
    if (w.style == Style.LOCKED && t <= w.progress) {
      // The tube fills from the origin towards its end as the strike approaches.
      tone = HOT;
      alpha += 0.12F + (w.progress - t < 0.08 ? 0.3F : 0);
    }
    GL11.glColor4f(tone[0], tone[1], tone[2], alpha);
    GL11.glVertex3d(x, y, z);
  }

  /** Chain links from one point to another in the given colour; alternate links are turned. */
  public static void chain(Vec3 from, Vec3 to, float[] rgba, float width) {
    Vec3 span = from.subtract(to);
    double length = span.lengthVector();
    if (length < 1e-3) {
      return;
    }
    Vec3 direction = span.normalize();
    Vec3 reference =
        Math.abs(direction.yCoord) > 0.9
            ? Vec3.createVectorHelper(1, 0, 0)
            : Vec3.createVectorHelper(0, 1, 0);
    Vec3 side = direction.crossProduct(reference).normalize();
    Vec3 up = direction.crossProduct(side).normalize();
    GL11.glLineWidth(width);
    GL11.glColor4f(rgba[0], rgba[1], rgba[2], rgba[3]);
    int count = (int) Math.floor(length / (CHAIN_LINK * 0.75));
    for (int link = 0; link < count; link++) {
      double centre = (link + 0.5) * length / count;
      Vec3 across = link % 2 == 0 ? side : up;
      GL11.glBegin(GL11.GL_LINE_LOOP);
      for (int i = 0; i < 10; i++) {
        double angle = i * Math.PI * 2 / 10;
        double along = centre + Math.cos(angle) * CHAIN_LINK / 2;
        double out = Math.sin(angle) * CHAIN_WIDTH / 2;
        GL11.glVertex3d(
            from.xCoord + direction.xCoord * along + across.xCoord * out,
            from.yCoord + direction.yCoord * along + across.yCoord * out,
            from.zCoord + direction.zCoord * along + across.zCoord * out);
      }
      GL11.glEnd();
    }
  }

  private static float[] links(Warning w) {
    if (w.style == Style.TRACKING) {
      return new float[] {0.7F, 0.66F, 0.6F, 0.35F};
    }
    float[] hot = brighten(w.rgb);
    return new float[] {hot[0], hot[1], hot[2], w.strength()};
  }

  /**
   * A sphere of the given radius around the origin. Its silhouette is the exact blast edge. When
   * floor is positive, the ground lies that far below the centre, and a ring on it marks where a
   * standing player's body leaves the sphere. A locked dome fills from its centre as it detonates.
   */
  public static void dome(double radius, double floor, Warning w) {
    float[] eye = eyeMatrix();
    float strength = w.strength();
    float[] tone = w.style == Style.IMPACT ? HOT : w.rgb;
    shell(radius, tone, strength, w, eye, 1);
    if (w.style == Style.LOCKED && w.progress > 0) {
      shell(radius * w.progress, HOT, strength, w, eye, 0.6F);
    }
    double plane = floor - BossCombat.PLAYER_CENTER_HEIGHT;
    if (floor > 0 && plane < radius) {
      double reach = Math.sqrt(radius * radius - plane * plane);
      GL11.glPushMatrix();
      GL11.glTranslated(0, -floor, 0);
      band(0, 180, Math.max(0, reach - RIM * 2), reach, SEGMENTS, tone, 0, 0.5F * strength);
      edge(outline(0, 180, reach, false, SEGMENTS), true, w);
      GL11.glPopMatrix();
    }
  }

  private static void shell(
      double radius, float[] rgb, float strength, Warning w, float[] eye, float weight) {
    for (int lat = 0; lat < DOME_LATITUDES; lat++) {
      double a0 = Math.PI * lat / DOME_LATITUDES;
      double a1 = Math.PI * (lat + 1) / DOME_LATITUDES;
      GL11.glBegin(GL11.GL_QUAD_STRIP);
      for (int lon = 0; lon <= DOME_LONGITUDES; lon++) {
        double b = Math.PI * 2 * lon / DOME_LONGITUDES;
        domeVertex(a0, b, radius, rgb, strength, w, eye, weight);
        domeVertex(a1, b, radius, rgb, strength, w, eye, weight);
      }
      GL11.glEnd();
    }
  }

  private static void domeVertex(
      double latitude,
      double longitude,
      double radius,
      float[] rgb,
      float strength,
      Warning w,
      float[] eye,
      float weight) {
    double nx = Math.sin(latitude) * Math.cos(longitude);
    double ny = Math.cos(latitude);
    double nz = Math.sin(latitude) * Math.sin(longitude);
    float rim = rim(eye, nx * radius, ny * radius, nz * radius, nx, ny, nz);
    // Heat bands rise over the surface.
    float shimmer = 0.8F + 0.2F * (float) Math.sin(latitude * 9 + w.time * 0.25);
    float alpha = (0.03F + 0.5F * rim * rim * rim) * shimmer * strength * weight;
    GL11.glColor4f(rgb[0], rgb[1], rgb[2], alpha);
    GL11.glVertex3d(nx * radius, ny * radius, nz * radius);
  }

  /** A thin bright ring on the ground at the given radius, as a shockwave front. */
  public static void ring(double radius, double width, float[] rgb, float alpha) {
    band(0, 180, Math.max(0, radius - width), radius, SEGMENTS, rgb, 0, alpha);
    band(0, 180, radius, radius + width * 0.4, SEGMENTS, rgb, alpha, 0);
  }

  /** A flat arc between two radii that brightens towards the outer one, like a swing trail. */
  public static void arcBand(
      double yaw, double arc, double inner, double outer, float[] rgb, float in, float out) {
    band(yaw, arc, inner, outer, Math.max(8, (int) Math.ceil(SEGMENTS * arc / 180)), rgb, in, out);
  }

  /** A soft round patch on the ground, opaque at its centre, such as a scorch mark. */
  public static void patch(double radius, float[] rgb, float alpha) {
    band(0, 180, 0, radius, SEGMENTS, rgb, alpha, 0);
  }

  /** The current modelview matrix: it maps drawing coordinates into eye space. */
  private static float[] eyeMatrix() {
    MATRIX.clear();
    GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
    float[] m = new float[16];
    MATRIX.get(m);
    return m;
  }

  /** 1 where a surface is seen edge-on, 0 where it faces the camera. */
  private static float rim(
      float[] m, double x, double y, double z, double nx, double ny, double nz) {
    double ex = m[0] * x + m[4] * y + m[8] * z + m[12];
    double ey = m[1] * x + m[5] * y + m[9] * z + m[13];
    double ez = m[2] * x + m[6] * y + m[10] * z + m[14];
    double fx = m[0] * nx + m[4] * ny + m[8] * nz;
    double fy = m[1] * nx + m[5] * ny + m[9] * nz;
    double fz = m[2] * nx + m[6] * ny + m[10] * nz;
    double view = Math.sqrt(ex * ex + ey * ey + ez * ez);
    double normal = Math.sqrt(fx * fx + fy * fy + fz * fz);
    if (view < 1e-6 || normal < 1e-6) {
      return 0;
    }
    return 1 - (float) Math.abs((ex * fx + ey * fy + ez * fz) / (view * normal));
  }

  private static float[] brighten(float[] rgb) {
    return new float[] {
      Math.min(1, rgb[0] * 0.5F + 0.5F),
      Math.min(1, rgb[1] * 0.6F + 0.4F),
      Math.min(1, rgb[2] * 0.6F + 0.25F)
    };
  }

  /** Stable per-vertex mottling in [0, 1). */
  private static float noise(int i, int j) {
    int h = i * 374761393 + j * 668265263;
    h = (h ^ (h >>> 13)) * 1274126177;
    return ((h ^ (h >>> 16)) & 0xFFFF) / 65536F;
  }

  private static double sq(double v) {
    return v * v;
  }
}
