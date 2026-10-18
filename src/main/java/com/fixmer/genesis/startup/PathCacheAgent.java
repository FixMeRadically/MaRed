package com.fixmer.genesis.startup;

import java.io.*;
import java.lang.instrument.*;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.jar.JarFile;

/** Initial-load transformer; never modifies the on-disk FML JAR. Disabled by default. */
public final class PathCacheAgent implements ClassFileTransformer {
    private static volatile String status = "waiting_for_loader";
    private static volatile Class<?> helper;
    private static volatile Class<?> accessHelper;
    private static volatile String accessStatus="disabled";
    private static volatile Class<?> fontHelper;
    private static volatile String fontStatus="disabled";
    private static boolean fontEnabled(){return Boolean.getBoolean("genesis.path.cache.fontSizing");}
    private static boolean pathEnabled(){return Boolean.parseBoolean(System.getProperty("genesis.path.cache.enabled","true"));}
    private static boolean accessEnabled(){return Boolean.getBoolean("genesis.path.cache.accessIndex");}
    private static JarFile retained;
    private final Instrumentation instrumentation;
    private PathCacheAgent(Instrumentation instrumentation) { this.instrumentation=instrumentation; }
    public static void premain(String arguments, Instrumentation instrumentation) {
        try {
            if (!Path.of("").getClass().getName().equals("sun.nio.fs.WindowsPath")) { status="unsupported_platform"; return; }
            // Construct the system-loaded transformer before extending bootstrap search.
            PathCacheAgent agent = new PathCacheAgent(instrumentation);
            Path jar = Path.of(PathCacheAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            retained = new JarFile(jar.toFile()); instrumentation.appendToBootstrapClassLoaderSearch(retained);
            helper = Class.forName("com.fixmer.genesis.startup.RootEquality", true, null);
            if(!pathEnabled())status="disabled";
            if(accessEnabled()) {
                accessHelper=Class.forName("com.fixmer.genesis.startup.AccessIndex",true,null);
                accessStatus="waiting_for_access_transformer";
            }
            if(fontEnabled()) {
                fontHelper=Class.forName("com.fixmer.genesis.startup.FontSizing",true,null);
                fontStatus="waiting_for_codepoint_map";
            }
            instrumentation.addTransformer(agent, false);
            Thread reporter = new Thread(() -> { try { Thread.sleep(30000); writeStatus(); } catch (InterruptedException ignored) {} }, "genesis-path-cache-report");
            reporter.setDaemon(true); reporter.start();
            Runtime.getRuntime().addShutdownHook(new Thread(PathCacheAgent::writeStatus,"genesis-path-cache-exit"));
        } catch (Exception | LinkageError failure) { status="agent_unavailable"; writeStatus(); }
    }
    @Override public byte[] transform(Module module, ClassLoader loader, String name, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
        if(redefining!=null)return null;
        boolean access="net/neoforged/accesstransformer/parser/AccessTransformerList".equals(name)&&accessEnabled();
        boolean root="net/neoforged/fml/loading/moddiscovery/locators/NeoForgeDevProvider".equals(name)&&pathEnabled();
        boolean font="net/minecraft/client/gui/font/CodepointMap".equals(name)&&fontEnabled();
        if(!access&&!root&&!font)return null;
        try {
            byte[] transformed=font?FontSizingPatch.patch(bytes):access?AccessIndexPatch.patch(bytes):RootPatch.patch(bytes);
            if(transformed==null){if(font)fontStatus="unsupported_font_layout";else if(access)accessStatus="unsupported_access_transformer";else status="unsupported_loader";return null;}
            Module helperModule=(font?fontHelper:access?accessHelper:helper).getModule();
            if(module.isNamed()&&!module.canRead(helperModule))
                instrumentation.redefineModule(module,Set.of(helperModule),Map.of(),Map.of(),Set.of(),Map.of());
            if(font)fontStatus="applied_layout_v1";else if(access)accessStatus="applied_at_10_0_1";else status="applied_fml_4_0_42";
            return transformed;
        } catch(Exception|LinkageError failure) {if(font)fontStatus="patch_declined";else if(access)accessStatus="patch_declined";else status="patch_declined";return null;}
    }
    private static synchronized void writeStatus() {
        try {
            String value=System.getProperty("genesis.path.cache.stats", ""); if(value.isEmpty())return;
            Path target=Path.of(value); Properties data=new Properties();data.setProperty("schema","1");data.setProperty("status",status);
            data.setProperty("launch",System.getProperty("genesis.path.cache.launch","unrecorded"));
            if(helper!=null){long[] counts=(long[])helper.getMethod("counts").invoke(null);data.setProperty("hits",Long.toString(counts[0]));data.setProperty("misses",Long.toString(counts[1]));}
            data.setProperty("at_status",accessStatus);
            if(accessHelper!=null) {
                long[] counts=(long[])accessHelper.getMethod("counts").invoke(null);
                String[] names={"at_queries","at_cache_hits","at_rebuilds","at_rules_indexed","at_fallbacks"};
                for(int i=0;i<names.length;i++)data.setProperty(names[i],Long.toString(counts[i]));
            }
            data.setProperty("font_status",fontStatus);
            if(fontHelper!=null) {
                long[] counts=(long[])fontHelper.getMethod("counts").invoke(null);
                String[] names={"font_calls","font_large_calls","font_expected_symbols","font_capped_calls","font_max_expected"};
                for(int i=0;i<names.length;i++)data.setProperty(names[i],Long.toString(counts[i]));
            }
            Path temporary=Files.createTempFile(target.getParent(),".path-cache-",".tmp");
            try { try(var out=Files.newOutputStream(temporary)){data.store(out,"Genesis path comparison cache; counters are not timings");}
                try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(temporary);}
        } catch (Exception | LinkageError ignored) { }
    }
}
