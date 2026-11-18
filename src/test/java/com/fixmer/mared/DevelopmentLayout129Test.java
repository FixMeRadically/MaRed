package com.fixmer.mared;

import com.fixmer.mared.gui2.development.DevelopmentLayout129;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DevelopmentLayout129Test {
    @Test void sectionContentUsesFullWidthAndKeepsBackControlInsideWindow() {
        for (int w : new int[]{240, 320, 360, 479, 480, 640, 854, 1280, 1920})
            for (int h : new int[]{180, 240, 360, 480, 720, 1080}) {
                var l = DevelopmentLayout129.of(w, h);
                assertTrue(l.contentWidth() > 0);
                assertTrue(l.contentX() + l.contentWidth() <= w);
                assertTrue(l.contentY() <= l.contentBottom());
                assertTrue(l.contentBottom() <= h - 30);
                assertEquals(12, l.contentX());
                assertEquals(w - 24, l.contentWidth());
                assertTrue(8 + l.navWidth() <= w);
                assertTrue(h - 30 >= l.contentBottom());
            }
    }
    @Test void scrollingCanReachLastActionAndSurvivesResize() {
        for (int w : new int[]{240, 480, 1920}) for (int h : new int[]{180, 360, 1080}) {
            var l = DevelopmentLayout129.of(w, h);
            for (int body : new int[]{0, 24, 200, 800, 5000}) {
                assertEquals(0, l.clampScroll(-100, body));
                int end = l.clampScroll(Integer.MAX_VALUE, body);
                assertEquals(Math.max(0, body - l.viewportHeight()), end);
                assertEquals(end, l.clampScroll(end, body));
                assertTrue(body - end <= l.viewportHeight());
            }
        }
    }
}
