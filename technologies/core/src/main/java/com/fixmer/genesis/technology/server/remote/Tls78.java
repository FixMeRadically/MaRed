package com.fixmer.genesis.technology.server.remote;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import java.io.*;import java.net.*;import java.nio.file.*;import java.security.cert.*;import java.util.*;import javax.net.ssl.HttpsURLConnection;
/** Offline SAN/date inspection and a separate DEFAULT-trust HTTPS probe; never installs trust. */
public final class Tls78 {
 private Tls78(){}
 static String host(String raw){String s=raw.toLowerCase(Locale.ROOT);if(s.startsWith("[")&&s.endsWith("]"))s=s.substring(1,s.length()-1);if(s.isEmpty()||s.endsWith(".")||s.contains("%"))throw new IllegalArgumentException("HTTPS78_HOST");return s;}
 static boolean ip(String host){return host.indexOf(':')>=0||host.matches("[0-9.]+");}
 public static boolean matches(String hostname,Collection<List<?>> sans)throws Exception{
  String target=host(hostname);if(sans==null)return false;boolean numeric=ip(target);
  for(var san:sans){if(san.size()<2||!(san.get(0) instanceof Integer type)||!(san.get(1) instanceof String raw))continue;
   if(numeric&&type==7&&ip(raw)){if(Arrays.equals(InetAddress.getByName(target).getAddress(),InetAddress.getByName(host(raw)).getAddress()))return true;}
   else if(!numeric&&type==2){String pattern=raw.toLowerCase(Locale.ROOT);if(pattern.equals(target))return true;if(pattern.startsWith("*.")&&pattern.indexOf('*',1)<0){String suffix=pattern.substring(1);String prefix=target.endsWith(suffix)?target.substring(0,target.length()-suffix.length()):"";if(!prefix.isEmpty()&&!prefix.contains(".")&&pattern.substring(2).contains("."))return true;}}
  }return false;
 }
 public static Map<String,String> inspect(Path pem,String endpoint)throws Exception{
  URI url=URI.create(RemoteOffer69.base(endpoint));byte[] bytes=FirstLaunchFiles.read(pem,1048576);var chain=CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes));if(chain.isEmpty()||chain.size()>8)throw new IOException("HTTPS78_CERTIFICATE");X509Certificate leaf=(X509Certificate)chain.iterator().next();
  for(var certificate:chain)try{((X509Certificate)certificate).checkValidity();}catch(CertificateExpiredException|CertificateNotYetValidException invalid){throw new IOException("HTTPS78_CERTIFICATE_DATE");}
  if(!matches(url.getHost(),leaf.getSubjectAlternativeNames()))throw new IOException("HTTPS78_HOSTNAME");var usage=leaf.getExtendedKeyUsage();if(usage!=null&&!usage.contains("1.3.6.1.5.5.7.3.1")&&!usage.contains("2.5.29.37.0"))throw new IOException("HTTPS78_CERTIFICATE_USAGE");
  return Map.of("endpoint",url.toString(),"host",host(url.getHost()),"fingerprint",FirstLaunchFiles.sha(leaf.getEncoded()),"expires",Long.toString(leaf.getNotAfter().getTime()),"clientTrust","NOT_TESTED");
 }
 private static byte[] get(URI uri,int limit,long deadline)throws Exception{
  if(System.nanoTime()>deadline)throw new IOException("HTTPS78_TIMEOUT");
  HttpsURLConnection connection=(HttpsURLConnection)uri.toURL().openConnection();PinnedTls159.apply(connection);connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setRequestProperty("Accept-Encoding","identity");
  try{if(connection.getResponseCode()!=200||connection.getContentLengthLong()>limit||!Set.of("identity","").contains(Objects.toString(connection.getHeaderField("Content-Encoding"),"")))throw new IOException("HTTPS78_HTTP");try(var in=connection.getInputStream();var out=new ByteArrayOutputStream()){byte[] block=new byte[1024];while(true){long remaining=(deadline-System.nanoTime())/1_000_000;if(remaining<=0)throw new IOException("HTTPS78_TIMEOUT");connection.setReadTimeout((int)Math.max(1,Math.min(5000,remaining)));int n=in.read(block,0,Math.min(block.length,limit+1-out.size()));if(n<0)break;out.write(block,0,n);if(out.size()>limit)throw new IOException("HTTPS78_HTTP");}byte[] bytes=out.toByteArray();if(connection.getContentLengthLong()>=0&&connection.getContentLengthLong()!=bytes.length)throw new IOException("HTTPS78_HTTP");return bytes;}}catch(ConnectException refused){throw new IOException("HTTPS78_CONNECTION_REFUSED",refused);}catch(SocketTimeoutException timeout){throw new IOException("HTTPS78_TIMEOUT",timeout);}finally{connection.disconnect();}
 }
 public static Map<String,String> probe(String endpoint,Path expectedPublisher)throws Exception{
  long deadline=System.nanoTime()+15_000_000_000L;String base=RemoteOffer69.base(endpoint);byte[] trusted=FirstLaunchFiles.read(expectedPublisher,256);PinnedTls159.hint(base,FirstLaunchFiles.sha(trusted));var offer=RemoteOffer69.decode(get(URI.create(base+"offer.bin"),8192,deadline));byte[] key=get(URI.create(base+"public.der"),256,deadline);if(!Arrays.equals(key,trusted)||!offer.endpoint().equals(base))throw new IOException("HTTPS78_ENDPOINT_IDENTITY");offer.verify(RemoteOffer69.publicKey(trusted),get(URI.create(base+"offer.sig"),64,deadline));offer.validAt(System.currentTimeMillis());
  return Map.of("endpoint",base,"release",offer.release().toString(),"archive",offer.archiveSha(),"signer",offer.signer(),"clientTrust","VERIFIED_THIS_JDK");
 }
 public static void main(String[] args){try{Map<String,String> data;if(args.length==2&&Set.of("public","public-history").contains(args[0])){var offer=RemoteUpdateCli69.verifyPublic(Path.of(args[1]),args[0].equals("public"));data=Map.of("endpoint",offer.endpoint(),"archive","client-"+offer.archiveSha()+".zip","game",offer.game(),"minecraft",offer.minecraft(),"neoforge",offer.neoforge(),"release",offer.release().toString());}else if(args.length==3&&args[0].equals("inspect"))data=inspect(Path.of(args[1]),args[2]);else if(args.length==3&&args[0].equals("probe"))data=probe(args[1],Path.of(args[2]));else throw new IOException("HTTPS78_ARGUMENTS");System.out.println(DedicatedTools74.json(data));}catch(Exception error){String code=error.getMessage();if("REMOTE_OFFER_EXPIRED".equals(code))code="HTTPS78_OFFER_TIME";if(code==null||!code.matches("HTTPS78_[A-Z0-9_]{1,80}"))code=error instanceof javax.net.ssl.SSLException?"HTTPS78_TLS_TRUST":"HTTPS78_CHECK_FAILED";System.err.println(code);System.exit(2);}}
}
