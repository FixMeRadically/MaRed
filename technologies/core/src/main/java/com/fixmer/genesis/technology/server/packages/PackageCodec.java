package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.packages.PackageModel.*;

/** Canonical, bounded binary encoding. No permissive JSON/properties ambiguity in signed data. */
public final class PackageCodec {
    private static final byte[] MAGIC={'G','E','N','P','K','G',0,1};
    private PackageCodec(){}
    public static byte[] encode(Manifest manifest)throws IOException {
        var buffer=new ByteArrayOutputStream();try(var out=new DataOutputStream(buffer)){
            out.write(MAGIC);uuid(out,manifest.distribution());uuid(out,manifest.release());out.writeBoolean(manifest.parent()!=null);if(manifest.parent()!=null)uuid(out,manifest.parent());
            for(String value:List.of(manifest.version(),manifest.minecraft(),manifest.loader(),manifest.loaderVersion()))string(out,value);
            out.writeInt(manifest.minProtocol());out.writeInt(manifest.maxProtocol());string(out,manifest.signer());out.writeInt(manifest.files().size());
            for(Entry entry:manifest.files()){string(out,entry.path());out.writeByte(entry.side().ordinal());out.writeLong(entry.bytes());string(out,entry.sha256());}
        }byte[] data=buffer.toByteArray();if(data.length>MAX_MANIFEST)throw new IOException("MANIFEST_SIZE_LIMIT");return data;
    }
    public static Manifest decode(byte[] data)throws IOException {
        if(data.length>MAX_MANIFEST)throw new IOException("MANIFEST_SIZE_LIMIT");
        try(var in=new DataInputStream(new ByteArrayInputStream(data))){
            if(!Arrays.equals(in.readNBytes(MAGIC.length),MAGIC))throw new IOException("UNSUPPORTED_PACKAGE_SCHEMA");
            UUID distribution=uuid(in),release=uuid(in);int flag=in.readUnsignedByte();if(flag>1)throw new IOException("NONCANONICAL_BOOLEAN");UUID parent=flag==1?uuid(in):null;
            String version=string(in),minecraft=string(in),loader=string(in),loaderVersion=string(in);int min=in.readInt(),max=in.readInt();String signer=string(in);int count=in.readInt();if(count<0||count>MAX_FILES)throw new IOException("FILE_COUNT_LIMIT");
            var files=new ArrayList<Entry>();for(int i=0;i<count;i++){String path=string(in);int side=in.readUnsignedByte();if(side>=Side.values().length)throw new IOException("INVALID_SIDE");files.add(new Entry(path,Side.values()[side],in.readLong(),string(in)));}
            if(in.read()!=-1)throw new IOException("MANIFEST_TRAILING_BYTES");var result=new Manifest(distribution,release,parent,version,minecraft,loader,loaderVersion,min,max,signer,files);
            if(!Arrays.equals(data,encode(result)))throw new IOException("NONCANONICAL_MANIFEST");return result;
        }catch(IllegalArgumentException bad){throw new IOException("INVALID_MANIFEST: "+bad.getMessage(),bad);}
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static void string(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    private static String string(DataInputStream in)throws IOException{
        int n=in.readInt();if(n<0||n>512)throw new IOException("STRING_SIZE_LIMIT");byte[] bytes=in.readNBytes(n);if(bytes.length!=n)throw new EOFException("TRUNCATED_STRING");
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException bad){throw new IOException("INVALID_UTF8",bad);}
    }
}
