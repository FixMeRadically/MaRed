package com.fixmer.mared.gui2.shell;

import org.lwjgl.glfw.GLFW;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.core.UiContext;
import com.fixmer.mared.gui2.framework.render.MaredScale;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.navigation.SpaceId;
import com.fixmer.mared.gui2.runtime.RuntimeProvider;
import com.fixmer.mared.gui2.runtime.StudioRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * MaredShellScreen v3 (1.5.39.1).
 *
 * Единственный экран MaRed. Владеет StudioRuntime. Runtime владеет
 * сессией, графом пространств и навигацией. Shell — тонкий host:
 *   - создаёт UiContext, биндит MaredScale;
 *   - создаёт StudioRuntime в init(), убивает в removed();
 *   - раздаёт input в navigation;
 *   - рисует breadcrumb поверх space'а.
 *
 * Genesis НЕ знает о Runtime. Studio получает Session через Runtime.
 */
public final class MaredShellScreen extends Screen {

    private StudioRuntime runtime;
    private boolean suspendedForSettings;
    public void suspendForSettings(){suspendedForSettings=true;}
    private SpaceId initialSpace = SpaceId.GENESIS;

    public MaredShellScreen() {
        super(Component.literal("MaRed Shell"));
    }

    /** Какое пространство открывать при boot'е. */
    public MaredShellScreen setInitialSpace(SpaceId id) {
        if (id != null) this.initialSpace = id;
        return this;
    }

    // ============================================================
    //  Lifecycle
    // ============================================================

    @Override
    protected void init() {
        super.init();
        suspendedForSettings=false;

        // Локализация — как в Studio.
        MaredLang.reload();

        Minecraft mc = Minecraft.getInstance();
        int physW = mc.getWindow().getWidth();
        int physH = mc.getWindow().getHeight();
        int mcGuiScale = (int) mc.getWindow().getGuiScale();

        UiContext uiCtx = new UiContext(this.width, this.height,
            physW, physH, mcGuiScale, MaredThemeRegistry.active());
        MaredScale.bind(uiCtx);

        // Runtime создаётся один раз на весь цикл жизни Shell.
        // Если Minecraft вызовет init() повторно (resize) — runtime
        // переиспользуется, сессия не теряется.
        if (runtime == null) {
            runtime = new StudioRuntime(this, uiCtx);
            RuntimeProvider.install(runtime);
        }

        RuntimeProvider.install(runtime);

        // Boot пространства — тоже один раз.
        if (runtime.navigation().depth() == 0) {
            runtime.navigation().boot(initialSpace);
        }
    }

    @Override
    public void removed() {
        if(suspendedForSettings){MaredScale.unbind();return;}
        if (runtime != null) {
            runtime.dispose();
            runtime = null;
        }
        RuntimeProvider.uninstall();
        MaredScale.unbind();
    }

    @Override
    public void tick() {
        if (runtime == null) return;
        runtime.tick(1f / 20f);
    }

    // ============================================================
    //  Render
    // ============================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (runtime == null) return;

        MaredRenderContext rctx = new MaredRenderContext(
            g, mouseX, mouseY, this.width, this.height);

        runtime.navigation().render(rctx);

        if (!(runtime.navigation().current() instanceof com.fixmer.mared.gui2.spaces.StudioSpace))
        BreadcrumbBar.render(g, this.font,
            runtime.navigation().stack(),
            16, 12, mouseX, mouseY);
    }

    // ============================================================
    //  Input
    // ============================================================

    private boolean transitioning() {
        return runtime == null || runtime.navigation().isTransitioning();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (transitioning()) return true;
        if (runtime.navigation().mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dx, double dy) {
        if (transitioning()) return true;
        if (runtime.navigation().mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (transitioning()) return true;
        if (runtime.navigation().mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (transitioning()) return true;
        if (runtime.navigation().mouseScrolled(mx, my, dx, dy)) return true;
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (runtime == null) return super.keyPressed(key, scan, mods);

        // F4 — Studio из любого пространства.
        if (key == GLFW.GLFW_KEY_F4 && !runtime.navigation().isTransitioning()) {
            var cur = runtime.navigation().current();
            if (cur != null && cur.id() != SpaceId.STUDIO && cur.id() != SpaceId.LOGIC) {
                runtime.navigation().push(SpaceId.STUDIO);
                return true;
            }
        }

        // Esc = back, если не в корне.
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (runtime.navigation().isTransitioning()) return true;
            var editorSpace=runtime.navigation().current();
            if(editorSpace instanceof com.fixmer.mared.gui2.spaces.StudioSpace && editorSpace.keyPressed(key,scan,mods))return true;
            if (runtime.navigation().depth() > 1) {
                runtime.navigation().back();
                return true;
            }
            var cur = runtime.navigation().current();
            if (cur != null && cur.keyPressed(key, scan, mods)) return true;
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        if (runtime.navigation().isTransitioning()) return true;
        if (runtime.navigation().keyPressed(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (transitioning()) return true;
        if (runtime.navigation().charTyped(c, mods)) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
