package com.fixmer.mared.gui2.spaces;

import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.GenesisWorld;
import com.fixmer.mared.gui2.genesis.node.GenesisNodeData;
import com.fixmer.mared.gui2.navigation.MaredNavigation;
import com.fixmer.mared.gui2.navigation.MaredSpace;
import com.fixmer.mared.gui2.navigation.SpaceCameraState;
import com.fixmer.mared.gui2.navigation.SpaceId;

/**
 * Genesis как MaredSpace.
 *
 * Обёртка над существующим GenesisWorld. Не меняет его логику —
 * только:
 *   - отдаёт cameraState() для transitions;
 *   - перехватывает сигнал GenesisController.onEnter и
 *     транслирует в navigation.push / replace.
 *
 * onEnter(prevCamera): если мы вернулись сюда из Content — ставим
 * камеру в prevCamera и просим контроллер сбросить состояние в IDLE.
 * Genesis сам lerp'нет камеру к дефолту (через camera.tick).
 */
public final class GenesisSpace implements MaredSpace {

    private final GenesisWorld world = new GenesisWorld();
    private final MaredNavigation nav;

    private int lastW = 1280;
    private int lastH = 720;

    public GenesisSpace(MaredNavigation nav) {
        this.nav = nav;

        world.controller().setEnterCallback(type -> {
            SpaceId target = mapNodeToSpace(type);
            if (target != null) {
                nav.push(target);
            }
        });
    }

    @Override public SpaceId id() { return SpaceId.GENESIS; }
    @Override public String titleKey() { return "mared.space.genesis"; }
    @Override public int accentColor() { return 0xFFFF55FF; }

    @Override
    public SpaceCameraState cameraState() {
        GenesisCamera c = world.camera();
        return new SpaceCameraState(
            c.yaw(), c.pitch(), c.zoom(),
            c.focusX(), c.focusY(), c.focusZ());
    }

    @Override
    public void onEnter(SpaceCameraState prevCamera) {
        world.controller().cancelFlight(world);
        // v13: snap zoom — иначе при возврате из подпространства
        // Genesis рендерится с зумом, оставшимся от входа.
        world.camera().setZoomDirect(1f);
        world.camera().setRotationTarget(0f, 0f);
    }

    @Override
    public void tick(float dt) {
        world.tick();
    }

    @Override
    public void render(MaredRenderContext rctx) {
        lastW = rctx.width();
        lastH = rctx.height();
        world.render(rctx.graphics(), lastW, lastH,
                     rctx.mouseX(), rctx.mouseY());
    }

    // ---------------------------------------------------------
    //  Input
    // ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            world.onMousePressed(mx, my, lastW, lastH);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button,
                                double dx, double dy) {
        if (button == 0) {
            world.onMouseDragged(mx, my, lastW, lastH);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            world.onMouseReleased(mx, my, lastW, lastH);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        world.onMouseScrolled(dy);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // ESC обрабатывается на уровне Shell (back или выход).
        // Genesis реагирует на ESC только когда в FOCUSED/FLYING.
        if (key == 256) {
            return world.controller().tryEscape(world);
        }
        return false;
    }

    @Override
    public boolean acceptsInput() { return true; }

    // ---------------------------------------------------------
    //  Mapping
    // ---------------------------------------------------------

    private static SpaceId mapNodeToSpace(GenesisNodeData.NodeType type) {
        return switch (type) {
            case CORE      -> null;   // ядро — не пространство
            case CONTENT   -> SpaceId.CONTENT;
            case WORLD     -> SpaceId.WORLD;
            case LOGIC     -> SpaceId.LOGIC;
            case RESOURCES -> SpaceId.RESOURCES;
            case TOOLS     -> SpaceId.TOOLS;
            case SCENARIOS -> SpaceId.SCENARIOS;
        };
    }
}