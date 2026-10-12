package com.fixmer.mared;

import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

/** Standard-Java-only child process: no core, Minecraft or JUnit on its application classpath. */
public final class PackagingModuleProbe {
    public static void main(String[] args) throws Exception {
        var finder=ModuleFinder.of(Path.of(args[0]));
        var configuration=ModuleLayer.boot().configuration().resolve(finder,ModuleFinder.of(),Set.of("com.fixmer.genesis.core"));
        var layer=ModuleLayer.boot().defineModulesWithOneLoader(configuration,ClassLoader.getPlatformClassLoader());
        Class<?> type=layer.findLoader("com.fixmer.genesis.core").loadClass("com.fixmer.genesis.technology.storage.TextRepository");
        if(!"com.fixmer.genesis.core".equals(type.getModule().getName()))throw new AssertionError("Wrong module");
        try {
            Class.forName(type.getName(),false,ClassLoader.getSystemClassLoader());
            throw new AssertionError("Core unexpectedly present on application classpath");
        }catch(ClassNotFoundException expected){ /* isolation confirmed */ }
        Object repository=type.getConstructor(Path.class).newInstance(Path.of(args[1]));
        if(!Boolean.TRUE.equals(type.getMethod("create",String.class,String.class).invoke(repository,"script","packaged core works")))
            throw new AssertionError("Create failed");
        if(!"packaged core works".equals(type.getMethod("read",String.class).invoke(repository,"script")))
            throw new AssertionError("Read failed");
        ClassLoader loader=layer.findLoader("com.fixmer.genesis.core");
        Class<?> projectsType=loader.loadClass("com.fixmer.genesis.technology.scripts.SourceProjects");
        Class<?> languageType=loader.loadClass("com.fixmer.genesis.technology.scripts.SourceProjects$Language");
        if(!"com.fixmer.genesis.core".equals(projectsType.getModule().getName()))throw new AssertionError("Projects in wrong module");
        Object projects=projectsType.getConstructor(Path.class).newInstance(Path.of(args[1]).resolve("projects"));
        projectsType.getMethod("create",String.class,languageType).invoke(projects,"java",languageType.getField("JAVA").get(null));
        String source=(String)projectsType.getMethod("read",String.class,String.class).invoke(projects,"java","src/Main.java");
        if(!source.contains("public final class Main"))throw new AssertionError("Independent source project failed");
        projectsType.getMethod("save",String.class,String.class,String.class,String.class).invoke(projects,"java","src/Main.java",source,"// module saved\n");
        if(!"// module saved\n".equals(projectsType.getMethod("read",String.class,String.class).invoke(projects,"java","src/Main.java")))throw new AssertionError("Independent source save failed");
        Class<?> runnerType=loader.loadClass("com.fixmer.genesis.technology.scripts.JavaSourceRunner");
        if(!Boolean.TRUE.equals(runnerType.getMethod("validMainClass",String.class).invoke(null,"demo.Main")))throw new AssertionError("Java runner missing from packaged module");
        Class<?> toolsType=loader.loadClass("com.fixmer.genesis.technology.scripts.BuildTools");
        if(!(Boolean)toolsType.getMethod("validAbsolutePath",String.class).invoke(null,Path.of(args[1]).toAbsolutePath().toString()))throw new AssertionError("BuildTools unavailable from isolated core");
        Class<?> gradleType=loader.loadClass("com.fixmer.genesis.technology.scripts.GradleBuildRunner");
        if(!(Boolean)gradleType.getMethod("validTask",String.class).invoke(null,":module:build"))throw new AssertionError("Gradle runner unavailable from isolated core");
        Class<?> compilerType=loader.loadClass("com.fixmer.genesis.technology.scripts.RuntimeModuleCompiler");
        byte[] api=(byte[])compilerType.getMethod("apiJar").invoke(null);
        var entries=new java.util.HashSet<String>();
        try(var jar=new java.util.jar.JarInputStream(new java.io.ByteArrayInputStream(api))){
            for(var entry=jar.getNextJarEntry();entry!=null;entry=jar.getNextJarEntry())entries.add(entry.getName());
        }
        if(!entries.equals(Set.of("com/fixmer/genesis/technology/server/ServerModule.class","com/fixmer/genesis/technology/server/ServerContext.class","com/fixmer/genesis/technology/server/ServerContext$Subscription.class")))throw new AssertionError("Packaged server API incomplete");
        projectsType.getMethod("createServerModule",String.class).invoke(projects,"world");
        String module=(String)projectsType.getMethod("read",String.class,String.class).invoke(projects,"world","src/WorldModule.java");
        if(!module.contains("implements ServerModule"))throw new AssertionError("Packaged server template missing");
        Class<?> treeType=loader.loadClass("com.fixmer.genesis.technology.editor.ProjectFileTree");
        Object tree=treeType.getMethod("of",java.util.Collection.class).invoke(null,java.util.List.of("src/Main.java"));
        if(!(Boolean)treeType.getMethod("containsFile",String.class).invoke(tree,"src/Main.java"))throw new AssertionError("File tree unavailable from isolated core");
        Class<?> importer=loader.loadClass("com.fixmer.genesis.technology.scripts.GradleProjectImport");
        if(!(Boolean)importer.getMethod("editable",String.class).invoke(null,"module/src/main/java/Main.java"))throw new AssertionError("Importer unavailable from isolated core");
        System.out.println("Packaged module check passed: repository, source projects, Java runner and build tools");
    }
}
