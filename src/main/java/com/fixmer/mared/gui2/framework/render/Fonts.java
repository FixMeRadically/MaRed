package com.fixmer.mared.gui2.framework.render;

/**
 * Глобальная точка доступа к FontProvider.
 *
 * 0.3.0 @Deprecated: временный мост. Правильно — получать FontProvider
 * через MaredRenderContext.fontProvider(). Оставлено, чтобы не сломать
 * StudioTopBar.layout(), который считает ширины ДО первого render().
 *
 * План: в 0.3.5, когда появится полноценный UI Services Layer,
 * Fonts удаляется, layout использует ctx.fontProvider().
 */
@Deprecated
public final class Fonts {

    private Fonts() {}

    private static volatile FontProvider provider = new ClientFontProvider();

    public static FontProvider provider() {
        return provider;
    }

    public static void setProvider(FontProvider p) {
        if (p != null) provider = p;
    }

    /** Шорткат: Fonts.width(text). */
    @Deprecated
    public static int width(String text) {
        return provider.width(text);
    }

    /** Шорткат: Fonts.lineHeight(). */
    @Deprecated
    public static int lineHeight() {
        return provider.lineHeight();
    }
}