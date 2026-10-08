package com.fixmer.mared;
import com.fixmer.genesis.technology.links.*;
import com.fixmer.genesis.technology.links.ScriptExports.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScriptLinksIntegrationTest {
    static final UUID MODULE=UUID.fromString("c817e3e5-9ad0-4e80-b655-21c771b88701"), EXPORT=UUID.fromString("58e67a0c-2a83-43ed-8759-0d1f48f57002");
    static final Reference REF=new Reference(MODULE,EXPORT,Kind.CONDITION);
    static String header() {
        return "// @genesis module "+MODULE+"\n// @genesis export "+EXPORT+" condition ready | Ready to run\n";
    }
    static CapabilityRegistry.Entry<String> entry(Reference ref,String source,List<String> params) {
        return new CapabilityRegistry.Entry<>(new CapabilityRegistry.Symbol(ref,source,"ready","Ready",params),"payload");
    }
    static Binding binding(Reference ref,Map<String,String> args) {
        return new Binding(UUID.randomUUID(),"quest:test","condition",ref,args);
    }
    @Test void metadataOnlyReadsTopLevelComments() {
        var m=ScriptExports.read(header()+"func ready($n) {\n// @genesis module nonsense\nreturn true\n}\nprint \"// @genesis export nonsense\"\n");
        assertEquals(MODULE,m.id());
        assertEquals(1,m.exports().size());
        assertEquals("ready",m.exports().get(0).function());
        assertEquals("Ready to run",m.exports().get(0).title());
        assertTrue(ScriptExports.read("print 'hello // @genesis module x'\n").exports().isEmpty());
    }
    @Test void malformedAndDuplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->ScriptExports.read(header()+"// @genesis module "+MODULE));
        assertThrows(IllegalArgumentException.class,()->ScriptExports.read(header()+"// @genesis export "+EXPORT+" action other"));
        assertThrows(IllegalArgumentException.class,()->ScriptExports.uuid("1-1-1-1-1"));
        assertThrows(IllegalArgumentException.class,()->ScriptExports.read("// @genesis export "+EXPORT+" condition ready"));
        assertThrows(IllegalArgumentException.class,()->ScriptExports.read("x".repeat(1_048_577)));
    }
    @Test void renamePreservesIdentityAndSnapshotIsImmutable() {
        var r=new CapabilityRegistry<String>();
        r.replace(List.of(entry(REF,"old",List.of("n"))));
        var before=r.snapshot();
        r.replace(List.of(entry(REF,"new",List.of("n"))));
        assertEquals("new",r.snapshot().resolve(REF).symbol().source());
        assertEquals("old",before.resolve(REF).symbol().source());
        assertThrows(UnsupportedOperationException.class,()->before.entries().clear());
        long rev=r.snapshot().revision();
        r.replace(List.of(entry(REF,"new",List.of("n"))));
        assertEquals(rev,r.snapshot().revision());
    }
    @Test void ambiguousRegistryPublicationIsAtomic() {
        var r=new CapabilityRegistry<String>();
        r.replace(List.of(entry(REF,"good",List.of())));
        long rev=r.snapshot().revision();
        assertThrows(IllegalArgumentException.class,()->r.replace(List.of(entry(REF,"a",List.of()),entry(new Reference(MODULE,EXPORT,Kind.ACTION),"b",List.of()))));
        assertEquals(rev,r.snapshot().revision());
        assertEquals("good",r.snapshot().resolve(REF).symbol().source());
    }
    @Test void signatureAndDeletionFailClosed() {
        var r=new CapabilityRegistry<String>();
        r.replace(List.of(entry(REF,"file",List.of("n"))));
        assertNull(r.snapshot().problem(binding(REF,Map.of("n","3"))));
        assertNotNull(r.snapshot().problem(binding(REF,Map.of())));
        assertNotNull(r.snapshot().problem(binding(REF,Map.of("n","3","extra","4"))));
        r.replace(List.of());
        assertNotNull(r.snapshot().problem(binding(REF,Map.of("n","3"))));
        assertThrows(IllegalArgumentException.class,()->r.snapshot().resolve(REF));
    }
    @Test void dependenciesAndUsagesAreImmutable() {
        var b=binding(REF,Map.of());
        var p=new Project(List.of(b),List.of());
        var index=new ScriptLinks.Index(p);
        assertEquals(List.of(b),index.usages(REF));
        assertEquals(Set.of(REF),index.dependencies("quest:test"));
        assertThrows(UnsupportedOperationException.class,()->index.usages(REF).clear());
        assertThrows(UnsupportedOperationException.class,()->index.dependencies("quest:test").clear());
        assertThrows(IllegalArgumentException.class,()->new Project(List.of(b,b),List.of()));
    }
    @Test void profileRejectsForeignBindingsAndInvalidValues() {
        UUID id=UUID.randomUUID(),cId=UUID.randomUUID(),aId=UUID.randomUUID();
        var p=new AiProfile(id,"AI","genesis.ai.test",false,10,32,cId,aId);
        var c=new Binding(cId,p.consumer(),"condition",REF,Map.of());
        var a=new Binding(aId,p.consumer(),"action",new Reference(MODULE,UUID.randomUUID(),Kind.ACTION),Map.of());
        var project=Project.empty().with(p,c,a);
        assertEquals(2,project.bindings().size());
        assertTrue(project.without(id).bindings().isEmpty());
        var foreign=new Binding(cId,"ai:other","condition",REF,Map.of());
        assertThrows(IllegalArgumentException.class,()->Project.empty().with(p,foreign,a));
        assertThrows(IllegalArgumentException.class,()->new AiProfile(id,"AI","genesis.ai.test",true,0,32,cId,aId));
        assertThrows(IllegalArgumentException.class,()->new AiProfile(id,"AI","genesis.ai.test",true,10,Double.NaN,cId,aId));
    }
    static class Action implements LinkedBehavior.Action {
        boolean done;
        Throwable failure;
        int closed;
        public boolean done() {
            return done;
        }
        public Throwable failure() {
            return failure;
        }
        public void close() {
            closed++;
        }
    }
    @Test void behaviorDoesNotOverlapAndFalseCancelsAction() {
        var action=new Action();
        int[] starts= {
            0
        }
        ,conditions= {
            0
        }
        ;
        boolean[] allow= {
            true
        }
        ;
        var b=new LinkedBehavior<Integer>(new LinkedBehavior.Host<>() {
            public boolean condition(Integer ctx) {
                conditions[0]++;
                return allow[0];
            }
            public LinkedBehavior.Action action(Integer ctx) {
                starts[0]++;
                return action;
            }
        }
        ,10);
        b.tick(0,0);
        b.tick(0,1);
        b.tick(0,10);
        assertEquals(1,starts[0]);
        assertEquals(2,conditions[0]);
        assertEquals(LinkedBehavior.State.RUNNING,b.state());
        allow[0]=false;
        b.tick(0,20);
        assertEquals(1,action.closed);
        assertEquals(LinkedBehavior.State.IDLE,b.state());
        b.close();
        b.tick(0,30);
        assertEquals(2+1,conditions[0]);
    }
    @Test void behaviorFailureIsStickyAndCleanupRuns() {
        var action=new Action();
        int[] starts= {
            0
        }
        ;
        var b=new LinkedBehavior<Integer>(new LinkedBehavior.Host<>() {
            public boolean condition(Integer c) {
                return true;
            }
            public LinkedBehavior.Action action(Integer c) {
                starts[0]++;
                return action;
            }
        }
        ,10);
        b.tick(0,0);
        action.failure=new IllegalStateException("broken");
        assertTrue(b.hasActiveFailure());
        b.tick(0,1);
        assertEquals(LinkedBehavior.State.ERROR,b.state());
        assertEquals(1,action.closed);
        b.tick(0,100);
        assertEquals(1,starts[0]);
        assertNotNull(b.failure());
    }
    @Test void completedActionCanStartOnNextEvaluationAndCloseIsIdempotent() {
        int[] starts= {
            0
        }
        ;
        var actions=new ArrayList<Action>();
        var b=new LinkedBehavior<Integer>(new LinkedBehavior.Host<>() {
            public boolean condition(Integer c) {
                return true;
            }
            public LinkedBehavior.Action action(Integer c) {
                starts[0]++;
                var a=new Action();
                actions.add(a);
                return a;
            }
        }
        ,10);
        b.tick(0,0);
        actions.get(0).done=true;
        b.tick(0,1);
        assertEquals(1,actions.get(0).closed);
        assertEquals(1,starts[0]);
        b.tick(0,10);
        assertEquals(2,starts[0]);
        b.close();
        b.close();
        assertEquals(1,actions.get(1).closed);
    }
    @Test void consumerSlotPairsDoNotCollideOnSlashes(){
        var first=new Binding(UUID.randomUUID(),"quest:campaign/path","reward",REF,Map.of());
        var second=new Binding(UUID.randomUUID(),"quest:campaign","path/reward",REF,Map.of());
        var project=new Project(List.of(first,second),List.of());
        assertEquals(2,project.bindings().size());
        assertEquals(List.of(first,second),new ScriptLinks.Index(project).usages(REF));
    }

}
