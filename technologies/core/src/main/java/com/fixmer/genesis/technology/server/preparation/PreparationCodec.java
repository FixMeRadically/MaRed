package com.fixmer.genesis.technology.server.preparation;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
/** Canonical bounded records. SHA256 detects corruption, not administrator impersonation. */
final class PreparationCodec {
    private static final byte[] MAGIC="GENESIS-SERVER-PREPARATION-52\n".getBytes(StandardCharsets.US_ASCII);
    private PreparationCodec(){
    }
    static byte[] encode(Map<String,String> map)throws IOException {
        if(map.size()>24000)throw new IOException("PREPARATION_FIELD_LIMIT");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)){
            out.write(MAGIC);
            out.writeInt(map.size());
            for(var e:new TreeMap<>(map).entrySet()){
                if(!e.getKey().matches("[A-Za-z][A-Za-z0-9._-]{0,127}"))throw new IOException("PREPARATION_FIELD_NAME");
                text(out,e.getKey());
                text(out,e.getValue());
                if(bytes.size()+32>PreparationFiles.MAX_RECORD)throw new IOException("PREPARATION_RECORD_SIZE_LIMIT");
            }
        }
        byte[] body=bytes.toByteArray();
        if(body.length+32>PreparationFiles.MAX_RECORD)throw new IOException("PREPARATION_RECORD_SIZE_LIMIT");
        bytes.write(HexFormat.of().parseHex(PreparationFiles.digest(body)));
        return bytes.toByteArray();
    }
    static Map<String,String> decode(byte[] data)throws IOException {
        if(data.length>PreparationFiles.MAX_RECORD||data.length<MAGIC.length+36)throw new IOException("PREPARATION_RECORD_SIZE_LIMIT");
        byte[] body=Arrays.copyOf(data,data.length-32);
        if(!java.security.MessageDigest.isEqual(HexFormat.of().parseHex(PreparationFiles.digest(body)),Arrays.copyOfRange(data,data.length-32,data.length)))throw new IOException("PREPARATION_RECORD_CHECKSUM");
        try(var in=new DataInputStream(new ByteArrayInputStream(body))){
            if(!Arrays.equals(in.readNBytes(MAGIC.length),MAGIC))throw new IOException("PREPARATION_RECORD_SCHEMA");
            int count=in.readInt();
            if(count<0||count>24000)throw new IOException("PREPARATION_FIELD_LIMIT");
            var result=new TreeMap<String,String>();
            String previous="";
            for(int i=0;i<count;i++){
                String key=text(in),value=text(in);
                if(!key.matches("[A-Za-z][A-Za-z0-9._-]{0,127}")||key.compareTo(previous)<=0)throw new IOException("PREPARATION_FIELD_ORDER");
                result.put(key,value);
                previous=key;
            }
            if(in.read()!=-1)throw new IOException("PREPARATION_TRAILING_DATA");
            return Collections.unmodifiableMap(result);
        }
    }
    private static void text(DataOutputStream out,String text)throws IOException{
        if(text==null)throw new IOException("PREPARATION_NULL_FIELD");
        byte[] data;
        try{
            var buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(text));
            data=new byte[buffer.remaining()];
            buffer.get(data);
        }
        catch(CharacterCodingException bad){
            throw new IOException("PREPARATION_INVALID_UTF8",bad);
        }
        if(data.length>8192||text.indexOf('\0')>=0)throw new IOException("PREPARATION_FIELD_SIZE");
        out.writeInt(data.length);
        out.write(data);
    }
    private static String text(DataInputStream in)throws IOException{
        int length=in.readInt();
        if(length<0||length>8192)throw new IOException("PREPARATION_FIELD_SIZE");
        byte[] data=in.readNBytes(length);
        if(data.length!=length)throw new EOFException("PREPARATION_TRUNCATED_FIELD");
        try{
            String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            if(value.indexOf('\0')>=0)throw new IOException("PREPARATION_NULL_CHARACTER");
            return value;
        }
        catch(CharacterCodingException bad){
            throw new IOException("PREPARATION_INVALID_UTF8",bad);
        }
    }
}
