package com.fixmer.mared.gui2.studio.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.Mared;

/**
 * Реестр actions Studio.
 *
 * 0.3.1: добавлен executeByKey(keyCode, modifiers, ctx) — Screen
 * делегирует глобальные shortcuts без хардкода.
 */
public final class EditorActionRegistry {

    private final Map<String, EditorAction> actions = new LinkedHashMap<>(16);

    public void register(EditorAction action) {
        if (action == null) return;
        if (actions.containsKey(action.id())) {
            Mared.LOGGER.warn("[action] duplicate id '{}', overriding",
                action.id());
        }
        actions.put(action.id(), action);
    }

    public void unregister(String id) {
        if (id == null) return;
        actions.remove(id);
    }

    public EditorAction byId(String id) {
        if (id == null) return null;
        return actions.get(id);
    }

    public List<EditorAction> all() {
        return new ArrayList<>(actions.values());
    }

    public boolean has(String id) {
        return id != null && actions.containsKey(id);
    }

    public int count() { return actions.size(); }

    /**
     * Выполнить action по id.
     * @return true, если action найден и успешно выполнен.
     */
    public boolean execute(String id, EditorActionContext ctx) {
        if (id == null || ctx == null) return false;
        EditorAction a = actions.get(id);
        if (a == null) {
            Mared.LOGGER.debug("[action] unknown id '{}'", id);
            return false;
        }
        if (!a.isEnabled(ctx)) return false;
        try {
            a.execute().accept(ctx);
            return true;
        } catch (Throwable t) {
            Mared.LOGGER.warn("[action] '{}' failed", id, t);
            return false;
        }
    }

    /**
     * 0.3.1: найти action по keyCode+modifiers и выполнить.
     * Перебирает в порядке регистрации — N < 20, O(N) приемлемо.
     *
     * @return true, если action найден и успешно выполнен.
     */
    public boolean executeByKey(int keyCode, int modifiers,
                                EditorActionContext ctx) {
        if (ctx == null) return false;
        for (EditorAction a : actions.values()) {
            Shortcut sc = a.parsedShortcut();
            if (sc == null) continue;
            if (!sc.matches(keyCode, modifiers)) continue;
            if (!a.isEnabled(ctx)) continue;
            try {
                a.execute().accept(ctx);
                return true;
            } catch (Throwable t) {
                Mared.LOGGER.warn("[action] '{}' failed (via key)", a.id(), t);
                return false;
            }
        }
        return false;
    }

    public void clear() {
        actions.clear();
    }
}