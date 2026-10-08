package com.fixmer.mared.gui2.genesis;

import java.util.function.Consumer;

import com.fixmer.mared.gui2.genesis.node.GenesisNode;
import com.fixmer.mared.gui2.genesis.node.GenesisNodeData;

/**
 * v12: setScreen(new MaredStudioScreen()) удалён.
 *
 * Контроллер больше не знает, что такое Screen. Он только сообщает
 * наружу «пользователь выбрал войти в категорию X» через
 * Consumer<GenesisNodeData.NodeType>. Кто на это реагирует —
 * решает GenesisSpace (в новой архитектуре) или тестовый обвес
 * (в старой).
 *
 * Если callback == null — контроллер просто останавливается на
 * IDLE. Это делает контроллер переносимым в тесты.
 */
public final class GenesisController {

    public enum State { IDLE, FLYING, FOCUSED, ENTERING }

    private static final float FLIGHT_DURATION = 1.2f;
    private static final float ENTER_DURATION = 1.1f;

    private static final float DRAG_SENS_CAM   = 0.008f;
    private static final float DRAG_SENS_ORBIT = 0.010f;
    private static final float ZOOM_FOCUSED    = 1.9f;
    private static final float ZOOM_ENTERING   = 5.0f;
    private static final float SCROLL_STEP     = 0.10f;
    private static final float ZOOM_IN_ENTER   = 3.2f;
    private static final float ZOOM_OUT_EXIT   = 1.0f;

    private State state = State.IDLE;
    private GenesisNode selected = null;
    private GenesisNode hovered  = null;
    private float flightElapsed = 0f;
    private float focusedElapsed = 0f;
    private float enterElapsed = 0f;
    private float panelSlideCur = 0f;

    private boolean dragging = false;
    private double pressX, pressY;
    private int lastDragX, lastDragY;
    private boolean dragMoved;

    /** v12: внешний получатель «пользователь вошёл в категорию». */
    private Consumer<GenesisNodeData.NodeType> enterCallback;

    public void setEnterCallback(Consumer<GenesisNodeData.NodeType> cb) {
        this.enterCallback = cb;
    }

    public void tick(GenesisWorld world, float dt) {
        if (selected != null
            && (state == State.FLYING
             || state == State.FOCUSED
             || state == State.ENTERING)) {
            world.camera().setFocusTarget(
                selected.worldX(),
                selected.worldY(),
                selected.worldZ()
            );
        }

        switch (state) {
            case FLYING -> {
                flightElapsed += dt;
                if (flightElapsed >= FLIGHT_DURATION) {
                    state = State.FOCUSED;
                    focusedElapsed = 0f;
                }
            }
            case FOCUSED -> focusedElapsed += dt;
            case ENTERING -> {
                enterElapsed += dt;
                float t = enterElapsed / ENTER_DURATION;
                if (t > 1f) t = 1f;
                float eased = t * t;
                float zoom = ZOOM_FOCUSED
                           + (ZOOM_ENTERING - ZOOM_FOCUSED) * eased;
                world.camera().setZoomDirect(zoom);
                if (enterElapsed >= ENTER_DURATION) {
                    // v12: не setScreen, а callback.
                    GenesisNodeData.NodeType type = (selected != null)
                        ? selected.data().type() : null;
                    boolean isCore = (type == GenesisNodeData.NodeType.CORE);
                    state = State.IDLE;
                    selected = null;
                    if (isCore) {
                        // v14: CORE не пространство — откат к исходному виду.
                        cancelFlight(world);
                        return;
                    }
                    if (enterCallback != null && type != null) {
                        try { enterCallback.accept(type); }
                        catch (Throwable ignored) { }
                    }
                }
            }
            default -> { }
        }

        float slideTarget = (state == State.FOCUSED) ? 1f : 0f;
        float kSlide = Math.min(1f, dt * 3.6f);
        panelSlideCur += (slideTarget - panelSlideCur) * kSlide;
        if (panelSlideCur < 0.001f) panelSlideCur = 0f;
    }

    public void onMousePressed(GenesisWorld world,
                               double mx, double my, int w, int h) {
        if (state == State.ENTERING) return;
        dragging = true;
        pressX = mx; pressY = my;
        lastDragX = (int) mx; lastDragY = (int) my;
        dragMoved = false;
    }

    public void onMouseDragged(GenesisWorld world,
                               double mx, double my, int w, int h) {
        if (!dragging || state == State.ENTERING) return;
        int dx = (int) mx - lastDragX;
        int dy = (int) my - lastDragY;
        lastDragX = (int) mx;
        lastDragY = (int) my;
        if (Math.abs(dx) + Math.abs(dy) > 1) dragMoved = true;

        float sens = (state == State.FOCUSED) ? DRAG_SENS_ORBIT : DRAG_SENS_CAM;
        world.camera().addRotation(-dx * sens, dy * sens);
    }

    public void onMouseReleased(GenesisWorld world,
                                double mx, double my, int w, int h) {
        if (!dragging) return;
        dragging = false;
        if (state == State.ENTERING) return;
        double dx = mx - pressX;
        double dy = my - pressY;
        if (!dragMoved && dx * dx + dy * dy < 25.0) {
            onMouseClicked(world, mx, my, w, h);
        }
    }

    public void onMouseClicked(GenesisWorld world,
                               double mx, double my, int w, int h) {
        switch (state) {
            case IDLE -> {
                GenesisNode hit = world.pickNode(mx, my, w, h);
                if (hit != null) startFlight(world, hit);
            }
            case FOCUSED -> {
                GenesisNode hit = world.pickNode(mx, my, w, h);
                if (hit == null) { /* ignore */ }
                else if (hit != selected) startFlight(world, hit);
                else startEntering();
            }
            case FLYING, ENTERING -> { }
        }
    }

    public void onMouseScrolled(GenesisWorld world, double scrollY) {
        if (state == State.ENTERING) return;
        float delta = (float)scrollY * SCROLL_STEP;

        if (state == State.FOCUSED && selected != null) {
            float newZoom = world.camera().zoomTarget() + delta;
            newZoom = Math.max(ZOOM_OUT_EXIT - 0.2f,
                               Math.min(ZOOM_IN_ENTER + 0.5f, newZoom));
            world.camera().setZoomTarget(newZoom);
            if (newZoom >= ZOOM_IN_ENTER) startEntering();
            else if (newZoom <= ZOOM_OUT_EXIT) cancelFlight(world);
            return;
        }

        if (state == State.IDLE) {
            if (delta > 0 && hovered != null) {
                startFlight(world, hovered);
                return;
            }
            float newZoom = world.camera().zoomTarget() + delta;
            newZoom = Math.max(0.5f, Math.min(2.5f, newZoom));
            world.camera().setZoomTarget(newZoom);
        }
    }

    public void onMouseMoved(GenesisWorld world,
                             double mx, double my, int w, int h) {
        if (state != State.IDLE || dragging) return;
        GenesisNode hit = world.pickNode(mx, my, w, h);
        hovered = hit;
        for (GenesisNode n : world.nodes()) {
            n.setMouseOver(n == hit);
        }
    }

    public void onEscape(GenesisWorld world) {
        tryEscape(world);
    }

    /** False in the overview: the host must handle Escape by closing the screen. */
    public boolean tryEscape(GenesisWorld world) {
        if(state==State.IDLE)return false;
        dragging=false;
        cancelFlight(world);
        return true;
    }

    private void startFlight(GenesisWorld world, GenesisNode node) {
        state = State.FLYING;
        selected = node;
        flightElapsed = 0f;
        for (GenesisNode n : world.nodes()) {
            n.setFocused(n == node);
            n.setMouseOver(false);
            n.resetSpin();
        }
        world.camera().setZoomTarget(ZOOM_FOCUSED);
        world.camera().setRotationTarget(0f, 0f);
    }

    private void startEntering() {
        state = State.ENTERING;
        enterElapsed = 0f;
    }

    public void cancelFlight(GenesisWorld world) {
        state = State.IDLE;
        selected = null;
        hovered = null;
        flightElapsed = 0f;
        focusedElapsed = 0f;
        enterElapsed = 0f;
        for (GenesisNode n : world.nodes()) n.setFocused(false);
        world.camera().reset();
    }

    public State state() { return state; }
    public GenesisNode selected() { return selected; }
    public GenesisNode hovered()  { return hovered; }
    public boolean isDragging() { return dragging; }
    public float panelSlide() { return panelSlideCur; }

    public float enterProgress() {
        if (state != State.ENTERING) return 0f;
        float t = enterElapsed / ENTER_DURATION;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public int accentTint() {
        GenesisNode ref = (state == State.FOCUSED
                        || state == State.FLYING
                        || state == State.ENTERING) ? selected : hovered;
        if (ref == null) return 0;
        return ref.data().defaultColor() & 0x00FFFFFF;
    }

    public float accentStrength() {
        GenesisNode ref = (state == State.FOCUSED
                        || state == State.FLYING
                        || state == State.ENTERING) ? selected : hovered;
        if (ref == null) return 0f;
        if (state == State.FOCUSED || state == State.FLYING
            || state == State.ENTERING) return 0.45f;
        return ref.hover() * 0.30f;
    }
}