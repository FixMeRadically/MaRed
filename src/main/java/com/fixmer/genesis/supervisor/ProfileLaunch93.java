package com.fixmer.genesis.supervisor;

import com.fixmer.genesis.admission.ProfileAgent93;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Installs the bundled admission capsule before child spawn; never on the persistent launch plan. */
public final class ProfileLaunch93 {
    public static final String REQUIRED="GENESIS_PROFILE_REQUIRED93",AGENT="GENESIS_PROFILE_AGENT93";
    private ProfileLaunch93() {}
    public static String argument(Path jar,Path game)throws IOException {
        jar=jar.toAbsolutePath().normalize();
        if(!Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS)||jar.toString().indexOf('=')>=0||jar.toString().chars().anyMatch(c->c<32))throw new IOException("PROFILE93_BAD_CAPSULE_PATH");
        RuntimeFiles.safe(jar);
        return "-javaagent:"+jar+"="+ProfileAgent93.encode(game);
    }
    static String install(Path game)throws IOException {
        byte[] bytes;
        try(var in=ProfileLaunch93.class.getResourceAsStream("/mared/dev/profile-admission93.jar")) {
            if(in==null)throw new IOException("PROFILE93_CAPSULE_MISSING_BUILD_PATCH93");
            bytes=in.readNBytes(256*1024+1);
            if(bytes.length==0||bytes.length>256*1024)throw new IOException("PROFILE93_CAPSULE_INVALID");
        }
        return persist(game,bytes);
    }
    private static String persist(Path game,byte[] bytes)throws IOException {
        if(bytes.length==0||bytes.length>256*1024)throw new IOException("PROFILE93_CAPSULE_INVALID");
        try(var jar=new java.util.jar.JarInputStream(new ByteArrayInputStream(bytes))) {
            var manifest=jar.getManifest();
            if(manifest==null||!"com.fixmer.genesis.admission.ProfileAgent93".equals(manifest.getMainAttributes().getValue("Premain-Class")))throw new IOException("PROFILE93_CAPSULE_INVALID");
            boolean main=false;long total=0;int entries=0;
            for(java.util.jar.JarEntry entry;(entry=jar.getNextJarEntry())!=null;) {
                if(++entries>32)throw new IOException("PROFILE93_CAPSULE_INVALID");
                main|=entry.getName().equals("com/fixmer/genesis/admission/ProfileAgent93.class");
                byte[] buffer=new byte[8192];int n;
                while((n=jar.read(buffer))!=-1)if((total+=n)>1024*1024)throw new IOException("PROFILE93_CAPSULE_INVALID");
            }
            if(!main)throw new IOException("PROFILE93_CAPSULE_INVALID");
        }
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException impossible){throw new IOException(impossible);}
        Path dir=game.resolve("config/mared/dev/admission93");RuntimeFiles.safe(dir);Files.createDirectories(dir);
        Path jar=dir.resolve("profile-"+hash+".jar");RuntimeFiles.safe(jar);
        if(!Files.exists(jar,LinkOption.NOFOLLOW_LINKS)) {
            Path temp=Files.createTempFile(dir,".profile-",".tmp");
            try{Files.write(temp,bytes);try{Files.move(temp,jar,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,jar);}}
            finally{Files.deleteIfExists(temp);}
        }
        if(!Files.isRegularFile(jar,LinkOption.NOFOLLOW_LINKS)||Files.size(jar)!=bytes.length||!Arrays.equals(bytes,Files.readAllBytes(jar)))throw new IOException("PROFILE93_CAPSULE_CHANGED");
        return argument(jar,game);
    }
    static void apply(Path game,String mode,List<String> command,Map<String,String> env)throws IOException {
        if(command.isEmpty())throw new IOException("PROFILE93_EMPTY_COMMAND");
        String name=Path.of(command.get(0)).getFileName().toString().toLowerCase(Locale.ROOT);
        boolean direct=Set.of("java","java.exe","javaw","javaw.exe").contains(name);
        if(!direct&&!"gradle".equals(mode))throw new IOException("PROFILE93_UNSUPPORTED_LAUNCHER_USE_JAVA_OR_PROJECT_GRADLE");
        String option=install(game);
        // The per-launch environment requires MaRed to see real premain proof, not an asserted property.
        env.put(REQUIRED,ProfileAgent93.encode(game));
        if(direct)command.add(1,option);else env.put(AGENT,option);
    }
    public static String forGradle(String supplied,Path bundled,Path game)throws IOException {
        String option;
        if(supplied==null) {
            RuntimeFiles.safe(bundled);
            if(!Files.isRegularFile(bundled,LinkOption.NOFOLLOW_LINKS)||Files.size(bundled)>256*1024)throw new IOException("PROFILE93_CAPSULE_INVALID");
            // A live game never opens the admission agent directly from build/.
            option=persist(game,Files.readAllBytes(bundled));
        } else option=supplied;
        String suffix="="+ProfileAgent93.encode(game);
        if(!option.startsWith("-javaagent:")||!option.endsWith(suffix))throw new IOException("PROFILE93_GRADLE_PROFILE_MISMATCH");
        Path jar=Path.of(option.substring(11,option.length()-suffix.length()));
        if(!argument(jar,game).equals(option))throw new IOException("PROFILE93_GRADLE_ARGUMENT_INVALID");
        return option;
    }
    public static boolean transientArgument(String value) {
        if(!value.startsWith("-javaagent:"))return false;
        int end=value.indexOf('=',11);if(end<0)return false;
        String path=value.substring(11,end).replace('\\','/');
        return path.endsWith("/profile-admission93.jar")||path.matches(".*/admission93/profile-[a-f0-9]{64}\\.jar");
    }
}
