package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.server.packages.PackageModel;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Signed public CLIENT offer. It never grants install or process-control authority. */
public record RemoteOffer69(String game,String endpoint,UUID distribution,UUID release,UUID parent,
                            String minecraft,String neoforge,long sequence,long issued,long expires,
                            long bytes,String archiveSha,String signer) {
    private static final byte[] MAGIC="GENESIS-REMOTE-OFFER-69\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DOMAIN="GENESIS-REMOTE-OFFER-SIGNATURE-69\0".getBytes(StandardCharsets.US_ASCII);
    public RemoteOffer69 {
        if(game==null||endpoint==null||game.length()>255||endpoint.length()>2048||!game.equals(gameAddress(game))||!endpoint.equals(base(endpoint)))throw new IllegalArgumentException("REMOTE_ADDRESS");
        if(distribution==null||release==null||distribution.equals(new UUID(0,0))||release.equals(new UUID(0,0))||parent!=null&&(parent.equals(release)||parent.equals(new UUID(0,0))))throw new IllegalArgumentException("REMOTE_RELEASE");
        if(!"1.21.1".equals(minecraft)||!neoforge.matches("21\\.1\\.[0-9]{1,6}")||sequence<1||issued<0||expires<=issued||expires-issued>7L*86400000||bytes<1||bytes>PackageModel.MAX_ARCHIVE||!hash(archiveSha)||!hash(signer))throw new IllegalArgumentException("REMOTE_FIELDS");
    }
    static boolean hash(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    /** Compare an already connected game's literal loopback aliases; no DNS or remote-host equivalence. */
    public static boolean sameGameAddress159(String left,String right){
        String a=gameAddress(left),b=gameAddress(right);if(a.equals(b))return true;
        int x=a.lastIndexOf(':'),y=b.lastIndexOf(':');var local=Set.of("localhost","127.0.0.1","[::1]","[0:0:0:0:0:0:0:1]");
        return a.substring(x).equals(b.substring(y))&&local.contains(a.substring(0,x))&&local.contains(b.substring(0,y));
    }
    public static String gameAddress(String input){
        if(input==null||input.length()>255)throw new IllegalArgumentException("REMOTE_GAME_ADDRESS");
        try{URI u=new URI("minecraft://"+input.trim());String host=u.getHost();int port=u.getPort()<0?25565:u.getPort();
            if(host==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||!Objects.toString(u.getRawPath(),"").isEmpty()||port<1||port>65535)throw new IllegalArgumentException("REMOTE_GAME_ADDRESS");
            return host.toLowerCase(Locale.ROOT)+":"+port;
        }catch(URISyntaxException bad){throw new IllegalArgumentException("REMOTE_GAME_ADDRESS");}
    }
    public static String base(String input){
        if(input==null||input.length()>2048)throw new IllegalArgumentException("REMOTE_HTTPS_ENDPOINT");
        try{URI u=new URI(input.trim());String host=u.getHost();int port=u.getPort();String path=Objects.toString(u.getRawPath(),"");if(path.isEmpty())path="/";if(!path.endsWith("/"))path+="/";
            if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null||port==0||port>65535||!path.matches("/(?:[A-Za-z0-9_-]+/)*"))throw new IllegalArgumentException("REMOTE_HTTPS_ENDPOINT");
            return "https://"+host.toLowerCase(Locale.ROOT)+(port<0||port==443?"":":"+port)+path;
        }catch(URISyntaxException bad){throw new IllegalArgumentException("REMOTE_HTTPS_ENDPOINT");}
    }
    public URI resource(String name){if(!name.matches("public\\.der|offer\\.bin|offer\\.sig|client-[a-f0-9]{64}\\.zip"))throw new IllegalArgumentException("REMOTE_RESOURCE");return URI.create(endpoint+name);}
    public void validAt(long now)throws IOException{if(now<0||issued>now+120000||expires<=now)throw new IOException("REMOTE_OFFER_EXPIRED");}
    public byte[] encode()throws IOException{
        var buffer=new ByteArrayOutputStream();try(var out=new DataOutputStream(buffer)){
            out.write(MAGIC);for(String value:List.of(game,endpoint,distribution.toString(),release.toString(),parent==null?"none":parent.toString(),minecraft,neoforge))text(out,value);
            out.writeLong(sequence);out.writeLong(issued);out.writeLong(expires);out.writeLong(bytes);text(out,archiveSha);text(out,signer);
        }return buffer.toByteArray();
    }
    public static RemoteOffer69 decode(byte[] bytes)throws IOException{
        if(bytes.length>8192)throw new IOException("REMOTE_OFFER_LIMIT");try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(!Arrays.equals(in.readNBytes(MAGIC.length),MAGIC))throw new IOException("REMOTE_SCHEMA");
            String game=text(in),endpoint=text(in),distribution=text(in),release=text(in),parent=text(in),mc=text(in),neo=text(in);
            var result=new RemoteOffer69(game,endpoint,UUID.fromString(distribution),UUID.fromString(release),parent.equals("none")?null:UUID.fromString(parent),mc,neo,in.readLong(),in.readLong(),in.readLong(),in.readLong(),text(in),text(in));
            if(in.read()!=-1||!Arrays.equals(bytes,result.encode()))throw new IOException("REMOTE_NONCANONICAL");return result;
        }catch(IllegalArgumentException bad){throw new IOException("REMOTE_INVALID_OFFER",bad);}
    }
    private static void text(DataOutputStream out,String value)throws IOException{byte[] raw=value.getBytes(StandardCharsets.US_ASCII);out.writeInt(raw.length);out.write(raw);}
    private static String text(DataInputStream in)throws IOException{int count=in.readInt();if(count<1||count>2048)throw new IOException("REMOTE_TEXT_LIMIT");byte[] raw=in.readNBytes(count);if(raw.length!=count)throw new EOFException();for(byte b:raw)if(b<32||b>126)throw new IOException("REMOTE_TEXT");return new String(raw,StandardCharsets.US_ASCII);}
    public static PublicKey publicKey(byte[] encoded)throws IOException{
        if(encoded.length>256)throw new IOException("REMOTE_KEY_LIMIT");try{PublicKey key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));if(!Arrays.equals(key.getEncoded(),encoded))throw new IOException("REMOTE_KEY_FORMAT");return key;}catch(GeneralSecurityException bad){throw new IOException("REMOTE_KEY_FORMAT",bad);}
    }
    public byte[] sign(PrivateKey key)throws IOException{try{Signature s=Signature.getInstance("Ed25519");s.initSign(key);s.update(DOMAIN);s.update(encode());return s.sign();}catch(GeneralSecurityException bad){throw new IOException("REMOTE_SIGN",bad);}}
    public void verify(PublicKey key,byte[] signature)throws IOException{
        if(signature.length!=64||!signer.equals(FirstLaunchFiles.sha(key.getEncoded())))throw new IOException("REMOTE_SIGNER");
        try{Signature s=Signature.getInstance("Ed25519");s.initVerify(key);s.update(DOMAIN);s.update(encode());if(!s.verify(signature))throw new IOException("REMOTE_SIGNATURE");}catch(GeneralSecurityException bad){throw new IOException("REMOTE_SIGNATURE",bad);}
    }
}
