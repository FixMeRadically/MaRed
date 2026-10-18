package com.fixmer.genesis.supervisor;
import java.io.*;
import java.util.function.Consumer;
/** Read blocks without per-character Reader calls; preserve bounded line/tail behavior. */
final class ChildOutput {
    private ChildOutput() { }
    static void copy(Reader reader,Consumer<String> lineSink)throws IOException {
        char[] buffer=new char[8192];StringBuilder line=new StringBuilder();int count;
        while((count=reader.read(buffer,0,buffer.length))!=-1){
            for(int i=0;i<count;i++){
                char c=buffer[i];
                if(c=='\n'||line.length()>=16384){lineSink.accept(line.toString());line.setLength(0);}
                if(c!='\n'&&c!='\r')line.append(c);
            }
        }
        if(line.length()>0)lineSink.accept(line.toString());
    }
}
