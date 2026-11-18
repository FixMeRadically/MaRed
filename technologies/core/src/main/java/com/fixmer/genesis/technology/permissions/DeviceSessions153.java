package com.fixmer.genesis.technology.permissions;
import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;import com.fixmer.genesis.technology.server.remote.ControlWire73;
import java.security.*;import java.security.spec.*;import java.util.*;import java.util.function.LongSupplier;
/** Signed one-use challenges pinned to the actual connection object, account and server instance. */
public final class DeviceSessions153 {
 private record Session(Object connection,String ticket,PublicKey key,String fingerprint,byte[] challenge,long at,boolean verified){}
 private static final class Replay{long counter;final Set<String> retry=new HashSet<>();final Map<String,String> seen=new LinkedHashMap<>();}
 private final Map<UUID,Replay> replay=new HashMap<>();
 private final Map<UUID,Session> sessions=new HashMap<>();private final DeviceTrust153 trust;private final LongSupplier clock;private final String server=UUID.randomUUID().toString();
 public DeviceSessions153(DeviceTrust153 trust,LongSupplier clock){this.trust=trust;this.clock=clock;}
 public synchronized Map<String,String> begin(UUID actor,Object connection,String encoded)throws Exception{
  if(encoded.length()>128||sessions.size()>=256&&!sessions.containsKey(actor))throw new IllegalArgumentException("AUTH153_LIMIT");var key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));String ticket=UUID.randomUUID().toString();byte[] nonce=new byte[32];new SecureRandom().nextBytes(nonce);
  byte[] challenge=ControlWire73.encode(Map.of("domain","MaRed-device153","actor",actor.toString(),"server",server,"nonce",Base64.getEncoder().encodeToString(nonce),"ticket",ticket));String fingerprint=FirstLaunchFiles.sha(key.getEncoded());sessions.put(actor,new Session(connection,ticket,key,fingerprint,challenge,clock.getAsLong(),false));replay.put(actor,new Replay());return Map.of("ticket153",ticket,"challenge153",Base64.getEncoder().encodeToString(challenge),"fingerprint153",fingerprint);
 }
 public synchronized void prove(UUID actor,Object connection,String ticket,String encoded)throws Exception{
  var session=sessions.get(actor);if(session==null||session.connection!=connection||session.verified||!session.ticket.equals(ticket)||clock.getAsLong()-session.at>=30_000_000_000L||encoded.length()>128)throw new SecurityException("AUTH153_CHALLENGE");
  sessions.remove(actor);var signature=Signature.getInstance("Ed25519");signature.initVerify(session.key);signature.update(session.challenge);if(!signature.verify(Base64.getDecoder().decode(encoded)))throw new SecurityException("AUTH153_SIGNATURE");sessions.put(actor,new Session(connection,ticket,session.key,session.fingerprint,session.challenge,session.at,true));
 }
 public static String requestBody(Map<String,String> request)throws Exception{var fields=new TreeMap<>(request);fields.remove("proof153");return Base64.getEncoder().encodeToString(("MaRed-request153\n"+FirstLaunchFiles.sha(com.fixmer.genesis.technology.workspace.live122.WorkspaceCodec122.encode(fields,32700))).getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
 public synchronized boolean request(UUID actor,Object connection,Map<String,String> request)throws Exception{
  var session=sessions.get(actor);if(!verified(actor,connection)||session==null||!session.ticket.equals(request.get("ticket153")))throw new SecurityException("AUTH153_SESSION");
  String proof=request.get("proof153");if(proof==null||proof.length()>128)throw new SecurityException("AUTH153_SIGNATURE");
  String body=requestBody(request);var verifier=Signature.getInstance("Ed25519");verifier.initVerify(session.key);verifier.update(Base64.getDecoder().decode(body));if(!verifier.verify(Base64.getDecoder().decode(proof)))throw new SecurityException("AUTH153_SIGNATURE");
  long counter=Long.parseLong(request.get("counter153"));var state=replay.get(actor);String id=request.get("request");String previous=state.seen.get(id);
  if(previous!=null){if(!previous.equals(body))throw new SecurityException("AUTH153_REUSED");return state.retry.remove(id);}
  if(counter<=state.counter)throw new SecurityException("AUTH153_REPLAY");state.counter=counter;state.seen.put(id,body);while(state.seen.size()>64){String oldest=state.seen.keySet().iterator().next();state.seen.remove(oldest);state.retry.remove(oldest);}return true;
 }
 public synchronized void rejected(UUID actor,Object connection,String request){var session=sessions.get(actor);var state=replay.get(actor);if(session!=null&&session.connection==connection&&state!=null&&state.seen.containsKey(request))state.retry.add(request);}
 public synchronized String fingerprint(UUID actor,Object connection){var session=sessions.get(actor);return session!=null&&session.connection==connection&&session.verified?session.fingerprint:"";}
 public synchronized boolean verified(UUID actor,Object connection){String fingerprint=fingerprint(actor,connection);return !fingerprint.isEmpty()&&trust.allows(actor,fingerprint);}
 public synchronized void disconnect(UUID actor,Object connection){var session=sessions.get(actor);if(session!=null&&session.connection==connection){sessions.remove(actor);replay.remove(actor);}}
}
