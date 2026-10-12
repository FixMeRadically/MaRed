package com.fixmer.genesis.technology.storage;

import java.util.Locale;

/** Portable relative document identifiers. Extension is managed by the repository. */
public final class RepositoryPath {
    public static final int MAX_LENGTH = 256;
    public static final int MAX_SEGMENT = 64;
    public static final int MAX_DEPTH = 16;
    private RepositoryPath() {}

    public static boolean valid(String path) {
        if (path == null || path.isEmpty() || path.length() > MAX_LENGTH) return false;
        String[] parts = path.split("/", -1);
        if (parts.length > MAX_DEPTH) return false;
        for (String part : parts) {
            if (part.length() > MAX_SEGMENT || !part.matches("[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*")) return false;
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) return false;
        }
        return true;
    }

    public static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }
    public static String leaf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
    public static String child(String folder, String name) {
        return folder.isEmpty() ? name : folder + "/" + name;
    }
}
