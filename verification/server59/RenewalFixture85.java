package com.fixmer.genesis.verification;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.genesis.technology.server.firstlaunch.*;import java.nio.file.*;import java.security.*;import java.security.spec.*;
/** Test-only re-signing of a local fixture's expiry. Not in controller runtime. */
public final class RenewalFixture85 {
 public static void main(String[] a)throws Exception{Path pair=Path.of(a[0]),id=Path.of(a[1]);var o=RemoteUpdateCli69.verifyPublic(pair.resolve("public"),false);var key=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(id.resolve("private.der"))));long now=System.currentTimeMillis();var expired=new RemoteOffer69(o.game(),o.endpoint(),o.distribution(),o.release(),o.parent(),o.minecraft(),o.neoforge(),o.sequence(),now-7200000,now-3600000,o.bytes(),o.archiveSha(),o.signer());FirstLaunchFiles.write(pair.resolve("public/offer.bin"),expired.encode(),true);FirstLaunchFiles.write(pair.resolve("public/offer.sig"),expired.sign(key),true);}
}
