package com.fixmer.mared;

import cpw.mods.jarhandling.JarContents;
import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipFile;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the shipped nested JAR and FML's real reader; a flat test classpath would hide this error. */
public class PackagingIntegrationTest {
    @TempDir Path directory;
    private Path extractedCore() throws Exception {
        Path mod=Path.of(System.getProperty("genesis.packagedMod"));
        try(var archive=new ZipFile(mod.toFile())) {
            String json=new String(archive.getInputStream(archive.getEntry("META-INF/jarjar/metadata.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            var metadata=com.google.gson.JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("jars").get(0).getAsJsonObject();
            assertEquals("0.1.8",metadata.getAsJsonObject("version").get("artifactVersion").getAsString());
            assertEquals("[0.1.8,0.2.0)",metadata.getAsJsonObject("version").get("range").getAsString());
            Path core=directory.resolve("genesis-core-0.1.8.jar");
            try(var in=archive.getInputStream(archive.getEntry(metadata.get("path").getAsString()))){Files.copy(in,core);}
            return core;
        }
    }
    @Test void fmlRecognizesNestedCoreAsGameLibrary() throws Exception {
        Path core=extractedCore();
        try(var contents=JarContents.of(core)) {
            assertEquals("GAMELIBRARY",contents.getManifest().getMainAttributes().getValue("FMLModType"));
            assertEquals("com.fixmer.genesis.core",contents.getManifest().getMainAttributes().getValue("Automatic-Module-Name"));
            var discovered=new JarModsDotTomlModFileReader().read(contents,ModFileDiscoveryAttributes.DEFAULT);
            assertNotNull(discovered);assertEquals(IModFile.Type.GAMELIBRARY,discovered.getType());
            assertEquals("com.fixmer.genesis.core",discovered.getSecureJar().name());
        }
    }
    @Test void textRepositoryLoadsFromPackagedModuleWithoutTestClasspath() throws Exception {
        Path core=extractedCore();
        // JVM module classloaders have no close(): on Windows they can lock this JAR
        // until JVM exit and make JUnit @TempDir cleanup fail after a successful check.
        Path javaExecutable=Path.of(System.getProperty("java.home"),"bin","java");
        String classes=Path.of(PackagingModuleProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        Path output=directory.resolve("module-probe.log");
        Process child=new ProcessBuilder(javaExecutable.toString(),"-cp",classes,PackagingModuleProbe.class.getName(),
            core.toString(),directory.resolve("data").toString())
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            boolean exited=child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(exited,"Module probe did not finish in 30 seconds");
            assertEquals(0,child.exitValue(),()->{
                try{return Files.readString(output);}catch(IOException error){return error.toString();}
            });
            // Verify release explicitly instead of depending only on JUnit's later cleanup.
            Files.delete(core);
            assertFalse(Files.exists(core));
        } finally {
            if(child.isAlive()){child.destroyForcibly();child.waitFor();}
        }
    }
    @Test void aiMixinAndDemoAreInTheShippedMod() throws Exception {
        try(var archive=new ZipFile(Path.of(System.getProperty("genesis.packagedMod")).toFile())) {
            var config=com.google.gson.JsonParser.parseString(new String(archive.getInputStream(archive.getEntry("mared.mixins.json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(java.util.stream.StreamSupport.stream(config.getAsJsonArray("mixins").spliterator(),false).anyMatch(v->v.getAsString().equals("GenesisMobGoals")));
            assertNotNull(archive.getEntry("com/fixmer/mared/mixin/GenesisMobGoals.class"));
            assertNotNull(archive.getEntry("com/fixmer/mared/technology/ai/GenesisAiEvents.class"));
            assertNotNull(archive.getEntry("genesis/examples/follow.mr"));
        }
    }

}
