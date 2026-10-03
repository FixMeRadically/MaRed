package com.fixmer.mared.commands.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fixmer.mared.Mared;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Работа с файлами команд в <config>/mared/commands/.
 * Не привязано к миру — доступно на любом сервере.
 *
 * 0.3.1 (security):
 *   - единый validateName() — граница безопасности Storage;
 *   - fileFor() отклоняет невалидные имена и проверяет, что итоговый
 *     путь остаётся внутри commandsDir (defense-in-depth против
 *     symlink и ".."), независимо от того, кто вызывает Storage;
 *   - writeCommand() — атомарная запись через .tmp + ATOMIC_MOVE;
 *   - readCommand()/exists() — не следуют по symlink;
 *   - listCommands() — фильтрует symlink и невалидные имена.
 *
 * Границы:
 *   UI-валидация (MaredNameDialog) — это UX.
 *   Storage-валидация (этот класс) — это безопасность.
 *   Они не заменяют друг друга.
 */
public class MaredCommandStorage {

    private MaredCommandStorage() {}

    private static final String SUBDIR = "mared/commands";
    private static final String EXT    = ".txt";
    private static final String TMP_SUFFIX = ".tmp";

    /** Максимальная длина имени файла (без .txt). */
    public static final int MAX_NAME_LEN = 64;

    /**
     * Единый regex допустимого имени.
     * Сегменты через точку: a, a.b, a.b.c.
     * Каждый сегмент: [a-zA-Z0-9_-]+.
     * Пробелы, слеши, ведущая точка — запрещены.
     */
    private static final Pattern NAME_PATTERN =
        Pattern.compile("[a-zA-Z0-9_\\-]+(\\.[a-zA-Z0-9_\\-]+)*");

    // ============================================================
    //  Директория
    // ============================================================

    private static Path commandsDir() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(SUBDIR);
        try {
            if (!Files.exists(dir)) Files.createDirectories(dir);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create commands dir: {}", dir, e);
            return null;
        }
        return dir;
    }

    // ============================================================
    //  Валидация
    // ============================================================

    /**
     * Единый контракт валидности имени.
     *
     * @return null если имя валидно, иначе код ошибки для локализации.
     *         Возможные коды:
     *           "empty"      — пустое
     *           "too_long"   — длиннее MAX_NAME_LEN
     *           "reserved"   — "." или ".."
     *           "slash"      — содержит "/" или "\"
     *           "hidden"     — начинается с "."
     *           "whitespace" — пробелы по краям
     *           "charset"    — не подходит под regex
     */
    public static String validateName(String name) {
        if (name == null || name.isEmpty()) return "empty";
        if (name.length() > MAX_NAME_LEN)   return "too_long";
        if (name.equals(".") || name.equals("..")) return "reserved";
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) return "slash";
        if (name.charAt(0) == '.') return "hidden";
        if (name.charAt(0) == ' ' || name.charAt(name.length() - 1) == ' ')
            return "whitespace";
        if (!NAME_PATTERN.matcher(name).matches()) return "charset";
        return null;
    }

    public static boolean isValidName(String name) {
        return validateName(name) == null;
    }

    // ============================================================
    //  Пути
    // ============================================================

    /**
     * Единственная точка построения пути к файлу.
     * Возвращает null, если name невалиден, либо если итоговый
     * нормализованный путь выходит за пределы commandsDir.
     */
    private static Path fileFor(String name) {
        Path dir = commandsDir();
        if (dir == null) return null;
        if (!isValidName(name)) return null;

        Path resolved = dir.resolve(name + EXT).normalize();
        Path dirNorm  = dir.toAbsolutePath().normalize();
        Path resAbs   = resolved.toAbsolutePath().normalize();

        if (!resAbs.startsWith(dirNorm)) {
            Mared.LOGGER.warn("[storage] name escapes commands dir: {}", name);
            return null;
        }
        return resolved;
    }

    // ============================================================
    //  Public API
    // ============================================================

    public static List<String> listCommands() {
        List<String> result = new ArrayList<>(16);
        Path dir = commandsDir();
        if (dir == null) return result;

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> !Files.isSymbolicLink(p))
                  .map(p -> p.getFileName().toString())
                  .filter(n -> n.endsWith(EXT))
                  .map(n -> n.substring(0, n.length() - EXT.length()))
                  .filter(MaredCommandStorage::isValidName)
                  .sorted()
                  .forEach(result::add);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to read commands list", e);
        }
        return result;
    }

    public static boolean exists(String name) {
        Path file = fileFor(name);
        if (file == null) return false;
        try {
            return Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(file);
        } catch (SecurityException e) {
            return false;
        }
    }

    public static String readCommand(String name) {
        Path file = fileFor(name);
        if (file == null) return "";
        try {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return "";
            if (Files.isSymbolicLink(file)) {
                Mared.LOGGER.warn("[storage] refusing to read symlink: {}", name);
                return "";
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Атомарная запись: .tmp + ATOMIC_MOVE с fallback на обычный move.
     * Возвращает true только если файл действительно заменён.
     */
    public static boolean writeCommand(String name, String content) {
        Path file = fileFor(name);
        if (file == null) {
            Mared.LOGGER.warn("[storage] writeCommand refused (invalid name): {}", name);
            return false;
        }
        if (content == null) content = "";

        try {
            Path tmp = file.resolveSibling(
                file.getFileName().toString() + TMP_SUFFIX);
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to save command {}", name, e);
            return false;
        }
    }

    public static boolean deleteCommand(String name) {
        Path file = fileFor(name);
        if (file == null) return false;
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to delete command {}", name, e);
            return false;
        }
    }

    public static boolean createCommand(String name) {
        if (!isValidName(name)) return false;
        Path file = fileFor(name);
        if (file == null) return false;
        if (Files.exists(file)) return false;
        try {
            Files.writeString(file,
                "// Write Minecraft commands here, one per line.\n" +
                "// Lines starting with / are sent as-is.\n" +
                "// Other lines get / prepended.\n",
                StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to create command {}", name, e);
            return false;
        }
    }

    public static boolean rename(String from, String to) {
        if (!isValidName(from) || !isValidName(to)) return false;
        Path src = fileFor(from);
        Path dst = fileFor(to);
        if (src == null || dst == null) return false;
        if (!Files.exists(src) || Files.exists(dst)) return false;
        try {
            Files.move(src, dst);
            return true;
        } catch (IOException e) {
            Mared.LOGGER.error("Failed to rename command {} -> {}", from, to, e);
            return false;
        }
    }
}