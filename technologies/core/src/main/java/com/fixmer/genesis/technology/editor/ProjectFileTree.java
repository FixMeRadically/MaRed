package com.fixmer.genesis.technology.editor;

import com.fixmer.genesis.technology.storage.RepositoryPath;
import java.util.*;

/** Immutable, bounded file hierarchy. Performs no filesystem access and stores no UI state. */
public final class ProjectFileTree {
    public static final int MAX_FILES = 4096;
    public record Row(String path, String label, int depth, boolean directory) {}
    private record Node(Row row, List<Node> children) {}
    private static final class Builder {
        final Row row;
        final Map<String, Builder> children = new HashMap<>();
        final Map<String, String> spellings = new HashMap<>();
        Builder(Row row) { this.row = row; }
    }
    private final List<Node> roots;
    private final Set<String> directories, files;

    private ProjectFileTree(List<Node> roots, Set<String> directories, Set<String> files) {
        this.roots = roots;
        this.directories = Set.copyOf(directories);
        this.files = Set.copyOf(files);
    }

    public static ProjectFileTree of(Collection<String> paths) {
        return of(paths,List.of());
    }
    public static ProjectFileTree of(Collection<String> paths,Collection<String> directoryPaths) {
        Objects.requireNonNull(paths);Objects.requireNonNull(directoryPaths);
        if ((long)paths.size()+directoryPaths.size() > MAX_FILES) throw new IllegalArgumentException("File tree exceeds " + MAX_FILES + " entries");
        var root = new Builder(null);
        var folders = new HashSet<String>();
        var files = new HashSet<String>();
        int count = 0;
        var explicitDirs=new HashSet<String>(directoryPaths);
        for(String file:paths)if(explicitDirs.contains(file))throw new IllegalArgumentException("File/directory collision: "+file);
        var entries=new ArrayList<String>(paths);entries.addAll(directoryPaths);
        for (String path : entries) {
            if (++count > MAX_FILES || !com.fixmer.genesis.technology.storage.ProjectPath.valid(path))
                throw new IllegalArgumentException("Invalid or excessive file tree path: " + path);
            String[] parts = path.split("/");
            Builder parent = root;
            String prefix = "";
            for (int depth = 0; depth < parts.length; depth++) {
                String name = parts[depth];
                prefix = RepositoryPath.child(prefix, name);
                boolean directory = depth < parts.length - 1 || explicitDirs.contains(path);
                String spelling = parent.spellings.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
                if (spelling != null && !spelling.equals(name))
                    throw new IllegalArgumentException("Case alias in file tree: " + prefix);
                Builder node = parent.children.get(name);
                if (node != null && node.row.directory() != directory)
                    throw new IllegalArgumentException("File/directory collision: " + prefix);
                if (node == null) {
                    node = new Builder(new Row(prefix, name, depth, directory));
                    parent.children.put(name, node);
                }
                if (directory) folders.add(prefix);
                parent = node;
            }
            if(explicitDirs.contains(path))folders.add(path);else files.add(path);
        }
        return new ProjectFileTree(freeze(root), folders, files);
    }

    private static List<Node> freeze(Builder parent) {
        var sorted = new ArrayList<>(parent.children.values());
        sorted.sort(Comparator.comparing((Builder b) -> !b.row.directory())
                .thenComparing(b -> b.row.label(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(b -> b.row.label()));
        var result = new ArrayList<Node>();
        for (Builder child : sorted) result.add(new Node(child.row, freeze(child)));
        return List.copyOf(result);
    }

    public Set<String> directories() { return directories; }
    public boolean containsFile(String path) { return files.contains(path); }

    /** Expanded paths belong to the caller; output is an immutable preorder view. */
    public List<Row> rows(Set<String> expanded) {
        Objects.requireNonNull(expanded);
        var result = new ArrayList<Row>();
        append(roots, expanded, result);
        return List.copyOf(result);
    }

    private static void append(List<Node> nodes, Set<String> expanded, List<Row> result) {
        for (Node node : nodes) {
            result.add(node.row());
            if (node.row().directory() && expanded.contains(node.row().path()))
                append(node.children(), expanded, result);
        }
    }

    /** Existing file's parent directories, from the root down. */
    public List<String> ancestors(String file) {
        if (!files.contains(file)) return List.of();
        var result = new ArrayList<String>();
        String parent = RepositoryPath.parent(file);
        while (!parent.isEmpty()) {
            result.add(parent);
            parent = RepositoryPath.parent(parent);
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }
}
