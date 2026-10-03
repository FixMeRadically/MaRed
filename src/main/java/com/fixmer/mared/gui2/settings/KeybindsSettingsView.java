package com.fixmer.mared.gui2.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.input.KeybindItem;
import com.fixmer.mared.commands.input.MaredBindRegistry;

/**
 * Draft-view настроек клавиш.
 *
 * 0.3.2: вынесено из SettingsContext.
 * 0.3.2 (audit #100 / #101):
 *   - searchBinds(query)
 *   - rename(oldKey, newKey) — немедленный, не через commit
 *   - hasKey(key)
 *
 * Rename применяется немедленно, потому что rebind — явное действие
 * пользователя. Отложить до Save = неожиданное поведение.
 */
public final class KeybindsSettingsView {

    private boolean resetRequested = false;
    private List<KeybindItem> cached;

    KeybindsSettingsView() {}

    // ---- Listing ----

    public List<KeybindItem> allBinds() {
        if (cached != null) return cached;

        List<String> keys = MaredBindRegistry.keys();
        List<KeybindItem> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            out.add(new KeybindItem(
                key,
                labelFor(key),
                MaredBindRegistry.hasBlocking(key)
            ));
        }
        cached = out;
        return cached;
    }

    public List<KeybindItem> searchBinds(String query) {
        List<KeybindItem> all = allBinds();
        if (query == null || query.isEmpty()) return all;

        String q = query.toLowerCase(Locale.ROOT);
        List<KeybindItem> out = new ArrayList<>(all.size());
        for (KeybindItem it : all) {
            if (it.key() != null
                && it.key().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(it);
                continue;
            }
            if (it.label() != null
                && it.label().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(it);
            }
        }
        return out;
    }

    // ---- Rebind ----

    public boolean rename(String oldKey, String newKey) {
        if (oldKey == null || newKey == null) return false;
        if (oldKey.equals(newKey)) return false;
        if (!MaredBindRegistry.keys().contains(oldKey)) return false;
        if (MaredBindRegistry.keys().contains(newKey)) return false;

        boolean ok = MaredBindRegistry.rename(oldKey, newKey);
        if (ok) cached = null;
        return ok;
    }

    public boolean hasKey(String key) {
        if (key == null) return false;
        return MaredBindRegistry.keys().contains(key);
    }

    // ---- Reset ----

    public void requestReset() { resetRequested = true; }
    public boolean isResetRequested() { return resetRequested; }

    void commit() {
        if (resetRequested) {
            MaredBindRegistry.clearAll();
            resetRequested = false;
            cached = null;
        }
    }

    // ---- Label helpers ----

    private static String labelFor(String key) {
        if (key == null || key.isEmpty()) return "";
        String lower = key.toLowerCase(Locale.ROOT);
        String translated = null;
        switch (lower) {
            case "leftclick":
                translated = MaredLang.get("mared.key.left_click"); break;
            case "rightclick":
                translated = MaredLang.get("mared.key.right_click"); break;
            case "middleclick":
                translated = MaredLang.get("mared.key.middle_click"); break;
            case "mousemove": case "mousemotion":
                translated = MaredLang.get("mared.key.mouse_move"); break;
            case "space":
                translated = MaredLang.get("mared.key.space"); break;
            case "enter": case "return":
                translated = MaredLang.get("mared.key.enter"); break;
            case "escape": case "esc":
                translated = MaredLang.get("mared.key.escape"); break;
            case "tab":
                translated = MaredLang.get("mared.key.tab"); break;
            case "backspace":
                translated = MaredLang.get("mared.key.backspace"); break;
            case "delete": case "del":
                translated = MaredLang.get("mared.key.delete"); break;
            case "shift": case "lshift": case "rshift":
                translated = MaredLang.get("mared.key.shift"); break;
            case "ctrl": case "control": case "lctrl": case "rctrl":
                translated = MaredLang.get("mared.key.ctrl"); break;
            case "alt": case "lalt": case "ralt":
                translated = MaredLang.get("mared.key.alt"); break;
        }
        return translated != null ? translated : key;
    }
}