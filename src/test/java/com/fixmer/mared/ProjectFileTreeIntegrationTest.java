package com.fixmer.mared;

import com.fixmer.genesis.technology.editor.ProjectFileTree;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectFileTreeIntegrationTest {
    @Test void foldersSortBeforeFilesAndCollapseOnlyTheirOwnDescendants() {
        var tree=ProjectFileTree.of(List.of("settings.gradle","src/z.java","src/pkg/A.java","tests/Test.java","build.gradle"));
        assertEquals(List.of("src","tests","build.gradle","settings.gradle"),tree.rows(Set.of()).stream().map(ProjectFileTree.Row::path).toList());
        var open=tree.rows(Set.of("src"));
        assertEquals(List.of("src","src/pkg","src/z.java","tests","build.gradle","settings.gradle"),open.stream().map(ProjectFileTree.Row::path).toList());
        assertEquals(1,open.get(1).depth());assertTrue(open.get(1).directory());
        assertEquals(List.of("src","src/pkg"),tree.ancestors("src/pkg/A.java"));
        assertTrue(tree.rows(Set.of("src","src/pkg")).stream().anyMatch(r->r.path().equals("src/pkg/A.java")&&r.depth()==2&&!r.directory()));
        assertEquals(List.of(),tree.ancestors("missing.java"));
        assertEquals(List.of(),tree.ancestors("build.gradle"));
    }
    @Test void snapshotIsImmutableDeduplicatedAndIndependentOfInputOrder() {
        var input=new ArrayList<>(List.of("src/z.java","src/a.java","src/z.java"));var tree=ProjectFileTree.of(input);input.clear();
        var rows=tree.rows(Set.of("src"));assertEquals(List.of("src","src/a.java","src/z.java"),rows.stream().map(ProjectFileTree.Row::path).toList());
        assertThrows(UnsupportedOperationException.class,()->rows.clear());assertThrows(UnsupportedOperationException.class,()->tree.directories().clear());
        assertEquals(rows,ProjectFileTree.of(List.of("src/a.java","src/z.java")).rows(Set.of("src")));
        var expanded=new HashSet<>(Set.of("src"));var first=tree.rows(expanded);expanded.clear();assertEquals(3,first.size());assertEquals(1,tree.rows(expanded).size());
    }
    @Test void invalidAliasesAndFileFolderCollisionsAreRefusedInEitherOrder() {
        for(String bad:List.of("../escape.java","src//File.java","C:/File.java","src/CON.java","/absolute.java"))assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(List.of(bad)));
        for(var paths:List.of(List.of("src/A.java","src/a.java"),List.of("src/A/File.java","src/a/Other.java"),List.of("src/file.java","src/file.java/Child.java"),List.of("src/file.java/Child.java","src/file.java")))assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(paths));
        assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(Collections.nCopies(ProjectFileTree.MAX_FILES+1,"src/File.java")));
    }
    @Test void largeHierarchyHasPredictableVisibleProjectionAndDepthBound() {
        var paths=new ArrayList<String>();for(int i=0;i<2000;i++)paths.add("src/pkg"+(i/20)+"/File"+i+".java");var tree=ProjectFileTree.of(paths);
        assertEquals(1,tree.rows(Set.of()).size());assertEquals(101,tree.rows(Set.of("src")).size());assertEquals(2101,tree.rows(tree.directories()).size());
        String deep="a/".repeat(15)+"File.java";assertEquals(16,ProjectFileTree.of(List.of(deep)).rows(new HashSet<>(ProjectFileTree.of(List.of(deep)).directories())).size());
        assertThrows(IllegalArgumentException.class,()->ProjectFileTree.of(List.of("a/"+deep)));
    }
}
