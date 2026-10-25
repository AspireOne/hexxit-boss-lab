package dev.hexxitlab;

import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

/**
 * Client-only warning geometry, drawn relative to the current GL origin in the current colour.
 * Solid geometry keeps telegraphs visible even with particles disabled.
 */
public final class Telegraphs {
  private static final int SEGMENTS = 96;

  private Telegraphs() {}

  public static void sphere(double radius) {
    for (int plane = 0; plane < 3; plane++) {
      GL11.glBegin(GL11.GL_LINE_LOOP);
      for (int i = 0; i < SEGMENTS; i++) {
        double angle = i * Math.PI * 2 / SEGMENTS;
        double a = Math.cos(angle) * radius;
        double b = Math.sin(angle) * radius;
        GL11.glVertex3d(
            plane == 0 ? 0 : a, plane == 1 ? 0 : plane == 0 ? a : b, plane == 2 ? 0 : b);
      }
      GL11.glEnd();
    }
  }

  /**
   * Flat wedge on the ground, centred on a Minecraft yaw, with its arc measured to either side.
   * Ground shapes take their colour because the fill and the outline use different opacities.
   */
  public static void sector(double yaw, double arc, double radius, float[] rgba) {
    ground(yaw, arc, radius, true, rgba);
  }

  public static void disc(double radius, float[] rgba) {
    ground(0, 180, radius, false, rgba);
  }

  private static void ground(double yaw, double arc, double radius, boolean wedge, float[] rgba) {
    int segments = (int) Math.ceil(SEGMENTS * arc / 180);
    GL11.glDisable(GL11.GL_CULL_FACE);
    // Translucent fill shows the whole danger area; the outline marks its exact edge.
    GL11.glColor4f(rgba[0], rgba[1], rgba[2], rgba[3] * 0.35F);
    GL11.glBegin(GL11.GL_TRIANGLE_FAN);
    GL11.glVertex3d(0, 0, 0);
    groundEdge(yaw, arc, radius, segments);
    GL11.glEnd();
    GL11.glColor4f(rgba[0], rgba[1], rgba[2], rgba[3]);
    GL11.glBegin(GL11.GL_LINE_LOOP);
    if (wedge) {
      GL11.glVertex3d(0, 0, 0);
    }
    groundEdge(yaw, arc, radius, segments);
    GL11.glEnd();
  }

  private static void groundEdge(double yaw, double arc, double radius, int segments) {
    for (int i = 0; i <= segments; i++) {
      double angle = Math.toRadians(yaw - arc + 2 * arc * i / segments);
      GL11.glVertex3d(-Math.sin(angle) * radius, 0, Math.cos(angle) * radius);
    }
  }

  public static void corridor(Vec3 aim, double radius) {
    Vec3 direction = aim.normalize();
    Vec3 reference =
        Math.abs(direction.yCoord) > 0.9
            ? Vec3.createVectorHelper(1, 0, 0)
            : Vec3.createVectorHelper(0, 1, 0);
    Vec3 side = direction.crossProduct(reference).normalize();
    Vec3 up = direction.crossProduct(side).normalize();
    GL11.glBegin(GL11.GL_LINES);
    for (int i = 0; i < 8; i++) {
      double angle = i * Math.PI / 4;
      double dx = (side.xCoord * Math.cos(angle) + up.xCoord * Math.sin(angle)) * radius;
      double dy = (side.yCoord * Math.cos(angle) + up.yCoord * Math.sin(angle)) * radius;
      double dz = (side.zCoord * Math.cos(angle) + up.zCoord * Math.sin(angle)) * radius;
      GL11.glVertex3d(dx, dy, dz);
      GL11.glVertex3d(aim.xCoord + dx, aim.yCoord + dy, aim.zCoord + dz);
    }
    GL11.glEnd();
  }
}
