package com.fixmer.mared.gui2.framework.components.editor;

import java.util.Collections;
import java.util.List;

/**
 * Default SpanResolver: вся строка одним цветом.
 *
 * 0.3.2: используется по умолчанию. Один span на строку → один
 * draw call на строку (а не на символ).
 */
final class PlainSpanResolver implements SpanResolver {

    static final PlainSpanResolver INSTANCE = new PlainSpanResolver(0xFFDDDDDD);

    private final int color;

    PlainSpanResolver(int color) {
        this.color = color;
    }

    @Override
    public int baseColor() {
        return color;
    }

    @Override
    public List<TextSpan> resolveSpans(int lineIndex, String lineText,
                                       EditorDocument doc) {
        if (lineText == null || lineText.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
            new TextSpan(0, lineText.length(), color));
    }
}