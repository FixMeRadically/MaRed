package com.fixmer.mared.services.theme;

import java.util.List;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

/**
 * Сервис тем.
 *
 * 0.3.0 (Phase F6): единая точка входа для тем. Пока обёртка над
 * MaredThemeRegistry — но новый код должен использовать ThemeService,
 * а не реестр напрямую. Это позволит позже добавить:
 *   - ThemePreset (комбинация цветов + spacing + radius);
 *   - ThemeSync (загрузка с сервера);
 *   - ThemePreview (превью на лету);
 *   - hot-reload тем.
 *
 * Не хранит состояние. Все данные — в MaredThemeRegistry.
 */
public final class ThemeService {

    private static final ThemeService INSTANCE = new ThemeService();

    public static ThemeService get() { return INSTANCE; }

    private ThemeService() {}

    /** Активная тема. Никогда не null. */
    public MaredTheme active() {
        return MaredThemeRegistry.active();
    }

    /** Все темы (встроенные + кастомные). */
    public List<MaredTheme> all() {
        return MaredThemeRegistry.all();
    }

    /** Количество тем. */
    public int count() {
        return MaredThemeRegistry.count();
    }

    /** Тема по id или null. */
    public MaredTheme byId(String id) {
        if (id == null) return null;
        for (MaredTheme t : all()) {
            if (id.equals(t.id)) return t;
        }
        return null;
    }
}