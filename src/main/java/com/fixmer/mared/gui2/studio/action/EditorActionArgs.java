package com.fixmer.mared.gui2.studio.action;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Параметры вызова action'а.
 *
 * 0.3.2:
 *   Раньше action'ы не принимали аргументов — Rename/Duplicate/Delete
 *   файла жили как отдельные RequestXxxEvent, а не как actions.
 *   Теперь context menu и Command Palette могут вызвать один и тот
 *   же action с разными fileName.
 *
 * Immutable. Создаётся на стороне вызывающего (Screen / Explorer)
 * и передаётся в EditorActionRegistry.execute(id, ctx, args).
 */
public record EditorActionArgs(Map<String, Object> values) {

    public static final EditorActionArgs EMPTY =
        new EditorActionArgs(Map.of());

    public EditorActionArgs {
        if (values == null || values.isEmpty()) {
            values = Map.of();
        } else {
            // Убираем null-значения — иначе Map.copyOf бросает NPE.
            Map<String, Object> cleaned = new HashMap<>(values.size());
            for (Map.Entry<String, Object> e : values.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    cleaned.put(e.getKey(), e.getValue());
                }
            }
            values = Collections.unmodifiableMap(cleaned);
        }
    }

    public static EditorActionArgs empty() { return EMPTY; }

    public static EditorActionArgs of(String key, Object value) {
        if (key == null || value == null) return EMPTY;
        return new EditorActionArgs(Map.of(key, value));
    }

    public static EditorActionArgs of(String k1, Object v1,
                                      String k2, Object v2) {
        Map<String, Object> m = new HashMap<>(4);
        if (k1 != null && v1 != null) m.put(k1, v1);
        if (k2 != null && v2 != null) m.put(k2, v2);
        return new EditorActionArgs(m);
    }

    public boolean has(String key) {
        return key != null && values.containsKey(key);
    }

    public Object get(String key) {
        return key == null ? null : values.get(key);
    }

    public String string(String key) {
        Object v = get(key);
        return v instanceof String s ? s : null;
    }

    public int intValue(String key, int def) {
        Object v = get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public long longValue(String key, long def) {
        Object v = get(key);
        return v instanceof Number n ? n.longValue() : def;
    }

    public boolean booleanValue(String key, boolean def) {
        Object v = get(key);
        return v instanceof Boolean b ? b : def;
    }
}