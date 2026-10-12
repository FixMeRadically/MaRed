package com.fixmer.mared;
import com.fixmer.genesis.technology.editor.LineTools;
import com.fixmer.mared.technology.runtime.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LineToolsIntegrationTest {
    private LineTools.Result edit(String s,int a,int b,LineTools.Operation op){return LineTools.edit(s,a,b,b,0,op,100000);}
    @Test void duplicatesAndMovesCompleteLinesIncludingTrailingEmptyLine(){
        assertEquals("a\nb\nb",edit("a\nb",1,1,LineTools.Operation.DUPLICATE).text());
        assertEquals("\n",edit("",0,0,LineTools.Operation.DUPLICATE).text());
        assertEquals("a\n\n",edit("a\n",1,1,LineTools.Operation.DUPLICATE).text());
        assertEquals("b\nc\na\nd",edit("a\nb\nc\nd",1,2,LineTools.Operation.UP).text());
        assertEquals("a\nd\nb\nc",edit("a\nb\nc\nd",1,2,LineTools.Operation.DOWN).text());
        assertEquals("a\nb",edit("a\nb",0,0,LineTools.Operation.UP).text());
        assertEquals("a\nb",edit("a\nb",1,1,LineTools.Operation.DOWN).text());
    }
    @Test void commentTogglePreservesIndentationBlankLinesAndNestedExistingComments(){
        String text="  say 😀\n\t// existing\n   \n";
        var commented=edit(text,0,3,LineTools.Operation.COMMENT);
        assertEquals("  // say 😀\n\t// // existing\n   \n",commented.text());
        assertEquals(text,edit(commented.text(),0,3,LineTools.Operation.COMMENT).text());
        assertEquals("   \n\t",edit("   \n\t",0,1,LineTools.Operation.COMMENT).text());
        assertEquals("say hi",edit("//say hi",0,0,LineTools.Operation.COMMENT).text());
        assertEquals(3,LineTools.offset("😀\nx",1,0));
        assertThrows(IllegalArgumentException.class,()->LineTools.edit("aaa",0,0,0,0,LineTools.Operation.DUPLICATE,4));
    }
    @Test void randomMovesAreReversible(){
        var random=new java.util.Random(706);
        for(int n=0;n<2000;n++){
            int count=2+random.nextInt(40);var lines=new java.util.ArrayList<String>();for(int i=0;i<count;i++)lines.add(i%3==0?"":"row"+i);
            String source=String.join("\n",lines);int first=random.nextInt(count-1),last=first+random.nextInt(count-1-first);
            var moved=edit(source,first,last,LineTools.Operation.DOWN);
            assertEquals(source,edit(moved.text(),moved.first(),moved.last(),LineTools.Operation.UP).text());
        }
    }
    @Test void fragmentIncludesOnlyWholeCommandsAndNeverInheritsPersistent(){
        String source="#persistent\n/say first\n{\nprint \\\"ignored\\\"\n}\nsay last".replace("\\\"","\"");
        String first=CommandFragment.extract(source,1,0,1,10);var plan=CommandFilePlan.parse(first);
        assertFalse(plan.persistent());assertEquals(1,plan.parts().size());assertEquals("say first",plan.parts().get(0).text());
        String block=CommandFragment.extract(source,2,0,4,1);assertTrue(CommandFilePlan.parse(block).parts().get(0).script());
        assertThrows(IllegalArgumentException.class,()->CommandFragment.extract(source,3,0,3,source.split("\n")[3].length()));
        assertThrows(IllegalArgumentException.class,()->CommandFragment.extract(source,1,1,1,10));
        assertThrows(IllegalArgumentException.class,()->CommandFragment.extract(source,0,0,0,11));
        assertThrows(IllegalArgumentException.class,()->CommandFragment.extract("// nothing",0,0,0,10));
    }
}
