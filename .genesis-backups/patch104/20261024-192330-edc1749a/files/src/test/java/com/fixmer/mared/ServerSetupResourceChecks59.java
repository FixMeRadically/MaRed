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
        for (String name : List.of("tools/first58/dedicated103.py", "tools/first58/auth89.py", "tools/first58/access88.py", "tools/first58/dedicated88.py", "tools/first58/preflight87.py", "tools/first58/http_range86.py", "tools/first58/rebind86.py", "tools/first58/dedicated85.py", "tools/first58/publication85.py", "tools/first58/renew85.py", "tools/first58/dedicated83.py", "tools/first58/publication84.py","tools/first58/publication83.py", "tools/first58/dedicated82.py", "tools/first58/admission82.py", "tools/first58/admission_wire82.py", "tools/first58/dedicated81.py", "tools/first58/transition81.py", "tools/first58/publication79.py", "tools/dedicated_server75.py", "tools/first58/dedicated75.py", "tools/first58/dedicated76.py", "tools/first58/bootstrap76.py", "tools/first58/bootstrap_wire76.py", "tools/first58/https_bootstrap76.py", "tools/first58/publication75.py", "tools/dedicated_server74.py", "tools/first58/dedicated74.py", "tools/control_setup73.py", "tools/first58/control73.py", "tools/first58/https_control73.py", "tools/first58/repeat71.py", "tools/remote_client70.py", "tools/first58/remote70.py", "tools/first58/install70.py", "tools/first58/native70.py", "tools/remote_update.py", "tools/server_setup.py", "tools/server_panel66.py", "tools/first58/panel66.py", "tools/first58/repeat67.py", "tools/first58/bridge67.py", "tools/first58/desktop66.py", "tools/first_launch.py", "tools/first58/wizard_model.py", "tools/first58/runtime.py", "tools/first58/launch62.py", "tools/first58/diagnostics63.py", "tools/first58/core65.py", "tools/first58/session61.py", "tools/first58/windows_job.py", "runtime/core.jar"))
            if (!files.containsKey(name)) throw new AssertionError("Missing controller component: " + name);
        Set<String> classes = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(files.get("runtime/core.jar")))) {
            for (ZipEntry entry; (entry=zip.getNextEntry())!=null;) classes.add(entry.getName());
        }
        for (String name : List.of("FirstLaunchArtifacts", "FirstLaunchProbe", "FirstLaunchDev62", "LocalApply67", "LocalFileBrowser68"))
            if (!classes.contains("com/fixmer/genesis/technology/server/firstlaunch/" + name + ".class"))
                throw new AssertionError("Incomplete controller core: " + name);
        for(String name:List.of("SessionHandoff89","ServerAccess88","ResumeDownload86","BootstrapRenewal86","Renewal85","ScriptRelease84","Publication83","Admission82","CommitFence81","PublicationHealth80","Publication79","PublicationStatus79","RemoteOffer69","RemoteClient69","RemotePublisher69","RemoteUpdateCli69","RemoteInstallCli70","RemoteSession70","RemoteRepeat71","RemoteConsent72","ControlWire73","ControlRoom73","ControlHttp73","ControlSetup73","DedicatedRelease74","DedicatedSession74","DedicatedTools74","DedicatedAdmin75","Bootstrap76","BootstrapPublisher76","BootstrapGateCli76","Recovery76","ServerDirectory76"))
            if(!classes.contains("com/fixmer/genesis/technology/server/remote/"+name+".class"))throw new AssertionError("Missing remote core: "+name);
        if(!classes.contains("com/fixmer/genesis/technology/scripts/BuildSnapshot84.class"))throw new AssertionError("Missing Script build snapshot core");
        System.out.println("Server59 real-classpath resource checks passed");
    }
}
