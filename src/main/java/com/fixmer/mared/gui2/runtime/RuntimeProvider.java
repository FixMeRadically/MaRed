package com.fixmer.mared.gui2.runtime;

import com.fixmer.mared.Mared;

/**
 * Явный holder активного StudioRuntime.
 *
 * Зачем не static-поле в самом StudioRuntime:
 *   static в самом классе означает "глобально доступен всегда",
 *   включая моменты, когда Shell не открыт. RuntimeProvider
 *   вводит явный lifecycle: install() в Shell.init(),
 *   uninstall() в Shell.removed(). Пока Shell закрыт — get()
 *   бросает исключение, что ловит ошибки жизненного цикла сразу.
 *
 * Что НЕ делает:
 *   - не хранит Session (это в StudioRuntime.session());
 *   - не знает про Screen / MC;
 *   - не устанавливается "навсегда" — только на время жизни Shell.
 *
 * Пример:
 *   RuntimeProvider.get().session()   — из StudioSpace.onEnter
 *   RuntimeProvider.isInstalled()     — из тестов/диагностики
 */
public final class RuntimeProvider {

    private RuntimeProvider() {}

    private static volatile StudioRuntime current;

    /** Вызывается один раз в MaredShellScreen.init(). */
    public static void install(StudioRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime must not be null");
        }
        if (current != null && current != runtime) {
            Mared.LOGGER.warn(
                "[runtime] install() replacing existing runtime");
        }
        current = runtime;
    }

    /** Вызывается один раз в MaredShellScreen.removed(). */
    public static void uninstall() {
        current = null;
    }

    /** Активный runtime. Бросает, если Shell ещё не открыт. */
    public static StudioRuntime get() {
        StudioRuntime rt = current;
        if (rt == null) {
            throw new IllegalStateException(
                "StudioRuntime not installed. "
                + "Is MaredShellScreen open?");
        }
        return rt;
    }

    public static boolean isInstalled() {
        return current != null;
    }

    /** Для диагностики: не null-safe. */
    public static StudioRuntime peek() {
        return current;
    }
}
