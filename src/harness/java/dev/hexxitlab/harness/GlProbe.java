package dev.hexxitlab.harness;

import dev.hexxitlab.BossLab;
import dev.hexxitlab.sovereign.Sovereign;
import dev.hexxitlab.warden.Cinder;
import dev.hexxitlab.warden.Warden;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

/**
 * Guards OpenGL state. Minecraft reports errors only once per frame, which cannot tell the lab's
 * renderers from the pack's, so errors are checked around each lab renderer. Stacks leaked by any
 * mod fill up and never drain; every later push fails while pops still succeed, so entities are
 * drawn with other passes' matrices and visibly jitter. Both fail the run.
 */
final class GlProbe {
  private static final Class<?>[] OWN = {Warden.class, Cinder.class, Sovereign.class};
  // Render-thread only: the stack depths at the first frame.
  private static int[] baseline;
  private static volatile String failure;
  private boolean installed;

  /** Wraps the installed renderers once Minecraft has finished loading them. */
  @SuppressWarnings("unchecked")
  void install() {
    if (installed) {
      return;
    }
    Map<Class<?>, Render> renderers = RenderManager.instance.entityRenderMap;
    for (Class<?> type : OWN) {
      Render render = renderers.get(type);
      if (render == null) {
        throw new IllegalStateException("No renderer installed for " + type.getSimpleName());
      }
      renderers.put(type, new Checked(type.getSimpleName(), render));
    }
    installed = true;
  }

  /** Every frame starts with the stacks it started with first. */
  static void frameStarted() {
    check("frame start");
    int[] depths = depths();
    if (baseline == null) {
      baseline = depths;
    } else if (!Arrays.equals(depths, baseline) && failure == null) {
      failure = "OpenGL stacks leaked: " + stacks() + ", first frame " + Arrays.toString(baseline);
      BossLab.LOGGER.severe(failure);
    }
  }

  /** Logs and clears every pending error; returns the first, or zero. */
  static int check(String where) {
    int first = GL11.GL_NO_ERROR;
    for (int error = GL11.glGetError(); error != GL11.GL_NO_ERROR; error = GL11.glGetError()) {
      if (first == GL11.GL_NO_ERROR) {
        first = error;
      }
      BossLab.LOGGER.warning("GL probe: error " + error + " at " + where + "; " + stacks());
    }
    return first;
  }

  static String failure() {
    return failure;
  }

  private static int[] depths() {
    return new int[] {
      GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),
      GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH),
      GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH),
      GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH),
      GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH)
    };
  }

  private static String stacks() {
    return "stack depths modelview="
        + GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)
        + " projection="
        + GL11.glGetInteger(GL11.GL_PROJECTION_STACK_DEPTH)
        + " texture="
        + GL11.glGetInteger(GL11.GL_TEXTURE_STACK_DEPTH)
        + " attrib="
        + GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH)
        + " client-attrib="
        + GL11.glGetInteger(GL11.GL_CLIENT_ATTRIB_STACK_DEPTH);
  }

  /** Checks for errors immediately before and after one of the lab's renderers draws. */
  private static final class Checked extends Render {
    private final String name;
    private final Render delegate;

    Checked(String name, Render delegate) {
      this.name = name;
      this.delegate = delegate;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float tick) {
      check("before " + name);
      delegate.doRender(entity, x, y, z, yaw, tick);
      if (check("after " + name) != GL11.GL_NO_ERROR && failure == null) {
        failure = "OpenGL error raised by the " + name + " renderer";
      }
    }

    @Override
    public void doRenderShadowAndFire(
        Entity entity, double x, double y, double z, float yaw, float tick) {
      delegate.doRenderShadowAndFire(entity, x, y, z, yaw, tick);
    }

    @Override
    public void setRenderManager(RenderManager manager) {
      super.setRenderManager(manager);
      delegate.setRenderManager(manager);
    }

    @Override
    public void updateIcons(IconRegister icons) {
      delegate.updateIcons(icons);
    }
  }
}
