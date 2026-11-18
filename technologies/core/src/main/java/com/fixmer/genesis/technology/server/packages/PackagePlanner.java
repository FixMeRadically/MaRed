package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Read-only comparison. A successful plan is not an installation receipt or a lock on the profile. */
public final class PackagePlanner {
    private PackagePlanner(){}
    public enum Action { ADD, RESTORE_MISSING, REPLACE, REMOVE, KEEP, ALREADY_ABSENT, CONFLICT }
    public record Change(String path,Action action,String reason,String expectedBefore,String expectedAfter,String observedBefore,boolean existed){public Change{Objects.requireNonNull(path);Objects.requireNonNull(action);Objects.requireNonNull(reason);}}
    public record Plan(UUID distribution,UUID fromRelease,UUID toRelease,Side side,String packageSha256,Path profileRoot,List<Change> changes){
        public Plan{changes=List.copyOf(changes);}public boolean ready(){return changes.stream().noneMatch(c->c.action()==Action.CONFLICT);}
        public long writesPlanned(){return changes.stream().filter(c->Set.of(Action.ADD,Action.RESTORE_MISSING,Action.REPLACE,Action.REMOVE).contains(c.action())).count();}
    }
    public static Plan plan(PackageArchive.Verified previous,PackageArchive.Verified next,Target target,Path profile)throws IOException{
        Manifest desired=next.manifest(),old=previous==null?null:previous.manifest();try{target.check(desired);}catch(IllegalArgumentException bad){throw new IOException(bad.getMessage(),bad);}
        if(old!=null){
            if(!old.distribution().equals(desired.distribution())||!old.minecraft().equals(desired.minecraft()))throw new IOException("BASE_DISTRIBUTION_OR_MINECRAFT_MISMATCH");
            if(old.release().equals(desired.release())){if(!previous.manifestSha256().equals(next.manifestSha256()))throw new IOException("RELEASE_ID_REUSED_WITH_DIFFERENT_CONTENT");}
            else if(!old.release().equals(desired.parent()))throw new IOException("WRONG_BASE_RELEASE");
        }
        profile=profile.toAbsolutePath().normalize();PackageFiles.safe(profile);if(!Files.isDirectory(profile,LinkOption.NOFOLLOW_LINKS))throw new IOException("PROFILE_DIRECTORY_REQUIRED");
        Path mods=profile.resolve("mods");PackageFiles.safe(mods);if(Files.exists(mods,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(mods,LinkOption.NOFOLLOW_LINKS))throw new IOException("MODS_DIRECTORY_REQUIRED");
        var actualNames=new HashMap<String,List<String>>();
        if(Files.isDirectory(mods))try(var stream=Files.list(mods)){
            var iterator=stream.iterator();int count=0;while(iterator.hasNext()){if(++count>4096)throw new IOException("PROFILE_ENTRY_COUNT_LIMIT");String name="mods/"+iterator.next().getFileName();actualNames.computeIfAbsent(name.toLowerCase(Locale.ROOT),k->new ArrayList<>()).add(name);}
        }
        var before=index(old==null?List.of():old.forSide(target.side()));var after=index(desired.forSide(target.side()));var paths=new TreeSet<String>();paths.addAll(before.keySet());paths.addAll(after.keySet());
        // A case-only rename is ambiguous on Windows; require a separately reviewed migration.
        var folded=new HashSet<String>();for(String path:paths)if(!folded.add(path.toLowerCase(Locale.ROOT)))throw new IOException("CROSS_RELEASE_CASE_COLLISION");
        var changes=new ArrayList<Change>();for(String path:paths){
            Entry was=before.get(path),want=after.get(path);String oldHash=was==null?null:was.sha256(),newHash=want==null?null:want.sha256();Path file=profile.resolve(path);
            List<String> aliases=actualNames.getOrDefault(path.toLowerCase(Locale.ROOT),List.of());
            if(aliases.size()>1||aliases.size()==1&&!aliases.get(0).equals(path)){changes.add(new Change(path,Action.CONFLICT,"CASE_COLLISION_ON_DISK",oldHash,newHash,null,true));continue;}
            String disk=null;boolean exists=Files.exists(file,LinkOption.NOFOLLOW_LINKS);
            if(exists)try{PackageFiles.safe(file);disk=PackageFiles.digest(file,MAX_FILE);}catch(IOException blocked){changes.add(new Change(path,Action.CONFLICT,"UNREADABLE_LINKED_SPECIAL_OR_OVERSIZED_FILE",oldHash,newHash,null,true));continue;}
            Action action;String reason;
            if(was==null){action=exists?Action.CONFLICT:Action.ADD;reason=exists?"UNMANAGED_FILE_ALREADY_EXISTS":"NEW_MANAGED_FILE";}
            else if(!exists){action=want==null?Action.ALREADY_ABSENT:Action.RESTORE_MISSING;reason=want==null?"OWNED_FILE_ALREADY_MISSING":"RESTORE_MISSING_OWNED_FILE";}
            else if(!disk.equals(oldHash)){action=Action.CONFLICT;reason="MANAGED_FILE_LOCALLY_CHANGED";}
            else if(want==null){action=Action.REMOVE;reason="REMOVED_FROM_MANAGED_INVENTORY";}
            else if(oldHash.equals(newHash)){action=Action.KEEP;reason="UNCHANGED_MANAGED_FILE";}
            else{action=Action.REPLACE;reason="MANAGED_FILE_UPDATE";}
            changes.add(new Change(path,action,reason,oldHash,newHash,disk,exists));
        }
        return new Plan(desired.distribution(),old==null?null:old.release(),desired.release(),target.side(),next.archiveSha256(),profile,changes);
    }
    private static Map<String,Entry> index(List<Entry> entries){var result=new HashMap<String,Entry>();for(Entry entry:entries)result.put(entry.path(),entry);return result;}
}
