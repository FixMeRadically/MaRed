package com.fixmer.mared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranslationFormattingIntegrationTest {
    @Test void versionAndQuotedFileNamesAreSubstituted() {
        assertEquals("Версия: 0.4.0", MaredLang.formatTemplate("Версия: {0}", Mared.VERSION));
        assertEquals("Rename 'quests/start'", MaredLang.formatTemplate("Rename '{0}'", "quests/start"));
    }
    @Test void insertedTextIsLiteralAndNeverExpandedAgain() {
        assertEquals("'$1\\path {1} 50%' / ok", MaredLang.formatTemplate("'{0}' / {1}", "$1\\path {1} 50%", "ok"));
        assertEquals("value-value", MaredLang.formatTemplate("{0}-{0}", "value"));
    }
    @Test void missingAndOversizedIndicesStayVisible() {
        assertEquals("first {2} {999999999999999999999}", MaredLang.formatTemplate("{0} {2} {999999999999999999999}", "first"));
        assertEquals("{0}", MaredLang.formatTemplate("{0}", (Object[]) null));
    }
    @Test void percentCompatibilityAndPlainTextArePreserved() {
        assertEquals("Could not create quests/start", MaredLang.formatTemplate("Could not create %s", "quests/start"));
        assertEquals("50% complete", MaredLang.formatTemplate("50% complete"));
        assertEquals("%q", MaredLang.formatTemplate("%q", "arg"));
        assertEquals("50% done", MaredLang.formatTemplate("50% {0}", "done"));
        assertEquals("null", MaredLang.formatTemplate("{0}", (Object) null));
    }
}
