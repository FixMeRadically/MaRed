package com.fixmer.mared.gui2.studio.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fixmer.mared.Mared;

/**
 * Реестр actions Studio.
 *
 * 0.3.1: executeByKey.
 * 0.3.2: overload execute(id, ctx, args) — параметризованные actions.
 */
public final class EditorActionRegistry {

    private final Map<String, EditorAction> actions = new LinkedHashMap<>(16);

    public void register(EditorAction action) {
        if (action == null) return;

        if (actions.containsKey(action.id())) {
            Mared.LOGGER.warn("[action] duplicate id '{}', overriding",
                action.id());
        }

        Shortcut newSc = action.parsedShortcut();
        if (newSc != null) {
            EditorAction conflict = findActionByShortcut(newSc, action.id());
            if (conflict != null) {
                Mared.LOGGER.warn(
                    "[action] shortcut conflict: '{}' uses '{}' which is "
                        + "already bound to '{}'",
                    action.id(), action.shortcutDisplay(), conflict.id());
            }
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

    // ============================================================
    //  Execute
    // ============================================================

    public boolean execute(String id, EditorActionContext ctx) {
        return execute(id, ctx, EditorActionArgs.EMPTY);
    }

    public boolean execute(String id, EditorActionContext ctx,
                           EditorActionArgs args) {
        if (id == null || ctx == null) return false;
        EditorAction a = actions.get(id);
        if (a == null) {
            Mared.LOGGER.debug("[action] unknown id '{}'", id);
            return false;
        }
        EditorActionArgs effective = args == null ? EditorActionArgs.EMPTY : args;
        if (!a.isEnabled(ctx, effective)) return false;
        try {
            a.run(ctx, effective);
            return true;
        } catch (Throwable t) {
            Mared.LOGGER.warn("[action] '{}' failed", id, t);
            return false;
        }
    }

    public boolean executeByKey(int keyCode, int modifiers,
                                EditorActionContext ctx) {
        if (ctx == null) return false;
        for (EditorAction a : actions.values()) {
            Shortcut sc = a.parsedShortcut();
            if (sc == null) continue;
            if (!sc.matches(keyCode, modifiers)) continue;
            if (!a.isEnabled(ctx, EditorActionArgs.EMPTY)) continue;
            try {
                a.run(ctx, EditorActionArgs.EMPTY);
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

    // ============================================================
    //  Conflicts
    // ============================================================

    private EditorAction findActionByShortcut(Shortcut sc, String exceptId) {
        if (sc == null) return null;
        for (EditorAction a : actions.values()) {
            if (a.id().equals(exceptId)) continue;
            Shortcut other = a.parsedShortcut();
            if (other == null) continue;
            if (other.keyCode == sc.keyCode
                && other.modifiers == sc.modifiers) {
                return a;
            }
        }
        return null;
    }

    public List<Conflict> findConflicts() {
        List<Conflict> out = new ArrayList<>();
        List<EditorAction> list = new ArrayList<>(actions.values());
        int n = list.size();

        for (int i = 0; i < n; i++) {
            Shortcut scI = list.get(i).parsedShortcut();
            if (scI == null) continue;
            for (int j = i + 1; j < n; j++) {
                Shortcut scJ = list.get(j).parsedShortcut();
                if (scJ == null) continue;
                if (scI.keyCode == scJ.keyCode
                    && scI.modifiers == scJ.modifiers) {
                    out.add(new Conflict(list.get(i), list.get(j)));
                }
            }
        }
        return out;
    }

    public record Conflict(EditorAction a, EditorAction b) {
        public String describe() {
            return a.id() + " <-> " + b.id()
                + " [" + a.shortcutDisplay() + "]";
        }
    }
}