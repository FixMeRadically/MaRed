package com.fixmer.mared;

import com.fixmer.genesis.technology.editor.CommandInput;
import com.fixmer.genesis.technology.editor.ScriptHeads;
import com.fixmer.mared.commands.engine.MaredScriptParser;
import com.fixmer.mared.commands.server_cmd.MaredMcCommand;
import com.fixmer.mared.technology.catalog.BrigadierCommandTools;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class MinecraftCommandToolsIntegrationTest {
    @Test void realBrigadierValidatesModCommandsArgumentsRedirectsWithoutExecuting() {
        var dispatcher = new CommandDispatcher<Object>();
        int[] executions = {0};
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("testmod:trade")
            .then(RequiredArgumentBuilder.<Object, Integer>argument("price", IntegerArgumentType.integer(0, 100))
                .executes(ctx -> { executions[0]++; return 1; })));
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("alias").redirect(dispatcher.getRoot()));
        var tools = new BrigadierCommandTools<>(dispatcher, new Object());
        assertTrue(tools.check("testmod:trade 4").valid());
        assertTrue(tools.check("alias testmod:trade 4").valid());
        assertFalse(tools.check("testmod:trade").valid());
        assertFalse(tools.check("testmod:trade 101").valid());
        assertFalse(tools.check("testmod:trade 4 extra").valid());
        assertFalse(tools.check("missing:command").valid());
        assertEquals(0, executions[0]);
    }

    @Test void realSuggestionsUseNewRegistrationsAndArgumentProvider() {
        var dispatcher = new CommandDispatcher<Object>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("testmod:trade")
            .then(RequiredArgumentBuilder.<Object, String>argument("item", StringArgumentType.word())
                .suggests((ctx, builder) -> builder.suggest("custommod:item").buildFuture())
                .executes(ctx -> 1)));
        var tools = new BrigadierCommandTools<>(dispatcher, new Object());
        var root = tools.complete("testmod:tr", 10).join();
        assertTrue(root.stream().anyMatch(c -> c.text().equals("testmod:trade")));
        var argument = tools.complete("testmod:trade ", 14).join();
        assertTrue(argument.stream().anyMatch(c -> c.text().equals("custommod:item") && c.start() == 14));
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("newmod:added").executes(ctx -> 1));
        assertTrue(tools.complete("newmod:", 7).join().stream().anyMatch(c -> c.text().equals("newmod:added")));
    }

    @Test void providerThatNeverCompletesTimesOutWithoutCancellingItsOwnFuture() {
        var dispatcher = new CommandDispatcher<Object>();
        var provider = new CompletableFuture<Suggestions>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("test")
            .then(RequiredArgumentBuilder.<Object, String>argument("value", StringArgumentType.word())
                .suggests((ctx, builder) -> provider).executes(ctx -> 1)));
        var tools = new BrigadierCommandTools<>(dispatcher, new Object());
        assertThrows(CompletionException.class, () -> tools.complete("test ", 5).join());
        assertFalse(provider.isCancelled());
        assertFalse(provider.isDone());
    }

    @Test void sharedScannerKeepsNbtQuotesAndControlBlockBoundaries() {
        String script = "if true { mc data merge entity @s {Name:'a } ; // b', Nested:{x:1}}; mc /testmod:trade 4 }";
        var snippets = ScriptHeads.minecraft(script, 10);
        assertEquals(2, snippets.size());
        assertEquals("data merge entity @s {Name:'a } ; // b', Nested:{x:1}}", snippets.getFirst().input().text());
        // Completion keeps the separator before the MR closing brace;
        // the runtime parser strips trailing whitespace before execution.
        assertEquals("testmod:trade 4", snippets.get(1).input().text().stripTrailing());
        assertEquals("testmod:trade 4 ", snippets.get(1).input().text());
        int cursor = script.lastIndexOf('}');
        var atCursor = ScriptHeads.at(script, cursor);
        assertNotNull(atCursor);
        assertEquals(snippets.get(1).input().text(), atCursor.input().text());
        assertEquals(atCursor.input().text().length(), atCursor.input().commandOffset(cursor));
        assertEquals(1, MaredScriptParser.parse(script).size());
        var parsed = MaredScriptParser.parse("mc data merge entity @s {Name:'a } ; // b', Nested:{x:1}}");
        assertEquals(snippets.getFirst().input().text(), ((MaredMcCommand) parsed.getFirst()).getCommand());
        assertNull(ScriptHeads.at("print \"mc ignored\"", 12));
        assertNull(ScriptHeads.at("// mc ignored", 8));
    }

    @Test void sourceMappingPreservesPrefixesAndMidTokenReplacement() {
        var input = new CommandInput("testmod:trade   cuitem later", 5);
        assertEquals("testmod:trade cuitem later", input.text());
        assertEquals(21, input.sourceOffset(14));
        assertEquals(20, input.replacementEnd(14, 16, "custommod:item", 16));
        assertEquals(16, input.replacementEnd(14, 16, "a b", 16));
        assertTrue(new CommandInput("say $name", 0).dynamic());
        assertTrue(new CommandInput("say \"a\\nb\"", 0).dynamic());
    }
}
