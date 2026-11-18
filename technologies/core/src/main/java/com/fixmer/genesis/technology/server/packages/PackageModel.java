package com.fixmer.genesis.technology.server.packages;

import java.util.*;

/** Version 1 is a complete inventory of managed mod JARs, never executable instructions. */
public final class PackageModel {
    public static final int SCHEMA=1, PROTOCOL=1, MAX_FILES=256, MAX_MANIFEST=131072;
    public static final long MAX_FILE=64L*1024*1024, MAX_TOTAL=256L*1024*1024, MAX_ARCHIVE=300L*1024*1024;
    private PackageModel(){}
    public enum Side { CLIENT, SERVER, BOTH;
        public boolean includes(Side target){return target!=BOTH&&(this==BOTH||this==target);}
    }
    public record Entry(String path,Side side,long bytes,String sha256){
        public Entry {path=modPath(path);Objects.requireNonNull(side);if(bytes<1||bytes>MAX_FILE)throw new IllegalArgumentException("FILE_SIZE_LIMIT");hash(sha256);}
    }
    public record Manifest(UUID distribution,UUID release,UUID parent,String version,String minecraft,String loader,
                           String loaderVersion,int minProtocol,int maxProtocol,String signer,List<Entry> files){
        public Manifest {
            nonzero(distribution);nonzero(release);if(parent!=null){nonzero(parent);if(parent.equals(release))throw new IllegalArgumentException("SELF_PARENT");}
            text(version);text(minecraft);text(loaderVersion);if(!"neoforge".equals(loader))throw new IllegalArgumentException("UNSUPPORTED_LOADER");
            if(minProtocol<1||maxProtocol<minProtocol||maxProtocol>10000)throw new IllegalArgumentException("INVALID_PROTOCOL_RANGE");hash(signer);
            files=List.copyOf(files);if(files.size()>MAX_FILES)throw new IllegalArgumentException("FILE_COUNT_LIMIT");
            var seen=new HashSet<String>();long total=0;for(Entry entry:files){if(!seen.add(entry.path().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("DUPLICATE_OR_CASE_COLLIDING_PATH");total+=entry.bytes();}if(total>MAX_TOTAL)throw new IllegalArgumentException("TOTAL_SIZE_LIMIT");
            files=files.stream().sorted(Comparator.comparing(Entry::path)).toList();
        }
        public List<Entry> forSide(Side side){if(side==null||side==Side.BOTH)throw new IllegalArgumentException("TARGET_SIDE_REQUIRED");return files.stream().filter(e->e.side().includes(side)).toList();}
    }
    public record Target(UUID distribution,Side side,String minecraft,String loaderVersion,int protocol){
        public Target{nonzero(distribution);if(side==null||side==Side.BOTH)throw new IllegalArgumentException("TARGET_SIDE_REQUIRED");text(minecraft);text(loaderVersion);if(protocol<1)throw new IllegalArgumentException("INVALID_PROTOCOL");}
        public void check(Manifest manifest){
            if(!distribution.equals(manifest.distribution()))throw new IllegalArgumentException("WRONG_DISTRIBUTION");
            if(!minecraft.equals(manifest.minecraft()))throw new IllegalArgumentException("WRONG_MINECRAFT_VERSION");
            if(!loaderVersion.equals(manifest.loaderVersion()))throw new IllegalArgumentException("WRONG_NEOFORGE_VERSION");
            if(protocol<manifest.minProtocol()||protocol>manifest.maxProtocol())throw new IllegalArgumentException("PROTOCOL_OUT_OF_RANGE");
        }
    }
    static String modPath(String path){
        if(path==null||!path.matches("mods/[A-Za-z0-9][A-Za-z0-9._+-]{0,119}\\.jar"))throw new IllegalArgumentException("UNSAFE_MOD_PATH");
        String name=path.substring(5);if(name.contains(".."))throw new IllegalArgumentException("UNSAFE_MOD_PATH");
        String stem=name.substring(0,name.indexOf('.')).toUpperCase(Locale.ROOT);
        if(stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))throw new IllegalArgumentException("WINDOWS_RESERVED_NAME");return path;
    }
    static String hash(String hash){if(hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("INVALID_SHA256");return hash;}
    static String text(String text){if(text==null||!text.matches("[A-Za-z0-9][A-Za-z0-9._+~-]{0,95}"))throw new IllegalArgumentException("INVALID_VERSION_TEXT");return text;}
    static void nonzero(UUID id){if(id==null||id.equals(new UUID(0,0)))throw new IllegalArgumentException("INVALID_UUID");}
}
