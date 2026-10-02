package com.fixmer.mared.commands.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fixmer.mared.commands.engine.MaredScriptCommand;
import com.fixmer.mared.commands.engine.MaredScriptContext;
import com.fixmer.mared.commands.engine.MaredScriptExecutor;
import com.fixmer.mared.commands.engine.MaredScriptParser;
import com.fixmer.mared.commands.engine.MaredScriptRunner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Запуск .txt-файла команд Mared.
 *
 * 0.3.0: вынесено из legacy MaredEditorScreen.runCommandFile().
 *
 * Логика:
 *   - Строки вне { } — ванильные команды Minecraft, отправляются на сервер.
 *   - Блоки { ... } — Mared-скрипты, идут через MaredScriptParser
 *     и MaredScriptRunner.
 *
 * Работает и в singleplayer, и в multiplayer:
 *   - В SP Mared-executor'ы тикаются из MaredServerEvents.
 *   - В MP — из MaredClientEventHooks (клиентский tick).
 */
public final class MaredFileRunner {

    private MaredFileRunner() {}

    // ============================================================
    //  Public entry
    // ============================================================

    /**
     * Запустить текст как файл команд.
     *
     * @param text   содержимое файла (может начинаться с #persistent)
     * @param logger приёмник логов (строки без префикса [studio])
     */
    public static void run(String text, Consumer<String> logger) {
        if (text == null || text.trim().isEmpty()) {
            logger.accept("[run] file is empty");
            return;
        }

        // Определяем persistent по первой строке, но для запуска она
        // не нужна — убираем.
        boolean isPersistent = text.startsWith("#persistent");
        if (isPersistent) {
            int nl = text.indexOf('\n');
            text = (nl >= 0) ? text.substring(nl + 1) : "";
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.connection == null) {
            logger.accept("[run] player unavailable");
            return;
        }

        logger.accept("[run] starting file...");

        int cmdCount = 0;
        int blockCount = 0;
        List<String> blockBuffer = new ArrayList<>();
        int braceDepth = 0;
        int blockStartLine = 0;

        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.trim();
            int ln = i + 1;

            // Комментарии и пустые строки: внутри блока — сохраняем,
            // снаружи — игнорируем.
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                if (braceDepth > 0) blockBuffer.add(raw);
                continue;
            }

            int opens = countChar(trimmed, '{');
            int closes = countChar(trimmed, '}');

            if (braceDepth == 0 && opens > 0) {
                blockStartLine = ln;
                logger.accept("[run] line " + ln + ": block opened");
            }

            if (braceDepth > 0 || opens > 0) {
                blockBuffer.add(raw);
                braceDepth += opens - closes;

                if (braceDepth == 0 && !blockBuffer.isEmpty()) {
                    runMaredBlock(
                        String.join("\n", blockBuffer),
                        isPersistent,
                        "line " + blockStartLine,
                        logger
                    );
                    blockCount++;
                    blockBuffer.clear();
                }
                continue;
            }

            // Vanilla-команда.
            String cmd = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
            try {
                player.connection.sendCommand(cmd);
                logger.accept("[cmd] /" + cmd);
                cmdCount++;
            } catch (Exception e) {
                logger.accept("[cmd error] /" + cmd + " — " + e.getMessage());
            }
        }

        if (braceDepth > 0) {
            logger.accept("[run] WARN: block '{' not closed at end of file");
        }

        logger.accept("[run] done — vanilla: " + cmdCount
            + ", Mared blocks: " + blockCount);
    }

    // ============================================================
    //  Mared-блок
    // ============================================================

    private static void runMaredBlock(String text,
                                      boolean isPersistent,
                                      String label,
                                      Consumer<String> logger) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();

        ServerPlayer initiator = null;
        if (server != null && mc.player != null) {
            initiator = server.getPlayerList().getPlayer(mc.player.getUUID());
        }

        List<MaredScriptCommand> commands;
        try {
            commands = MaredScriptParser.parse(text);
        } catch (MaredScriptParser.ParseException e) {
            logger.accept("[run] parse error (" + label + "): " + e.getMessage());
            return;
        } catch (RuntimeException e) {
            logger.accept("[run] parse error (" + label + "): "
                + e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (commands.isEmpty()) {
            logger.accept("[run] " + label + ": empty block");
            return;
        }

        logger.accept("[run] " + label + ": " + commands.size() + " commands ready");

        MaredScriptContext ctx = new MaredScriptContext(initiator, server, logger);
        ctx.setPersistent(isPersistent);
        ctx.forceRefreshPlayerData();

        MaredScriptRunner.start(new MaredScriptExecutor(ctx, commands));
    }

    // ============================================================
    //  Утилиты
    // ============================================================

    private static int countChar(String s, char target) {
        int n = 0;
        boolean inString = false;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < len) { i++; continue; }
            if (c == '"') inString = !inString;
            if (!inString && c == target) n++;
        }
        return n;
    }
}