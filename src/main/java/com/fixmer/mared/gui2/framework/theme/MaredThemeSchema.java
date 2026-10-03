package com.fixmer.mared.gui2.framework.theme;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Валидатор JSON-описания темы.
 *
 * 0.3.2 (audit #29):
 *   Раньше custom theme JSON принимался как есть. Пользователь мог
 *   передать transparent text (alpha=0) → невидимый UI, absurd
 *   radius (10_000) → рендер в мусор, background=text → нечитаемо.
 *
 *   Теперь — schema validation: возвращает список Diagnostic'ов.
 *   Caller сам решает, что делать: пропустить с warning или
 *   отклонить тему.
 */
public final class MaredThemeSchema {

    private MaredThemeSchema() {}

    public static final class Diagnostic {
        public enum Severity { ERROR, WARNING, INFO }

        public final String field;
        public final Severity severity;
        public final String message;

        public Diagnostic(String field, Severity severity, String message) {
            this.field = field;
            this.severity = severity;
            this.message = message;
        }

        @Override
        public String toString() {
            return severity + " [" + field + "]: " + message;
        }
    }

    private static final Pattern ID_PATTERN =
        Pattern.compile("[a-z0-9_\\-:]+");

    private static final int MIN_TEXT_ALPHA = 0x40;
    private static final int MAX_PAD        = 32;
    private static final int MAX_RADIUS     = 16;
    private static final float MIN_CONTRAST = 0.15f;

    public static List<Diagnostic> validate(JsonObject o) {
        List<Diagnostic> out = new ArrayList<>(8);
        if (o == null) {
            out.add(new Diagnostic("<root>", Diagnostic.Severity.ERROR,
                "theme object is null"));
            return out;
        }

        // id
        if (!o.has("id")) {
            out.add(new Diagnostic("id", Diagnostic.Severity.ERROR,
                "missing 'id'"));
        } else {
            try {
                String id = o.get("id").getAsString();
                if (id == null || id.isEmpty()) {
                    out.add(new Diagnostic("id", Diagnostic.Severity.ERROR,
                        "empty 'id'"));
                } else if (!ID_PATTERN.matcher(id).matches()) {
                    out.add(new Diagnostic("id", Diagnostic.Severity.ERROR,
                        "invalid 'id' (must match [a-z0-9_\\-:]+): " + id));
                }
            } catch (Exception e) {
                out.add(new Diagnostic("id", Diagnostic.Severity.ERROR,
                    "id must be a string"));
            }
        }

        // displayName
        if (o.has("displayName")) {
            try {
                String dn = o.get("displayName").getAsString();
                if (dn == null || dn.trim().isEmpty()) {
                    out.add(new Diagnostic("displayName",
                        Diagnostic.Severity.WARNING,
                        "empty displayName — falls back to id"));
                }
            } catch (Exception e) {
                out.add(new Diagnostic("displayName",
                    Diagnostic.Severity.WARNING,
                    "displayName must be a string"));
            }
        }

        // colors
        checkColor(o, "text",        out, MIN_TEXT_ALPHA);
        checkColor(o, "textDim",     out, MIN_TEXT_ALPHA);
        checkColor(o, "textFaint",   out, 0);
        checkColor(o, "textInverse", out, 0);
        checkColor(o, "accent",      out, MIN_TEXT_ALPHA);

        // contrast
        int bg = tryColor(o, "bgPanel");
        int fg = tryColor(o, "text");
        if (bg != 0 && fg != 0) {
            float lb = luminance(bg);
            float lf = luminance(fg);
            if (Math.abs(lf - lb) < MIN_CONTRAST) {
                out.add(new Diagnostic("text/bgPanel",
                    Diagnostic.Severity.WARNING,
                    "low contrast (ΔL="
                        + String.format("%.3f", Math.abs(lf - lb)) + ")"));
            }
        }

        // metrics
        checkRange(o, "padSmall",  out, 0, MAX_PAD);
        checkRange(o, "padMedium", out, 0, MAX_PAD);
        checkRange(o, "padLarge",  out, 0, MAX_PAD);
        checkRange(o, "padHuge",   out, 0, MAX_PAD);
        checkRange(o, "radiusSmall",  out, 0, MAX_RADIUS);
        checkRange(o, "radiusMedium", out, 0, MAX_RADIUS);
        checkRange(o, "radiusLarge",  out, 0, MAX_RADIUS);

        return out;
    }

    public static List<Diagnostic> validateRoot(JsonObject root) {
        List<Diagnostic> out = new ArrayList<>();
        if (root == null || !root.has("themes")) return out;

        try {
            var arr = root.getAsJsonArray("themes");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if (!el.isJsonObject()) continue;
                JsonObject theme = el.getAsJsonObject();
                String prefix = "themes[" + i + "].";
                for (Diagnostic d : validate(theme)) {
                    out.add(new Diagnostic(prefix + d.field,
                        d.severity, d.message));
                }
                if (theme.has("id")) {
                    try {
                        String id = theme.get("id").getAsString();
                        if (id != null && !seen.add(id)) {
                            out.add(new Diagnostic(prefix + "id",
                                Diagnostic.Severity.WARNING,
                                "duplicate id inside themes.json: " + id));
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            out.add(new Diagnostic("themes", Diagnostic.Severity.ERROR,
                "invalid structure: " + e.getMessage()));
        }
        return out;
    }

    // ============================================================
    //  Helpers
    // ============================================================

    private static void checkColor(JsonObject o, String key,
                                   List<Diagnostic> out, int minAlpha) {
        if (!o.has(key)) return;
        try {
            int c = o.get(key).getAsInt();
            int a = (c >>> 24) & 0xFF;
            if (minAlpha > 0 && a < minAlpha) {
                out.add(new Diagnostic(key, Diagnostic.Severity.WARNING,
                    "alpha too low (0x" + Integer.toHexString(a)
                        + "), text may be invisible"));
            }
        } catch (Exception e) {
            out.add(new Diagnostic(key, Diagnostic.Severity.ERROR,
                "color must be an int (ARGB)"));
        }
    }

    private static void checkRange(JsonObject o, String key,
                                   List<Diagnostic> out, int min, int max) {
        if (!o.has(key)) return;
        try {
            int v = o.get(key).getAsInt();
            if (v < min || v > max) {
                out.add(new Diagnostic(key, Diagnostic.Severity.WARNING,
                    "value " + v + " out of range [" + min + ".." + max + "]"));
            }
        } catch (Exception e) {
            out.add(new Diagnostic(key, Diagnostic.Severity.ERROR,
                "metric must be an int"));
        }
    }

    private static int tryColor(JsonObject o, String key) {
        if (!o.has(key)) return 0;
        try { return o.get(key).getAsInt(); }
        catch (Exception e) { return 0; }
    }

    private static float luminance(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;
        r = lin(r); g = lin(g); b = lin(b);
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static float lin(float v) {
        return v <= 0.03928f ? v / 12.92f
            : (float) Math.pow((v + 0.055f) / 1.055f, 2.4);
    }
}