package com.fixmer.genesis.technology.workspace.live122;
import java.io.*;
import java.util.zip.*;

/** Optional single-stream zlib. The uncompressed limit is enforced independently of wire size. */
public final class ModelTransfer149 {
    public record Packed(byte[] bytes,String encoding){}
    private ModelTransfer149(){}
    public static Packed pack(byte[] raw)throws IOException{
        if(raw.length>WorkspaceModels147.MAX)throw new IOException("MODEL_LIMIT147");
        if(raw.length<16384)return new Packed(raw,"raw");
        var buffer=new ByteArrayOutputStream();Deflater deflater=new Deflater(Deflater.BEST_SPEED);
        try(var output=new DeflaterOutputStream(buffer,deflater)){output.write(raw);}finally{deflater.end();}
        byte[] packed=buffer.toByteArray();return packed.length+256<raw.length?new Packed(packed,"zlib"):new Packed(raw,"raw");
    }
    public static byte[] unpack(byte[] wire,String encoding,int expectedSize)throws IOException{
        if(wire.length>WorkspaceModels147.MAX||expectedSize<0||expectedSize>WorkspaceModels147.MAX)throw new IOException("MODEL_LIMIT147");
        byte[] raw=switch(encoding){case "raw"->wire;case "zlib"->WorkspaceCodec122.inflate142(wire,expectedSize,"MODEL_LIMIT147");default->throw new IOException("MODEL_FORMAT147");};
        if(raw.length!=expectedSize)throw new IOException("MODEL_HASH147");return raw;
    }
}
