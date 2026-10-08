package com.fixmer.mared.gui2.navigation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.navigation.transition.FadeZoomTransition;
import com.fixmer.mared.gui2.navigation.transition.TransitionEffect;

/**
 * Навигационный стек пространств.
 *
 * Владеет:
 *   - текущим MaredSpace;
 *   - стеком (стек = breadcrumb);
 *   - активным переходом (одним, атомарно).
 *
 * Не владеет:
 *   - SpaceGraph (передаётся в конструкторе);
 *   - MaredProject (передаётся в space при инстанцировании через
 *     лямбды-фабрики);
 *   - overlay'ями (это забота MaredShellScreen).
 *
 * Threading: только main thread.
 */
public final class MaredNavigation {

    private final SpaceGraph graph;
    private final TransitionEffect transition = new FadeZoomTransition();

    /** Стек: последний = current, первый = root (обычно GENESIS). */
    private final Deque<MaredSpace> stack = new ArrayDeque<>(8);

    /** Активный переход. null = мы в стабильном пространстве. */
    private ActiveTransition active = null;

    /** Если true — input не рассылается (в процессе перехода). */
    public boolean isTransitioning() { return active != null; }

    public MaredSpace current() {
        return stack.peekLast();
    }

    public List<MaredSpace> stack() {
        return Collections.unmodifiableList(new ArrayList<>(stack));
    }

    public int depth() { return stack.size(); }

    public MaredNavigation(SpaceGraph graph) {
        this.graph = graph;
    }

    // ---------------------------------------------------------
    //  Boot
    // ---------------------------------------------------------

    /**
     * Заменить корень. Используется один раз при старте Shell'а.
     */
    public void boot(SpaceId rootId) {
        if (stack.isEmpty() && graph.has(rootId)) {
            MaredSpace root = graph.instantiate(rootId);
            if (root != null) {
                stack.addLast(root);
                root.onEnter(null);
            }
        }
    }

    // ---------------------------------------------------------
    //  Навигация
    // ---------------------------------------------------------

    /**
     * Push: зайти в подпространство. Текущее остаётся в стеке.
     */
    public void push(SpaceId toId) {
        if (active != null) return;
        SpaceId fromId = current() != null ? current().id() : null;
        if (!graph.canGo(fromId, toId)) {
            Mared.LOGGER.warn("[nav] cannot go {} -> {}", fromId, toId);
            return;
        }
        MaredSpace to = graph.instantiate(toId);
        if (to == null) {
            Mared.LOGGER.warn("[nav] no factory for {}", toId);
            return;
        }
        SpaceCameraState fromCam = current() != null ? current().cameraState() : null;
        TransitionKind kind = graph.kindFor(fromId, toId);
        active = new ActiveTransition(current(), to, fromCam, kind);
    }

    /**
     * Back: вернуться к родителю. pop со стека.
     */
    public void back() {
        if (active != null) return;
        if (stack.size() <= 1) return;
        MaredSpace from = current();
        MaredSpace to = null;
        // Пока не знаем to — пройдёмся по стеку вниз, пропуская current.
        var it = stack.descendingIterator();
        if (it.hasNext()) it.next();  // skip current
        if (it.hasNext()) to = it.next();
        if (to == null) return;

        SpaceCameraState fromCam = from != null ? from.cameraState() : null;
        TransitionKind kind = graph.kindFor(from.id(), to.id());
        active = new ActiveTransition(from, to, fromCam, kind);
        active.isBack = true;
    }

    /**
     * Заменить текущее. Используется из Genesis, когда категория
     * выбрана: нет смысла оставлять Genesis в стеке.
     */
    public void replace(SpaceId toId) {
        if (active != null) return;
        SpaceId fromId = current() != null ? current().id() : null;
        if (!graph.canGo(fromId, toId)) return;
        MaredSpace to = graph.instantiate(toId);
        if (to == null) return;
        SpaceCameraState fromCam = current() != null ? current().cameraState() : null;
        TransitionKind kind = graph.kindFor(fromId, toId);
        active = new ActiveTransition(current(), to, fromCam, kind);
        active.isReplace = true;
    }

    /** Return to the existing Genesis root without stacking duplicate editor screens. */
    public void returnToGenesis() {
        if(active!=null)return;
        MaredSpace genesis=stack.stream().filter(space->space.id()==SpaceId.GENESIS).findFirst().orElse(null);
        if(genesis==null){genesis=graph.instantiate(SpaceId.GENESIS);if(genesis==null)return;}
        while(!stack.isEmpty()&&stack.peekLast()!=genesis){MaredSpace old=stack.pollLast();old.onExit();}
        if(stack.isEmpty())stack.addLast(genesis);
        genesis.onEnter(null);
    }

    // ---------------------------------------------------------
    //  Tick / Render
    // ---------------------------------------------------------

    public void tick(float dt) {
        if (active != null) {
            active.elapsed += dt;
            if (active.elapsed >= transition.duration()) {
                finishTransition();
            }
            return;
        }
        MaredSpace cur = current();
        if (cur != null) cur.tick(dt);
    }

    private void finishTransition() {
        MaredSpace from = active.from;
        MaredSpace to = active.to;

        if (active.isBack) {
            // Убираем верх стека (current), затем финализируем to.
            MaredSpace top = stack.pollLast();
            if (top != null) top.onExit();
            // to уже в стеке — он же stack.peekLast теперь.
            MaredSpace now = current();
            if (now != null && now != to) {
                // На всякий случай: если стек съехал — переинициализируем.
                if (now != to) {
                    MaredSpace fresh = graph.instantiate(to.id());
                    if (fresh != null) {
                        stack.pollLast();
                        stack.addLast(fresh);
                        to = fresh;
                    }
                }
            }
            if (to != null) to.onEnter(active.fromCamera);
        } else if (active.isReplace) {
            MaredSpace top = stack.pollLast();
            if (top != null) top.onExit();
            stack.addLast(to);
            to.onEnter(active.fromCamera);
        } else {
            // push
            if (from != null) {
                // не выходим из from — он остаётся в стеке ниже;
                // onExit не вызываем.
            }
            stack.addLast(to);
            to.onEnter(active.fromCamera);
        }

        active = null;
    }

    public void render(MaredRenderContext rctx) {
        if (active != null) {
            float p = active.elapsed / transition.duration();
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            transition.render(rctx,
                active.from, active.to,
                active.fromCamera, p, active.kind);
        } else {
            MaredSpace cur = current();
            if (cur != null) cur.render(rctx);
        }
    }

    // ---------------------------------------------------------
    //  Input
    // ---------------------------------------------------------

    public boolean acceptsInput() {
        if (active != null) return false;
        MaredSpace cur = current();
        return cur != null && cur.acceptsInput();
    }

    public boolean mouseClicked(double mx, double my, int b) {
        if (!acceptsInput()) return false;
        return current().mouseClicked(mx, my, b);
    }
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (!acceptsInput()) return false;
        return current().mouseDragged(mx, my, b, dx, dy);
    }
    public boolean mouseReleased(double mx, double my, int b) {
        if (!acceptsInput()) return false;
        return current().mouseReleased(mx, my, b);
    }
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!acceptsInput()) return false;
        return current().mouseScrolled(mx, my, dx, dy);
    }
    public boolean keyPressed(int key, int scan, int mods) {
        if (!acceptsInput()) return false;
        return current().keyPressed(key, scan, mods);
    }
    public boolean charTyped(char c, int mods) {
        if (!acceptsInput()) return false;
        return current().charTyped(c, mods);
    }

    // ---------------------------------------------------------
    //  Internal
    // ---------------------------------------------------------

    private static final class ActiveTransition {
        final MaredSpace from;
        final MaredSpace to;
        final SpaceCameraState fromCamera;
        final TransitionKind kind;
        float elapsed = 0f;
        boolean isBack = false;
        boolean isReplace = false;

        ActiveTransition(MaredSpace from, MaredSpace to,
                         SpaceCameraState fromCamera,
                         TransitionKind kind) {
            this.from = from;
            this.to = to;
            this.fromCamera = fromCamera;
            this.kind = kind;
        }
    }
}