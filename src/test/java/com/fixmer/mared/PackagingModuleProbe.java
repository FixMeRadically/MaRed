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
        System.out.println("Packaged module check passed");
    }
}
