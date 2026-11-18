package com.fixmer.genesis.supervisor;
import com.fixmer.genesis.admission.ProfileAgent93;
import java.io.*;import java.nio.file.*;import java.util.*;
public final class AdmissionChecks93 {
 private static int n;
 private static void ok(boolean v){n++;if(!v)throw new AssertionError("check "+n);}
 private static void rejects(RunnableIO r)throws Exception {try{r.run();throw new AssertionError("accepted invalid input");}catch(IOException expected){n++;}}
 private interface RunnableIO {void run()throws Exception;}
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("admission93 "),game=Files.createDirectory(root.resolve("игра space")),jar=Files.write(root.resolve("profile-admission93.jar"),new byte[]{1});
  String encoded=ProfileAgent93.encode(game),option=ProfileLaunch93.argument(jar,game);
  ok(ProfileAgent93.decode(encoded).equals(game));ok(option.equals("-javaagent:"+jar+"="+encoded));
  rejects(()->ProfileAgent93.decode(null));rejects(()->ProfileAgent93.decode(""));rejects(()->ProfileAgent93.decode(encoded+"="));rejects(()->ProfileAgent93.decode("__8"));
  rejects(()->ProfileAgent93.decode(Base64.getUrlEncoder().withoutPadding().encodeToString("relative".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  rejects(()->ProfileAgent93.decode(Base64.getUrlEncoder().withoutPadding().encodeToString((game+"/../other").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
  rejects(()->ProfileLaunch93.argument(root.resolve("missing.jar"),game));
  Path bad=Files.write(root.resolve("bad=path.jar"),new byte[]{1});rejects(()->ProfileLaunch93.argument(bad,game));
  rejects(()->ProfileLaunch93.forGradle(null,jar,game));ok(ProfileLaunch93.forGradle(option,jar,game).equals(option));
  rejects(()->ProfileLaunch93.forGradle(option,jar,root));rejects(()->ProfileLaunch93.forGradle("-Xmx1G",jar,game));
  ok(ProfileLaunch93.transientArgument(option));ok(!ProfileLaunch93.transientArgument("-javaagent:/third-party-agent.jar=anything"));
  ok(!ProfileLaunch93.transientArgument("-javaagent:/third-party-agent.jar=profile-admission93.jar="+encoded));
  Path hash=Files.createDirectories(game.resolve("config/mared/dev/admission93")).resolve("profile-"+"a".repeat(64)+".jar");
  ok(ProfileLaunch93.transientArgument("-javaagent:"+hash+"="+encoded));
  ok(ProfileLaunch93.transientArgument("-javaagent:C:\\space dir\\admission93\\profile-"+"a".repeat(64)+".jar="+encoded));
  ok(!ProfileAgent93.early(game));ok(!ProfileAgent93.held(game));
  var manifest=new java.util.jar.Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");manifest.getMainAttributes().putValue("Premain-Class",ProfileAgent93.class.getName());
  try(var out=new java.util.jar.JarOutputStream(Files.newOutputStream(jar),manifest)) {
   out.putNextEntry(new java.util.jar.JarEntry("com/fixmer/genesis/admission/ProfileAgent93.class"));
   try(var in=ProfileAgent93.class.getResourceAsStream("ProfileAgent93.class")){out.write(in.readAllBytes());}out.closeEntry();
  }
  String pinned=ProfileLaunch93.forGradle(null,jar,game);
  ok(!pinned.equals(option));ok(pinned.contains("admission93"));ok(ProfileLaunch93.transientArgument(pinned));ok(ProfileLaunch93.forGradle(null,jar,game).equals(pinned));
  var captured=JavaExecCommand.classic(root.resolve("java.exe").toString(),List.of(pinned,"-Xmx1G","-javaagent:/other.jar=keep"),List.of("game.jar"),"Game",List.of("--test","space value"));
  ok(!captured.contains(pinned));ok(captured.contains("-javaagent:/other.jar=keep"));ok(captured.get(captured.size()-1).equals("space value"));
  Path pinnedPath=Path.of(pinned.substring(11,pinned.lastIndexOf('=')));Files.writeString(pinnedPath,"tampered");rejects(()->ProfileLaunch93.forGradle(null,jar,game));
  rejects(()->ProfileLaunch93.apply(game,"configured",new ArrayList<>(List.of("powershell.exe","launch.ps1")),new HashMap<>()));
  rejects(()->ProfileAgent93.require(game,encoded));ok(!ProfileAgent93.held(game));
  System.setProperty("genesis.profile.early93","true");rejects(()->ProfileAgent93.require(game,encoded));
  try(var w=ProfileLease92.writer(game)){rejects(()->ProfileLease92.start(game));}
  ProfileLease92.start(game);ok(ProfileAgent93.held(game));ok(!ProfileAgent93.early(game));
  ProfileLease92.start(game);rejects(()->ProfileLease92.writer(game));
  rejects(()->ProfileAgent93.enter(Files.createDirectory(root.resolve("other"))));
  // A same-VM writer probe must not reopen/close the reader file and release POSIX locks.
  ok(Files.readString(game.resolve("config/mared/dev/profile-admission-last93.properties")).contains("phase=mod_constructor_only"));
  System.out.println("Admission93 actual encoding/arguments/bridge/lifetime-writer checks passed: "+n+"; process acceptance is a separate harness.");
 }
}
