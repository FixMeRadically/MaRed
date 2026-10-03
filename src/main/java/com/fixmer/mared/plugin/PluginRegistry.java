package com.fixmer.mared.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fixmer.mared.Mared;
import com.fixmer.mared.gui2.studio.action.EditorAction;
import com.fixmer.mared.gui2.studio.action.EditorActionRegistry;

/**
 * Реестр загруженных плагинов + очередь отложенных EditorAction'ов.
 *
 * 0.3.2:
 *   - loaded — список успешно загруженных плагинов.
 *   - pendingActions — EditorAction'ы, ждущие старта StudioSession.
 *     После StudioActions.registerAll() сессия вызывает
 *     applyPendingActions — так плагинные actions попадают в её
 *     EditorActionRegistry.
 *   - applyPendingActions идемпотентен на стороне сессии: если
 *     действие уже зарегистрировано (по id), повторная попытка
 *     перезапишет существующее — это штатное поведение
 *     EditorActionRegistry.register.
 *
 * Не потокобезопасен — всё с main thread.
 */
public final class PluginRegistry {

    private PluginRegistry() {}

    private static final List<MaredPlugin> loaded = new ArrayList<>(4);
    private static final List<EditorAction> pendingActions =
        new ArrayList<>(8);

    // ============================================================
    //  Плагины
    // ============================================================

    public static void registerLoaded(MaredPlugin p) {
        if (p == null) return;
        loaded.add(p);
    }

    public static List<MaredPlugin> loaded() {
        return Collections.unmodifiableList(loaded);
    }

    public static int count() { return loaded.size(); }

    public static MaredPlugin byId(String id) {
        if (id == null) return null;
        for (MaredPlugin p : loaded) {
            if (id.equals(p.id())) return p;
        }
        return null;
    }

    public static void unloadAll() {
        for (MaredPlugin p : loaded) {
            try {
                p.onUnload();
            } catch (Throwable t) {
                Mared.LOGGER.warn(
                    "[plugin:{}] onUnload failed", p.id(), t);
            }
        }
        loaded.clear();
    }

    // ============================================================
    //  Pending actions
    // ============================================================

    static void addPendingAction(EditorAction a) {
        if (a == null) return;
        pendingActions.add(a);
    }

    /**
     * Применить отложенные EditorAction'ы к конкретному реестру.
     * Вызывается StudioSession.initialize() после registerAll().
     * Каждая новая сессия создаёт свежий EditorActionRegistry —
     * поэтому метод вызывается на каждый старт сессии.
     */
    public static void applyPendingActions(EditorActionRegistry registry) {
        if (registry == null) return;
        int n = pendingActions.size();
        for (int i = 0; i < n; i++) {
            EditorAction a = pendingActions.get(i);
            try {
                registry.register(a);
            } catch (Throwable t) {
                Mared.LOGGER.warn(
                    "[plugin] failed to register action '{}'",
                    a.id(), t);
            }
        }
    }

    public static int pendingActionCount() {
        return pendingActions.size();
    }

    public static void clear() {
        loaded.clear();
        pendingActions.clear();
    }
}