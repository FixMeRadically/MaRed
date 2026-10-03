package com.fixmer.mared.services.theme;

import java.util.List;

import com.fixmer.mared.gui2.framework.theme.MaredCustomThemes;
import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;

/**
 * Сервис тем — единая точка входа.
 *
 * 0.3.2 (audit #27):
 *   Сервис перестаёт быть просто фасадом над реестром. Он владеет
 *   операциями reload/register/unregisterCustom. Реестр остаётся
 *   storage, но весь write-путь — через сервис.
 *
 *   Не хранит состояние само — все данные в MaredThemeRegistry.
 */
public final class ThemeService {

    private static final ThemeService INSTANCE = new ThemeService();

    public static ThemeService get() { return INSTANCE; }

    private ThemeService() {}

    public MaredTheme active() {
        return MaredThemeRegistry.active();
    }

    public List<MaredTheme> all() {
        return MaredThemeRegistry.all();
    }

    public int count() {
        return MaredThemeRegistry.count();
    }

    public MaredTheme byId(String id) {
        if (id == null) return null;
        return MaredThemeRegistry.get(id);
    }

    public void setActive(String id) {
        MaredThemeRegistry.setActive(id);
    }

    /** Перезагрузить custom темы с диска. */
    public void reloadCustom() {
        MaredCustomThemes.reload();
    }

    /** Удалить custom тему. Built-in не трогается. */
    public boolean unregisterCustom(String id) {
        return MaredThemeRegistry.unregisterCustom(id);
    }

    /** Является ли тема custom (загруженной из themes.json). */
    public boolean isCustom(String id) {
        return MaredThemeRegistry.isCustom(id);
    }
}