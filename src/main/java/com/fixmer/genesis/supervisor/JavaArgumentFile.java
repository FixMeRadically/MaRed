package com.fixmer.genesis.supervisor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Java launcher @file grammar, never shell quoting. Opaque @files are not nested. */
final class JavaArgumentFile {
    static List<String> wrap(List<String> command,RuntimeFiles runtime)throws IOException {
        if(command.stream().skip(1).anyMatch(a->a.startsWith("@")))throw new IOException("Cannot nest Java argument files");
        Path file=runtime.directory.resolve("launch.args");RuntimeFiles.safe(file);
        StringBuilder out=new StringBuilder();
        for(int i=1;i<command.size();i++)out.append(quote(command.get(i))).append('\n');
        Files.writeString(file,out,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try {Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));}catch(UnsupportedOperationException ignored) { }
        return List.of(command.get(0),"@"+file);
    }
    private static String quote(String value)throws IOException {
        if(value.indexOf('\0')>=0)throw new IOException("Invalid launch argument");
        return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t").replace("\f","\\f")+"\"";
    }
}
