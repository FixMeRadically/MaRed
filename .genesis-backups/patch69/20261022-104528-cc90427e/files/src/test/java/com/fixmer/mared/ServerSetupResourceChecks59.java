package com.fixmer.mared;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Actual resource packaging check on Gradle's real Minecraft/NeoForge classpath. */
public final class ServerSetupResourceChecks59 {
    public static void main(String[] args) throws Exception {
        Class.forName("net.neoforged.neoforge.client.event.ClientTickEvent$Post", false,
            ServerSetupResourceChecks59.class.getClassLoader());
        Map<String,byte[]> files = new HashMap<>();
        try (InputStream resource = ServerSetupResourceChecks59.class.getResourceAsStream("/genesis/server59/controller.zip")) {
            if (resource == null) throw new AssertionError("Server setup bundle missing from processed resources");
            try (var zip = new ZipInputStream(resource)) {
                for (ZipEntry entry; (entry=zip.getNextEntry())!=null;)
                    if (!entry.isDirectory()) files.put(entry.getName(), zip.readAllBytes());
            }
        }
        for (String name : List.of("tools/server_setup.py", "tools/server_panel66.py", "tools/first58/panel66.py", "tools/first58/repeat67.py", "tools/first58/bridge67.py", "tools/first58/desktop66.py", "tools/first_launch.py", "tools/first58/wizard_model.py", "tools/first58/runtime.py", "tools/first58/launch62.py", "tools/first58/diagnostics63.py", "tools/first58/core65.py", "tools/first58/session61.py", "tools/first58/windows_job.py", "runtime/core.jar"))
            if (!files.containsKey(name)) throw new AssertionError("Missing controller component: " + name);
        Set<String> classes = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(files.get("runtime/core.jar")))) {
            for (ZipEntry entry; (entry=zip.getNextEntry())!=null;) classes.add(entry.getName());
        }
        for (String name : List.of("FirstLaunchArtifacts", "FirstLaunchProbe", "FirstLaunchDev62", "LocalApply67", "LocalFileBrowser68"))
            if (!classes.contains("com/fixmer/genesis/technology/server/firstlaunch/" + name + ".class"))
                throw new AssertionError("Incomplete controller core: " + name);
        System.out.println("Server59 real-classpath resource checks passed");
    }
}
