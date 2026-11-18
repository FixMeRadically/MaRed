package com.fixmer.genesis.supervisor;
import java.io.*;
import java.util.function.Consumer;
/** Read blocks without per-character Reader calls; preserve bounded line/tail behavior. */
final class ChildOutput {
    private ChildOutput() { }
    static void copy(Reader reader,Consumer<String> lineSink)throws IOException {
        char[] buffer=new char[8192];StringBuilder line=new StringBuilder();int count;
        boolean afterCr=false;
        while((count=reader.read(buffer,0,buffer.length))!=-1){
            for(int i=0;i<count;i++){
                char c=buffer[i];
                if(c=='\n'){
                    if(!afterCr){lineSink.accept(line.toString());line.setLength(0);}
                    afterCr=false;continue;
                }
                if(c=='\r'){
                    lineSink.accept(line.toString());line.setLength(0);afterCr=true;continue;
                }
                afterCr=false;
                if(line.length()>=16384){
                    // Do not split a valid UTF-16 pair when bounding a long line.
                    int end=line.length();
                    boolean pair=Character.isHighSurrogate(line.charAt(end-1))&&Character.isLowSurrogate(c);
                    char carry=line.charAt(end-1);
                    lineSink.accept(pair?line.substring(0,end-1):line.toString());line.setLength(0);
                    if(pair)line.append(carry);
                }
                line.append(c);
            }
        }
        if(line.length()>0)lineSink.accept(line.toString());
    }
}
