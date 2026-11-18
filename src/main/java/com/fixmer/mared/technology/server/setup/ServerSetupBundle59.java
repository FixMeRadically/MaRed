package com.fixmer.mared.technology.server.setup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Pure-JDK extraction and private-directory boundary, independent of the game API. */
final class ServerSetupBundle59 {
    private ServerSetupBundle59() {}
    static void ensureDirectory(Path path) throws IOException {
        for (Path p = path; p != null; p = p.getParent()) {
            if (Files.exists(p, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(p) || Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()))
                throw new IOException("Linked setup directory");
        }
        Files.createDirectories(path);
    }
    static void privateDirectory(Path directory) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(directory, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) throw new IOException("Private Windows ACL required");
        UserPrincipal owner = view.getOwner();
        view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(EnumSet.allOf(AclEntryPermission.class))
            .setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build()));
    }
    static void unpack(Path root, InputStream resource) throws IOException {
        try (resource) {
            if (resource == null) throw new IOException("Bundled controller missing");
            try (ZipInputStream zip = new ZipInputStream(resource)) {
                Set<String> names = new HashSet<>(); long total = 0; int count = 0;
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    String name = entry.getName();
                    if (++count > 256 || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0 || !names.add(name.toLowerCase(Locale.ROOT)))
                        throw new IOException("Controller archive entry");
                    Path relative=Path.of(name);
                    if(relative.isAbsolute()||Arrays.stream(name.split("/")).anyMatch(part->part.equals("..")||part.equals(".")))throw new IOException("Controller archive path");
                    Path target = root.resolve(name).normalize();
                    com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles.safe(target);
                    if (!target.startsWith(root) || target.equals(root)) throw new IOException("Controller archive path");
                    if (entry.isDirectory()) { Files.createDirectories(target); continue; }
                    if (!(name.equals("runtime/core.jar") || name.startsWith("tools/") && name.endsWith(".py")))
                        throw new IOException("Controller archive file type");
                    Files.createDirectories(target.getParent());
                    try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                        byte[] bytes = new byte[65536]; int read;
                        while ((read = zip.read(bytes)) != -1) {
                            total += read;
                            if (total > 16 * 1024 * 1024) throw new IOException("Controller archive limit");
                            out.write(bytes, 0, read);
                        }
                    }
                }
            }
        }
        for (String required : List.of("tools/server_setup.py", "tools/first_launch.py", "runtime/core.jar"))
            if (!Files.isRegularFile(root.resolve(required))) throw new IOException("Incomplete controller bundle");
    }
}
