package com.fixmer.genesis.technology.storage;
import java.io.*;import java.nio.*;import java.nio.channels.*;import java.nio.charset.*;import java.nio.file.*;
/** Unique same-directory temporary file; only unsupported atomic moves permit the fallback. */
public final class AtomicSettings111 {
    private AtomicSettings111(){}
    public static void write(Path target,String text)throws IOException{
        target=target.toAbsolutePath().normalize();Path parent=target.getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,".mared-settings-",".tmp");
        try{
            byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
            try(var file=FileChannel.open(temporary,StandardOpenOption.WRITE)){ByteBuffer data=ByteBuffer.wrap(bytes);while(data.hasRemaining())file.write(data);file.force(true);}
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
}
