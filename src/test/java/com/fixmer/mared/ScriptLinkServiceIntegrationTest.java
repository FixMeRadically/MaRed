package com.fixmer.mared;
import com.fixmer.mared.technology.links.*;
import com.fixmer.genesis.technology.links.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import com.fixmer.genesis.technology.runtime.*;
import com.fixmer.mared.commands.engine.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class ScriptLinkServiceIntegrationTest {
    static {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(Path.of(".").toAbsolutePath());
    }
    static final ExecutionLimits TEST_LIMITS=new ExecutionLimits(1000,1_000_000,128,1_000_000_000);
    static ScriptLinkService.Snapshot refresh(ScriptLinkService s)throws Exception {
        s.refresh();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until) {
            if(s.snapshot().loaded())return s.snapshot();
            Thread.yield();
        }
        throw new AssertionError("Catalog load timeout");
    }
    static ScriptLinkService.Snapshot revision(ScriptLinkService s,long previous)throws Exception {
        s.refresh();
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(System.nanoTime()<until) {
            if(s.snapshot().revision()>previous)return s.snapshot();
            Thread.yield();
        }
        throw new AssertionError("Catalog change timeout");
    }
    static void delete(Path root)throws Exception {
        try(var paths=Files.walk(root)) {
            for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);
        }
    }
    @Test void demoInstallRoundTripAndOptimisticSave()throws Exception {
        Path root=Files.createTempDirectory("genesis-links-");
        var s=new ScriptLinkService(root);
        try {
            var first=s.installDemo().get(5,TimeUnit.SECONDS);
            var p=GenesisAiDemo.profile();
            assertEquals(2,first.catalog().entries().size());
            assertFalse(first.project().profiles().get(0).enabled());
            assertNull(first.problem(p));
            assertEquals(first.project(),ScriptLinkCodec.read(ScriptLinkCodec.write(first.project())));
            assertEquals(1,first.index().usages(GenesisAiDemo.condition().reference()).size());
            var enabled=new AiProfile(p.id(),p.title(),p.tag(),true,p.interval(),p.range(),p.condition(),p.action());
            var changed=s.put(enabled,GenesisAiDemo.condition(),GenesisAiDemo.action(),first.projectText()).get(5,TimeUnit.SECONDS);
            assertTrue(changed.project().profiles().get(0).enabled());
            assertThrows(Exception.class,()->s.put(p,GenesisAiDemo.condition(),GenesisAiDemo.action(),first.projectText()).get(5,TimeUnit.SECONDS));
            assertEquals(changed.projectText(),Files.readString(root.resolve("genesis/links.txt")));
            var removed=s.remove(p.id(),changed.projectText()).get(5,TimeUnit.SECONDS);
            assertTrue(removed.project().bindings().isEmpty());
            assertEquals(2,removed.catalog().entries().size());
        }
        finally {
            s.close();
            delete(root);
        }
    }
    @Test void renameDeletionDuplicateAndBrokenProjectFailClosed()throws Exception {
        Path root=Files.createTempDirectory("genesis-links-");
        var s=new ScriptLinkService(root);
        try {
            var first=s.installDemo().get(5,TimeUnit.SECONDS);
            Path file=root.resolve("commands/genesis.follow.txt");
            Files.move(file,root.resolve("commands/renamed.txt"));
            var renamed=revision(s,first.revision());
            assertNull(renamed.problem(GenesisAiDemo.profile()));
            assertEquals("renamed",renamed.catalog().resolve(GenesisAiDemo.action().reference()).symbol().source());
            Files.copy(root.resolve("commands/renamed.txt"),root.resolve("commands/duplicate.txt"));
            var duplicate=revision(s,renamed.revision());
            assertTrue(duplicate.catalog().entries().isEmpty());
            assertTrue(duplicate.errors().containsKey("renamed"));
            assertTrue(duplicate.errors().containsKey("duplicate"));
            Files.delete(root.resolve("commands/duplicate.txt"));
            var restored=revision(s,duplicate.revision());
            assertNull(restored.problem(GenesisAiDemo.profile()));
            Files.delete(root.resolve("commands/renamed.txt"));
            var deleted=revision(s,restored.revision());
            assertNotNull(deleted.problem(GenesisAiDemo.profile()));
            String broken="{ broken json";
            Files.writeString(root.resolve("genesis/links.txt"),broken);
            var invalid=revision(s,deleted.revision());
            assertTrue(invalid.project().profiles().isEmpty());
            assertTrue(invalid.errors().containsKey("links.txt"));
            assertEquals(broken,Files.readString(root.resolve("genesis/links.txt")));
        }
        finally {
            s.close();
            delete(root);
        }
    }
    @Test void importedFunctionsDoNotRunTopLevelAndConditionsAreStrict()throws Exception {
        Path root=Files.createTempDirectory("genesis-links-");
        Files.createDirectories(root.resolve("commands"));
        var s=new ScriptLinkService(root);
        try {
            String source=ScriptLinksIntegrationTest.header()+"print TOP_LEVEL_MUST_NOT_RUN\nfunc ready($n) { return $n > 2 }\n";
            Files.writeString(root.resolve("commands/test.txt"),source);
            var snap=refresh(s);
            assertTrue(snap.errors().isEmpty());
            var binding=ScriptLinksIntegrationTest.binding(ScriptLinksIntegrationTest.REF,Map.of("n","3"));
            var logs=new ArrayList<String>();
            var base=new MaredScriptContext(null,null,logs::add);
            assertTrue(ScriptLibrary.condition(snap.catalog(),binding,base,TEST_LIMITS));
            assertTrue(logs.isEmpty());
            assertFalse(base.hasFunction("ready"));
            assertNull(base.executionScope());
            Files.writeString(root.resolve("commands/test.txt"),ScriptLinksIntegrationTest.header()+"func ready($n) { return 1 }\n");
            var bad=revision(s,snap.revision());
            assertThrows(IllegalStateException.class,()->ScriptLibrary.condition(bad.catalog(),binding,base,TEST_LIMITS));
        }
        finally {
            s.close();
            delete(root);
        }
    }
    @Test void codecRejectsCoercedSchemaFields() {
        var project=Project.empty().with(GenesisAiDemo.profile(),GenesisAiDemo.condition(),GenesisAiDemo.action());
        String json=ScriptLinkCodec.write(project);
        assertThrows(RuntimeException.class,()->ScriptLinkCodec.read(json.replace("\"version\": 1","\"version\": 1.5")));
        assertThrows(RuntimeException.class,()->ScriptLinkCodec.read(json.replace("\"interval\": 10","\"interval\": 10.5")));
        assertThrows(RuntimeException.class,()->ScriptLinkCodec.read(json.replace("\"enabled\": false","\"enabled\": \"false\"")));
        assertThrows(RuntimeException.class,()->ScriptLinkCodec.arguments("{\"speed\": 1}"));
        assertThrows(RuntimeException.class,()->ScriptLinkCodec.read(json.replace("\"version\": 1","\"version\": 9")));
    }
    @Test void existingDemoFileIsNeverOverwritten()throws Exception {
        Path root=Files.createTempDirectory("genesis-links-");
        Files.createDirectories(root.resolve("commands"));
        Files.writeString(root.resolve("commands/genesis.follow.txt"),"user code");
        var s=new ScriptLinkService(root);
        try {
            assertThrows(Exception.class,()->s.installDemo().get(5,TimeUnit.SECONDS));
            assertEquals("user code",Files.readString(root.resolve("commands/genesis.follow.txt")));
            assertFalse(Files.exists(root.resolve("genesis/links.txt")));
        }
        finally {
            s.close();
            delete(root);
        }
    }
    @Test void linkedActionRetainsAndCancelsOwnedHostResource()throws Exception {
        Path root=Files.createTempDirectory("genesis-links-");
        Files.createDirectories(root.resolve("commands"));
        var s=new ScriptLinkService(root);
        try {
            Files.writeString(root.resolve("commands/test.txt"),ScriptLinksIntegrationTest.header().replace("condition ready","action go")+"func go($n) { return probe($n) }\n");
            var snap=refresh(s);
            var ref=new ScriptExports.Reference(ScriptLinksIntegrationTest.MODULE,ScriptLinksIntegrationTest.EXPORT,ScriptExports.Kind.ACTION);
            var binding=ScriptLinksIntegrationTest.binding(ref,Map.of("n","7"));
            var base=new MaredScriptContext(null,null,line-> {
            }
            );
            int[] calls= {
                0
            }
            ,cleanup= {
                0
            }
            ;
            base.setCapabilityHost(new InvocationCapabilities<MaredScriptContext>() {
                public boolean supports(String name) {
                    return name.equals("probe");
                }
                public Object call(String name,List<Object> args,MaredScriptContext context) {
                    calls[0]++;
                    assertEquals(7L,args.get(0));
                    context.executionScope().own(ExecutionScope.Kind.ACTION,()->cleanup[0]++);
                    return true;
                }
            }
            );
            var action=ScriptLibrary.action(snap.catalog(),binding,base,TEST_LIMITS);
            for(int i=0;i<20&&!action.executor().isFinished();i++)MaredScriptRunner.tick(null);
            assertTrue(action.executor().isFinished());
            assertEquals(1,calls[0]);
            assertFalse(action.done());
            assertNull(action.failure());
            assertNull(base.executionScope());
            action.close();
            assertEquals(1,cleanup[0]);
            assertTrue(action.done());
            action.close();
            assertEquals(1,cleanup[0]);
        }
        finally {
            MaredScriptRunner.stopAll();
            s.close();
            delete(root);
        }
    }
    @Test void actionExpressionFailureIsNotSwallowed()throws Exception {
        Path root=Files.createTempDirectory("genesis-links-");
        Files.createDirectories(root.resolve("commands"));
        var s=new ScriptLinkService(root);
        try {
            Files.writeString(root.resolve("commands/test.txt"),ScriptLinksIntegrationTest.header().replace("condition ready","action go")+"func go($n) { return $n + ) }\n");
            var snap=refresh(s);
            var ref=new ScriptExports.Reference(ScriptLinksIntegrationTest.MODULE,ScriptLinksIntegrationTest.EXPORT,ScriptExports.Kind.ACTION);
            var base=new MaredScriptContext(null,null,line-> {
            }
            );
            var action=ScriptLibrary.action(snap.catalog(),ScriptLinksIntegrationTest.binding(ref,Map.of("n","1")),base,TEST_LIMITS);
            for(int i=0;i<20&&!action.executor().isFinished();i++)MaredScriptRunner.tick(null);
            assertNotNull(action.failure());
            assertTrue(action.scope().cancelled());
        }
        finally {
            MaredScriptRunner.stopAll();
            s.close();
            delete(root);
        }
    }
}
