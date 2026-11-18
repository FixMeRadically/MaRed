package com.fixmer.genesis.technology.workspace.collaboration121;
import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;
/** Immutable exact build input. Hash is integrity, not remote authentication or deployment authority. */
public final class WorkspaceRelease121 {
 public record File(String path,long revision,byte[] bytes){public File{path=WorkspacePolicy121.path(path);if(revision<1||bytes==null||bytes.length>8*1024*1024)throw new IllegalArgumentException("FILE");bytes=bytes.clone();}@Override public byte[] bytes(){return bytes.clone();}}
 public record Snapshot(UUID project,long revision,UUID author,Map<String,File> files,String hash){public Snapshot{Objects.requireNonNull(project);Objects.requireNonNull(author);files=Map.copyOf(files);if(revision<1||!hash.equals(digest(files)))throw new IllegalArgumentException("SNAPSHOT");}}
 public record Approval(UUID reviewer,String hash,long policyRevision){public Approval{Objects.requireNonNull(reviewer);if(hash==null||!hash.matches("[a-f0-9]{64}")||policyRevision<0)throw new IllegalArgumentException("APPROVAL");}}
 private WorkspaceRelease121(){}
 public static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
 public static String digest(Map<String,File> files){
  if(files.isEmpty()||files.size()>512)throw new IllegalArgumentException("FILES");long total=0;var s=new StringBuilder();var folded=new HashSet<String>();
  for(var e:new TreeMap<>(files).entrySet()){var f=e.getValue();if(!e.getKey().equals(f.path)||!folded.add(f.path.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("PATH_COLLISION");String normalized=f.path.toLowerCase(Locale.ROOT);for(String prior:folded)if(!prior.equals(normalized)&&(prior.startsWith(normalized+"/")||normalized.startsWith(prior+"/")))throw new IllegalArgumentException("PATH_COLLISION");total+=f.bytes.length;if(total>32L*1024*1024)throw new IllegalArgumentException("LIMIT");s.append(f.path).append('\0').append(f.revision).append('\0').append(sha(f.bytes)).append('\n');}return sha(s.toString().getBytes(StandardCharsets.UTF_8));
 }
 public static Snapshot freeze(UUID project,long revision,UUID author,Map<String,File> selected){return new Snapshot(project,revision,author,selected,digest(selected));}
 public static Approval approve(WorkspacePolicy121 policy,UUID reviewer,Snapshot snapshot){for(String p:snapshot.files.keySet())policy.require(reviewer,WorkspacePolicy121.Capability.APPROVE,p);return new Approval(reviewer,snapshot.hash,policy.revision());}
 public static Map<String,File> build(WorkspacePolicy121 policy,UUID builder,Snapshot snapshot,Approval approval){
  if(!snapshot.hash.equals(approval.hash)||approval.policyRevision!=policy.revision())throw new SecurityException("STALE_APPROVAL");
  for(String p:snapshot.files.keySet()){policy.require(approval.reviewer,WorkspacePolicy121.Capability.APPROVE,p);policy.require(builder,WorkspacePolicy121.Capability.BUILD,p);}if(!snapshot.hash.equals(digest(snapshot.files)))throw new SecurityException("CHANGED");return snapshot.files;
 }
}
