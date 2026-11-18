package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;import java.net.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.security.cert.*;import java.security.spec.*;import java.time.*;import java.time.format.DateTimeFormatter;import java.util.*;import javax.security.auth.x500.X500Principal;
/** JDK-only private publisher CA and server certificate. No system trust-store writes. */
public final class TlsIdentity159 {
 private TlsIdentity159(){}
 private static byte[] join(byte[]...v){var o=new ByteArrayOutputStream();for(byte[] x:v)o.writeBytes(x);return o.toByteArray();}
 private static byte[] der(int tag,byte[] b){var o=new ByteArrayOutputStream();o.write(tag);int n=b.length;if(n<128)o.write(n);else{int s=(32-Integer.numberOfLeadingZeros(n)+7)/8;o.write(128|s);for(int i=s-1;i>=0;i--)o.write(n>>>(8*i));}o.writeBytes(b);return o.toByteArray();}
 private static byte[] seq(byte[]...v){return der(48,join(v));}private static byte[] oid(String hex){return der(6,HexFormat.of().parseHex(hex));}private static byte[] integer(java.math.BigInteger n){return der(2,n.toByteArray());}
 private static final byte[] ALG=seq(oid("2b6570"));
 private static byte[] ext(String id,boolean critical,byte[] b){return seq(oid(id),critical?der(1,new byte[]{-1}):new byte[0],der(4,b));}
 private static byte[] time(Instant at){return der(24,DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC).format(at).getBytes(StandardCharsets.US_ASCII));}
 private static byte[] cert(PublicKey subject,PrivateKey signer,byte[] issuer,byte[] name,Instant now,boolean ca,String host)throws Exception{
  var e=new ArrayList<byte[]>();e.add(ext("551d13",true,ca?seq(der(1,new byte[]{-1})):seq()));e.add(ext("551d0f",true,der(3,ca?new byte[]{1,6}:new byte[]{5,(byte)160})));
  if(!ca){e.add(ext("551d25",false,seq(oid("2b06010505070301"))));e.add(ext("551d11",false,seq(Tls78.ip(host)?der(135,InetAddress.getByName(host).getAddress()):der(130,host.getBytes(StandardCharsets.US_ASCII)))));}
  byte[] body=seq(der(160,integer(java.math.BigInteger.TWO)),integer(new java.math.BigInteger(159,new SecureRandom()).add(java.math.BigInteger.ONE)),ALG,issuer,seq(time(now.minusSeconds(300)),time(now.plusSeconds((ca?3650L:90L)*86400))),name,subject.getEncoded(),der(163,seq(e.toArray(byte[][]::new))));
  var s=Signature.getInstance("Ed25519");s.initSign(signer);s.update(body);return seq(body,ALG,der(3,join(new byte[]{0},s.sign())));
 }
 private static byte[] pem(String label,byte[] b){return ("-----BEGIN "+label+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(b)+"\n-----END "+label+"-----\n").getBytes(StandardCharsets.US_ASCII);}
 public static Map<String,String> ensure(Path identity,Path directory,String endpoint)throws Exception{
  String base=RemoteOffer69.base(endpoint),host=Tls78.host(URI.create(base).getHost());identity=FirstLaunchFiles.safe(identity);directory=FirstLaunchFiles.safe(directory);Files.createDirectories(directory);FirstLaunchFiles.privateMode(directory);
  byte[] pub=FirstLaunchFiles.read(identity.resolve("public.der"),256);var publisher=RemoteOffer69.publicKey(pub);var secret=DedicatedRelease74.privateKey(identity);String signer=FirstLaunchFiles.sha(pub);
  Path target=FirstLaunchFiles.safe(directory.resolve(FirstLaunchFiles.sha((signer+"\n"+host).getBytes(StandardCharsets.UTF_8))));
  if(Files.exists(target)){
   var chain=CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(FirstLaunchFiles.read(target.resolve("server.pem"),1048576))).stream().map(c->(X509Certificate)c).toList();
   if(chain.size()!=2||!Arrays.equals(chain.get(1).getPublicKey().getEncoded(),pub)||chain.get(1).getBasicConstraints()<0)throw new IOException("HTTPS159_IDENTITY_CHANGED");chain.get(1).verify(publisher);chain.get(0).verify(publisher);PinnedTls159.verify(chain.toArray(X509Certificate[]::new),signer);Tls78.inspect(target.resolve("server.pem"),base);
   String text=new String(FirstLaunchFiles.read(target.resolve("private.pem"),16384),StandardCharsets.US_ASCII);byte[] raw=Base64.getMimeDecoder().decode(text.replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----",""));var k=(java.security.interfaces.RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(raw));if(!k.getModulus().equals(((java.security.interfaces.RSAPublicKey)chain.get(0).getPublicKey()).getModulus()))throw new IOException("HTTPS159_KEY_PAIR");
  }else{
   Path stage=Files.createTempDirectory(directory,"identity159-");boolean done=false;try{
    FirstLaunchFiles.privateMode(stage);var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var leaf=g.generateKeyPair();Instant now=Instant.now();byte[] issuer=new X500Principal("CN=MaRed publisher "+signer.substring(0,16)).getEncoded(),name=new X500Principal("CN=MaRed distribution").getEncoded();
    FirstLaunchFiles.write(stage.resolve("server.pem"),join(pem("CERTIFICATE",cert(leaf.getPublic(),secret,issuer,name,now,false,host)),pem("CERTIFICATE",cert(publisher,secret,issuer,issuer,now,true,host))),false);FirstLaunchFiles.write(stage.resolve("private.pem"),pem("PRIVATE KEY",leaf.getPrivate().getEncoded()),false);Tls78.inspect(stage.resolve("server.pem"),base);try(var input=Files.newInputStream(stage.resolve("server.pem"))){var issued=CertificateFactory.getInstance("X.509").generateCertificates(input).stream().map(c->(X509Certificate)c).toArray(X509Certificate[]::new);PinnedTls159.verify(issued,signer);}Files.move(stage,target);done=true;
   }finally{if(!done)try(var paths=Files.walk(stage)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
  }
  return Map.of("cert",target.resolve("server.pem").toString(),"key",target.resolve("private.pem").toString(),"signer",signer,"endpoint",base);
 }
 public static void main(String[] a){try{if(a.length!=3)throw new IOException("HTTPS159_ARGUMENTS");System.out.println(DedicatedTools74.json(ensure(Path.of(a[0]),Path.of(a[1]),a[2])));}catch(Exception e){String c=e.getMessage();System.err.println(c!=null&&c.matches("HTTPS(?:78|159)_[A-Z0-9_]{1,80}")?c:"HTTPS159_IDENTITY_FAILED");System.exit(2);}}
}
