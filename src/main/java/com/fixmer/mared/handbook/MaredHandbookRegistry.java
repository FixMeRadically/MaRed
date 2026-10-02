package com.fixmer.mared.handbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр провайдеров руководства.
 *
 * Сейчас есть только один — "commands" (MaredCommandsHandbookData).
 * Но архитектура готова к тому, что для каждой вкладки будет свой.
 */
public final class MaredHandbookRegistry {

    private MaredHandbookRegistry() {}

    private static final Map<String, MaredHandbookData> PROVIDERS =
        new LinkedHashMap<>(4);

    private static boolean bootstrapped = false;

    // ============================================================
    //  Регистрация
    // ============================================================

    public static void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;

        register(new com.fixmer.mared.commands.handbook.MaredCommandsHandbookData());
        register(new com.fixmer.mared.handbook.providers.MaredSyntaxHandbookData());
        register(new com.fixmer.mared.handbook.providers.MaredVariablesHandbookData());
        register(new com.fixmer.mared.handbook.providers.MaredEventsHandbookData());
        register(new com.fixmer.mared.handbook.providers.MaredKeybindsHandbookData());
        register(new com.fixmer.mared.handbook.providers.MaredUiMetaHandbookData());
    }

    public static void register(MaredHandbookData provider) {
        if (provider == null || provider.id() == null) return;
        PROVIDERS.put(provider.id(), provider);
    }

    public static void reset() {
        PROVIDERS.clear();
        bootstrapped = false;
    }

    // ============================================================
    //  Доступ
    // ============================================================

    public static MaredHandbookData get(String id) {
        bootstrap();
        return PROVIDERS.get(id);
    }

    public static List<MaredHandbookData> all() {
        bootstrap();
        return new ArrayList<>(PROVIDERS.values());
    }

    public static List<String> ids() {
        bootstrap();
        return new ArrayList<>(PROVIDERS.keySet());
    }

    /** Данные для вкладки "commands" (основной провайдер). */
    public static MaredHandbookData commands() {
        return get("commands");
    }

    public static Map<String, MaredHandbookData> raw() {
        return Collections.unmodifiableMap(PROVIDERS);
    }
}
