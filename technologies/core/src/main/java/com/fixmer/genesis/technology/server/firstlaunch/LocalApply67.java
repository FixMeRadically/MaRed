package com.fixmer.genesis.technology.server.firstlaunch;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated SAME-USER local bridge. Not a remote updater or multiplayer identity protocol. */
public final class LocalApply67 {
    public final Path root,workspace;public final String instance,run,account;private final byte[] key;
    private LocalApply67(Path root,byte[] key,String instance,String run,String account)throws IOException{
        this.root=root;this.key=key;this.instance=instance;this.run=run;this.account=account;workspace=FirstLaunchFiles.safe(root.resolve("workspace"));
    }
    private static byte[] body(Map<String,String> values)throws IOException{
        if(values.isEmpty()||values.size()>16)throw new IOException("APPLY67_FIELDS");StringBuilder b=new StringBuilder();
        for(var e:new TreeMap<>(values).entrySet()){
            if(!e.getKey().matches("[A-Za-z][A-Za-z0-9]{0,31}")||e.getKey().equals("mac")||e.getValue()==null||e.getValue().getBytes(StandardCharsets.UTF_8).length>65536)throw new IOException("APPLY67_FIELD");
            b.append(e.getKey()).append('=').append(Base64.getUrlEncoder().withoutPadding().encodeToString(e.getValue().getBytes(StandardCharsets.UTF_8))).append('\n');
        }
        byte[] out=b.toString().getBytes(StandardCharsets.US_ASCII);if(out.length>130000)throw new IOException("APPLY67_SIZE");return out;
    }
    private static String hmac(byte[] key,byte[] data)throws IOException{try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(data));}catch(GeneralSecurityException bad){throw new IOException("APPLY67_MAC",bad);}}
    public static byte[] sign(byte[] key,Map<String,String> values)throws IOException{
        if(key.length!=32)throw new IOException("APPLY67_KEY");byte[] data=body(values);return (new String(data,StandardCharsets.US_ASCII)+"mac="+hmac(key,data)+"\n").getBytes(StandardCharsets.US_ASCII);
    }
    public static Map<String,String> verify(byte[] key,byte[] input)throws IOException{
        if(input.length>131072||key.length!=32)throw new IOException("APPLY67_SIZE");
        try{
            String text=StandardCharsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(input)).toString();int at=text.lastIndexOf("mac=");
            if(at<0||at>0&&text.charAt(at-1)!='\n')throw new IOException("APPLY67_MAC");String signature=text.substring(at+4);
            if(!signature.matches("[a-f0-9]{64}\\n"))throw new IOException("APPLY67_MAC");byte[] raw=text.substring(0,at).getBytes(StandardCharsets.US_ASCII);
            if(!MessageDigest.isEqual(signature.substring(0,64).getBytes(StandardCharsets.US_ASCII),hmac(key,raw).getBytes(StandardCharsets.US_ASCII)))throw new IOException("APPLY67_AUTH");
            var values=new TreeMap<String,String>();for(String line:text.substring(0,at).split("\n")){int eq=line.indexOf('=');if(eq<1)throw new IOException("APPLY67_LINE");String k=line.substring(0,eq),encoded=line.substring(eq+1);byte[] decoded=Base64.getUrlDecoder().decode(encoded);
                if(!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(encoded))throw new IOException("APPLY67_BASE64");String v=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(decoded)).toString();if(values.put(k,v)!=null)throw new IOException("APPLY67_DUPLICATE");}
            if(!Arrays.equals(raw,body(values)))throw new IOException("APPLY67_CANONICAL");return Map.copyOf(values);
        }catch(IllegalArgumentException bad){throw new IOException("APPLY67_FORMAT",bad);}
    }
    public static LocalApply67 open(Map<String,String> env)throws IOException{
        String value=env.get("GENESIS_PANEL67_ROOT");if(value==null)return null;
        if(!"client".equals(env.get("GENESIS_FIRST58_ROLE")))throw new IOException("APPLY67_ROLE");
        var session=FirstLaunchSession.open(env,"client");if(session==null)throw new IOException("APPLY67_SESSION");
        Path root=FirstLaunchFiles.safe(Path.of(value));if(!root.getFileName().toString().equals("repeat67"))throw new IOException("APPLY67_ROOT");
        byte[] key=FirstLaunchFiles.read(root.resolve("bridge.key"),32);var meta=verify(key,FirstLaunchFiles.read(root.resolve("meta67"),131072));
        if(!meta.keySet().equals(Set.of("instance","account"))||!meta.get("instance").equals(env.get("GENESIS_PANEL67_INSTANCE"))||!meta.get("account").equals(session.account().toString()))throw new IOException("APPLY67_BINDING");
        UUID.fromString(meta.get("instance"));return new LocalApply67(root,key,meta.get("instance"),session.run().toString(),session.account().toString());
    }
    public Map<String,String> state()throws IOException{
        var v=verify(key,FirstLaunchFiles.read(root.resolve("state67"),131072));
        if(!instance.equals(v.get("instance"))||!account.equals(v.get("account"))||!run.equals(v.get("run")))throw new IOException("APPLY67_STALE_STATE");return v;
    }
    public void send(String nonce,String action,String jar,String sha,String side)throws IOException{
        UUID.fromString(nonce);if(!Set.of("CHECK","PREPARE","ARM","CANCEL").contains(action)||!Set.of("BOTH","SERVER","CLIENT").contains(side))throw new IOException("APPLY67_ACTION");
        FirstLaunchFiles.write(root.resolve("request67"),sign(key,Map.of("instance",instance,"run",run,"account",account,"nonce",nonce,"action",action,"jar",jar,"sha",sha,"side",side)),true);
    }
    public static Path workspace(Path fallback){
        try{LocalApply67 bridge=open(System.getenv());return bridge==null?fallback:bridge.workspace;}catch(IOException|RuntimeException bad){throw new IllegalStateException("Genesis shared workspace binding unavailable; default workspace was not substituted",bad);}
    }
}
