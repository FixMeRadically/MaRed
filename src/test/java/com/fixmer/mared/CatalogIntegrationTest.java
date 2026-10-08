package com.fixmer.mared;

import com.fixmer.genesis.technology.catalog.CommandTree;
import com.fixmer.mared.technology.catalog.BrigadierCatalog;
import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static org.junit.jupiter.api.Assertions.*;

public class CatalogIntegrationTest {
    @AfterEach void clear(){MaredCommandRegistry.install(CommandTree.empty());}
    @Test void discoversModCommandsAndTypedOptionalArgumentsWithoutExecution() {
        int[] calls={0};var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(CatalogIntegrationTest.<Object>lit("pack:magic").requires(s->{calls[0]++;return true;})
            .executes(c->{calls[0]++;return 0;})
            .then(CatalogIntegrationTest.<Object,Integer>arg("amount",IntegerArgumentType.integer(1,9)).executes(c->{calls[0]++;return 0;})));
        var tree=BrigadierCatalog.snapshot(dispatcher);var detail=tree.describe("pack:magic");
        assertEquals(List.of("pack:magic","pack:magic <amount>"),detail.usages());assertEquals(0,calls[0]);
        assertEquals(1,detail.parameters().size());assertTrue(detail.parameters().getFirst().type().contains("[1, 9]"));
        assertFalse(detail.truncated());
    }
    static <S> com.mojang.brigadier.builder.LiteralArgumentBuilder<S> lit(String name){return literal(name);}
    static <S,T> com.mojang.brigadier.builder.RequiredArgumentBuilder<S,T> arg(String name,com.mojang.brigadier.arguments.ArgumentType<T> type){return argument(name,type);}
    @Test void redirectAliasesKeepTheirOwnPrefix() {
        var dispatcher=new CommandDispatcher<Object>();
        var target=dispatcher.register(CatalogIntegrationTest.<Object>lit("target").then(CatalogIntegrationTest.<Object,String>arg("value",StringArgumentType.word()).executes(c->0)));
        dispatcher.register(CatalogIntegrationTest.<Object>lit("alias").redirect(target));
        var detail=BrigadierCatalog.snapshot(dispatcher).describe("alias");
        assertEquals(List.of("alias <value>"),detail.usages());assertEquals(List.of("alias → target"),detail.redirects());assertFalse(detail.truncated());
    }
    @Test void executeStyleRootRedirectDoesNotExpandAllCommands() {
        var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(CatalogIntegrationTest.<Object>lit("chain").redirect(dispatcher.getRoot()));
        var detail=BrigadierCatalog.snapshot(dispatcher).describe("chain");
        assertEquals(List.of("chain …"),detail.usages());assertFalse(detail.truncated());
    }
    @Test void equalArgumentNamesInDifferentBranchesStayDistinct() {
        var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(CatalogIntegrationTest.<Object>lit("edit")
            .then(CatalogIntegrationTest.<Object>lit("number").then(CatalogIntegrationTest.<Object,Integer>arg("value",IntegerArgumentType.integer()).executes(c->0)))
            .then(CatalogIntegrationTest.<Object>lit("text").then(CatalogIntegrationTest.<Object,String>arg("value",StringArgumentType.greedyString()).executes(c->0))));
        var detail=BrigadierCatalog.snapshot(dispatcher).describe("edit");assertEquals(2,detail.parameters().size());
        assertNotEquals(detail.parameters().get(0).path(),detail.parameters().get(1).path());assertNotEquals(detail.parameters().get(0).type(),detail.parameters().get(1).type());
    }
    @Test void snapshotIsDetachedAndImmutable() {
        var dispatcher=new CommandDispatcher<Object>();dispatcher.register(CatalogIntegrationTest.<Object>lit("first").executes(c->0));
        var tree=BrigadierCatalog.snapshot(dispatcher);dispatcher.register(CatalogIntegrationTest.<Object>lit("second").executes(c->0));
        assertEquals(List.of("first"),tree.names());assertEquals(List.of("first","second"),BrigadierCatalog.snapshot(dispatcher).names());
        assertThrows(UnsupportedOperationException.class,()->tree.nodes().clear());
        assertThrows(UnsupportedOperationException.class,()->tree.nodes().get(0).children().clear());
    }
    @Test void graphCyclesAndTraversalBudgetsStopCleanly() {
        var nodes=Map.of(0,new CommandTree.Node(0,"",CommandTree.Kind.ROOT,"",List.of(),List.of(1),null,false),
            1,new CommandTree.Node(1,"cycle",CommandTree.Kind.LITERAL,"",List.of(),List.of(2),null,false),
            2,new CommandTree.Node(2,"again",CommandTree.Kind.LITERAL,"",List.of(),List.of(1),null,true));
        var tree=new CommandTree(nodes,0,false);assertTrue(tree.describe("cycle").truncated());
        assertEquals(List.of("cycle again"),tree.describe("cycle").usages());assertTrue(tree.describe("cycle",2,1,2).truncated());
    }
    @Test void snapshotBudgetIsVisibleInMetadata() {
        var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(CatalogIntegrationTest.<Object>lit("large").then(CatalogIntegrationTest.<Object>lit("nested").executes(c->0)));
        var tree=BrigadierCatalog.snapshot(dispatcher,2,10);assertTrue(tree.truncated());assertTrue(tree.describe("large").truncated());assertEquals(2,tree.nodes().size());
    }
    @Test void changingServersDropsOldCommandsAndDetails() {
        var first=new CommandDispatcher<Object>();first.register(CatalogIntegrationTest.<Object>lit("oldmod:command").executes(c->0));
        MaredCommandRegistry.install(BrigadierCatalog.snapshot(first));assertNotNull(MaredCommandRegistry.findByName("oldmod:command"));long revision=MaredCommandRegistry.revision();
        var second=new CommandDispatcher<Object>();second.register(CatalogIntegrationTest.<Object>lit("newmod:command").executes(c->0));
        MaredCommandRegistry.install(BrigadierCatalog.snapshot(second));assertTrue(MaredCommandRegistry.revision()>revision);
        assertNull(MaredCommandRegistry.findByName("oldmod:command"));assertNotNull(MaredCommandRegistry.findByName("newmod:command"));
        MaredCommandRegistry.install(CommandTree.empty());assertTrue(MaredCommandRegistry.all().isEmpty());
    }
    @Test void sourcesCategoriesAndSearchAreSeparate() {
        var dispatcher=new CommandDispatcher<Object>();dispatcher.register(CatalogIntegrationTest.<Object>lit("mod:spell").executes(c->0));
        MaredCommandRegistry.install(BrigadierCatalog.snapshot(dispatcher));
        assertEquals(1,MaredCommandRegistry.search("SPELL",MaredCommandRegistry.Source.MC,"MC / mod").size());
        assertTrue(MaredCommandRegistry.search("spell",MaredCommandRegistry.Source.MC,"other").isEmpty());
        assertFalse(MaredCommandRegistry.all(MaredCommandRegistry.Source.MR).isEmpty());
        assertNotNull(MaredCommandRegistry.findByName("wait",MaredCommandRegistry.Source.MR));
        var info=MaredCommandRegistry.findByName("mod:spell");assertEquals(-1,info.opLevel);assertTrue(info.nbtHints.isEmpty());
        assertThrows(UnsupportedOperationException.class,()->MaredCommandRegistry.all().clear());
    }
    @Test void searchDoesNotDependOnTurkishDefaultLocale() {
        Locale saved=Locale.getDefault();
        try{Locale.setDefault(Locale.forLanguageTag("tr-TR"));var dispatcher=new CommandDispatcher<Object>();dispatcher.register(CatalogIntegrationTest.<Object>lit("INFO").executes(c->0));
            MaredCommandRegistry.install(BrigadierCatalog.snapshot(dispatcher));assertEquals(1,MaredCommandRegistry.search("info").size());
        }finally{Locale.setDefault(saved);}
    }
    @Test void handbookReadsLiveMcAndHasNoFallbackVanillaCommands() {
        var book=new com.fixmer.mared.commands.handbook.MaredCommandsHandbookData();
        MaredCommandRegistry.install(CommandTree.empty());assertNull(book.findById("mc.give"));assertNull(book.findById("vanilla.give"));
        var dispatcher=new CommandDispatcher<Object>();dispatcher.register(CatalogIntegrationTest.<Object>lit("custom").then(CatalogIntegrationTest.<Object,Integer>arg("amount",IntegerArgumentType.integer()).executes(c->0)));
        MaredCommandRegistry.install(BrigadierCatalog.snapshot(dispatcher));assertEquals(1,book.findById("mc.custom").parameters.size());
        assertTrue(book.allEntries().stream().anyMatch(e->e.id.equals("mc.custom")));assertNotNull(book.findById("mared.wait"));
    }
    @Test void registeredMetadataAdapterPreservesHostArgumentProperties() {
        var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(CatalogIntegrationTest.<Object>lit("custom").then(CatalogIntegrationTest.<Object,Integer>arg("amount",IntegerArgumentType.integer(2,7)).executes(c->0)));
        int[] calls={0};
        var tree=BrigadierCatalog.snapshot(dispatcher,parser->{
            calls[0]++;var integer=(IntegerArgumentType)parser;
            return "plugin:integer min="+integer.getMinimum()+" max="+integer.getMaximum();
        });
        assertEquals(1,calls[0]);assertEquals("plugin:integer min=2 max=7",tree.describe("custom").parameters().getFirst().type());
    }
    @Test void brokenPluginSerializerDoesNotHideOtherCommands() {
        var dispatcher=new CommandDispatcher<Object>();
        dispatcher.register(CatalogIntegrationTest.<Object>lit("custom").then(CatalogIntegrationTest.<Object,Integer>arg("amount",IntegerArgumentType.integer()).executes(c->0)));
        var tree=BrigadierCatalog.snapshot(dispatcher,parser->{throw new LinkageError("bad plugin metadata");});
        assertEquals(List.of("custom"),tree.names());
        assertTrue(tree.describe("custom").parameters().getFirst().type().contains("unavailable"));
    }
    @Test void shippedJarContainsNoObsoleteCommandJson() throws Exception {
        try(var zip=new java.util.zip.ZipFile(System.getProperty("genesis.packagedMod"))){assertTrue(zip.stream().noneMatch(e->e.getName().startsWith("mared/commands/")&&e.getName().endsWith(".json")));}
    }
}
