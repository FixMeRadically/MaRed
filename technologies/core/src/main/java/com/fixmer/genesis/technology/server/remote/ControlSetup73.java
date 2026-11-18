package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
/** Explicit setup only: installs matching persistent identity, never overwrites/rotates an existing key. */
public final class ControlSetup73 {
 public static void install(Path server,Path source)throws Exception{
  server=FirstLaunchFiles.safe(server);source=FirstLaunchFiles.safe(source);if(!Files.isRegularFile(server.resolve("server.properties")))throw new java.io.IOException("SERVER_PROFILE_REQUIRED");
  byte[] pub=FirstLaunchFiles.read(source.resolve("public.der"),256),priv=FirstLaunchFiles.read(source.resolve("private.der"),256);
  PrivateKey key=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(priv));ControlWire73.verify(RemoteOffer69.publicKey(pub),ControlWire73.sign(key,Map.of("test","73")));
  Path target=server.resolve("config/mared/publisher73");if(Files.exists(target)){
   if(!MessageDigest.isEqual(pub,FirstLaunchFiles.read(target.resolve("public.der"),256))||!MessageDigest.isEqual(priv,FirstLaunchFiles.read(target.resolve("private.der"),256)))throw new java.io.IOException("EXISTING_IDENTITY_DIFFERS");FirstLaunchFiles.privateMode(target);FirstLaunchFiles.privateMode(target.resolve("private.der"));return;
  }
  Files.createDirectories(target.getParent());Files.createDirectory(target);FirstLaunchFiles.privateMode(target);FirstLaunchFiles.write(target.resolve("private.der"),priv,false);FirstLaunchFiles.write(target.resolve("public.der"),pub,false);
 }
 public static void main(String[] args){try{if(args.length!=2)throw new IllegalArgumentException();install(Path.of(args[0]),Path.of(args[1]));System.out.println("Publisher73 installed/confirmed. Start server after setup. Private key was not printed.");}catch(Exception bad){System.err.println("CONTROL73_SETUP_REFUSED; keep the existing identity and inspect paths locally.");System.exit(2);}}
}
