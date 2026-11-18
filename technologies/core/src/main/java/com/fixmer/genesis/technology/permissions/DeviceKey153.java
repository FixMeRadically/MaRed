package com.fixmer.genesis.technology.permissions;
import com.fixmer.genesis.technology.server.firstlaunch.*;import com.fixmer.genesis.technology.server.remote.ControlWire73;import com.fixmer.genesis.technology.storage.AtomicSettings111;
import java.nio.file.*;import java.nio.channels.*;import java.security.*;import java.security.spec.*;import java.util.*;
/** A local installation key; private bytes never enter protocol fields or logs. */
public final class DeviceKey153 {
 private final PrivateKey secret;private final PublicKey publicKey;
 private DeviceKey153(PrivateKey secret,PublicKey publicKey){this.secret=secret;this.publicKey=publicKey;}
 public static DeviceKey153 load(Path directory)throws Exception{
  Files.createDirectories(FirstLaunchFiles.safe(directory));Path guard=FirstLaunchFiles.safe(directory.resolve("device153.lock"));
  try(var channel=FileChannel.open(guard,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);var lock=channel.tryLock()){
   if(lock==null)throw new java.io.IOException("AUTH153_KEY_BUSY");Path file=FirstLaunchFiles.safe(directory.resolve("device153.key"));
   if(!Files.exists(file)){var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();FirstLaunchFiles.write(file,ControlWire73.encode(Map.of("private",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),"public",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()))),false);FirstLaunchFiles.privateMode(file);}
   var data=ControlWire73.decode(FirstLaunchFiles.read(file,4096));if(!data.keySet().equals(Set.of("private","public")))throw new java.io.IOException("AUTH153_KEY");var factory=KeyFactory.getInstance("Ed25519");var key=new DeviceKey153(factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(data.get("private")))),factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(data.get("public")))));
   byte[] sample="MaRed key pair check 153".getBytes(java.nio.charset.StandardCharsets.US_ASCII);Signature check=Signature.getInstance("Ed25519");check.initVerify(key.publicKey);check.update(sample);if(!check.verify(Base64.getDecoder().decode(key.sign(Base64.getEncoder().encodeToString(sample)))))throw new java.io.IOException("AUTH153_KEY");return key;
  }
 }
 public String publicEncoded(){return Base64.getEncoder().encodeToString(publicKey.getEncoded());}
 public String fingerprint(){return FirstLaunchFiles.sha(publicKey.getEncoded());}
 public String sign(String challenge)throws Exception{byte[] bytes=Base64.getDecoder().decode(challenge);if(bytes.length<16||bytes.length>1024)throw new IllegalArgumentException("AUTH153_CHALLENGE");var signature=Signature.getInstance("Ed25519");signature.initSign(secret);signature.update(bytes);return Base64.getEncoder().encodeToString(signature.sign());}
}
