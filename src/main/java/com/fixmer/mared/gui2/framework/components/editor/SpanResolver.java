package com.fixmer.mared.gui2.framework.components.editor;

import java.util.List;

/**
 * Резолвер цветов для строки.
 *
 * 0.3.2: вводится как база для будущего syntax highlighting.
 *         Дефолтная реализация — plain(), один span на всю строку.
 *
 * Координаты spans — в logical column, не в визуальных.
 *
 * Если resolver вернёт пустой список — EditorView использует baseColor().
 * Если resolver вернёт spans с дырками — EditorView заполнит дырки
 * baseColor(). Если spans перекрываются — приоритет у первого.
 */
public interface SpanResolver {

    /**
     * Spans для логической строки.
     *
     * @param lineIndex номер логической строки (0-based)
     * @param lineText  полный текст логической строки
     * @param doc       документ (для доступа к соседним строкам,
     *                  если нужно — например, для многострочных
     *                  комментариев)
     */
    List<TextSpan> resolveSpans(int lineIndex, String lineText,
                                EditorDocument doc);

    /** Цвет по умолчанию для символов вне spans. */
    default int baseColor() { return 0xFFDDDDDD; }

    /** Стандартный resolver — весь текст одним цветом. */
    static SpanResolver plain() {
        return PlainSpanResolver.INSTANCE;
    }

    static SpanResolver plain(int color) {
        return new PlainSpanResolver(color);
    }
}