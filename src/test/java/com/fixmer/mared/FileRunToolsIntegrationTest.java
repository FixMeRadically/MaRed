package com.fixmer.mared;

import com.fixmer.mared.technology.runtime.CommandFilePlan;
import com.fixmer.mared.technology.runtime.RunJournal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FileRunToolsIntegrationTest {
    @Test void mixedFilesKeepRawNbtAndQuotedMrNbtSeparate() {
        var plan = CommandFilePlan.parse("#persistent\n/data merge entity @s {Name:'a } // b'}\n{\n// } ignored\nmc data merge entity @s {Name:'a } // b'};\n}\n/othermod:test");
        assertTrue(plan.persistent()); assertEquals(3, plan.parts().size());
        assertFalse(plan.parts().get(0).script());
        assertEquals("data merge entity @s {Name:'a } // b'}", plan.parts().get(0).text());
        assertTrue(plan.parts().get(1).script()); assertEquals(3, plan.parts().get(1).line());
        assertEquals("othermod:test", plan.parts().get(2).text());
        assertEquals("{ print ok }", CommandFilePlan.parse("{ print ok } // } comment").parts().get(0).text());
    }
    @Test void malformedAndOversizedPlansAreRejectedBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> CommandFilePlan.parse("say before\n{ print incomplete"));
        assertThrows(IllegalArgumentException.class, () -> CommandFilePlan.parse("{ print ok } say unexpected"));
        assertThrows(IllegalArgumentException.class, () -> CommandFilePlan.parse("}"));
        assertThrows(IllegalArgumentException.class, () -> CommandFilePlan.parse("x".repeat(1_048_577)));
        assertThrows(IllegalArgumentException.class, () -> CommandFilePlan.parse("say hi\n".repeat(4097)));
    }
    @Test void journalHasBoundedImmutableOutputAndClearRevision() {
        var journal = new RunJournal();
        for(int i=0;i<700;i++) journal.add("line " + i);
        var before=journal.snapshot();
        assertEquals(512,before.size()); assertEquals(188,journal.dropped());
        journal.add("x".repeat(5000));
        assertEquals("line 188",before.get(0).message());
        assertEquals(4097,journal.snapshot().get(511).message().length());
        assertThrows(UnsupportedOperationException.class, () -> before.clear());
        long revision=journal.revision(); journal.clear();
        assertTrue(journal.snapshot().isEmpty()); assertTrue(journal.revision()>revision);
    }
}
