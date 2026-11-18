package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Per-transaction append/force journal. Only an incomplete last line may be repaired. */
final class InstallJournal implements AutoCloseable {
    enum Event { PREPARED, INTENT, DONE, COMMIT_INTENT, COMMITTED, ROLLBACK_BEGIN, ROLLED_BACK }
    record State(int sequence,Event event,int index){}
    private final FileChannel channel;private final String descriptor;private final int tasks;private final InstallIo.Fault fault;private State state;private boolean poisoned;
    InstallJournal(Path path,String descriptor,int tasks,InstallIo.Fault fault)throws IOException{
        this.descriptor=PackageModel.hash(descriptor);this.tasks=tasks;this.fault=fault;PackageFiles.safe(path);
        channel=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try{
            PackageFiles.privateMode(path,false);
            long size=channel.size();
            if(size>1024*1024)throw new IOException("INSTALL_JOURNAL_LIMIT");
            byte[] bytes=new byte[(int)size];var buffer=ByteBuffer.wrap(bytes);
            while(buffer.hasRemaining())if(channel.read(buffer)<=0)throw new IOException("INSTALL_JOURNAL_CHANGED");
            if(channel.size()!=size)throw new IOException("INSTALL_JOURNAL_CHANGED");
            int start=0;
            for(int i=0;i<bytes.length;i++)if(bytes[i]=='\n'){String line=new String(bytes,start,i-start,StandardCharsets.US_ASCII);String[] f=line.split("\\|",-1);if(f.length!=5)throw new IOException("INSTALL_JOURNAL_CORRUPT");String body=String.join("|",Arrays.copyOf(f,4));if(!f[3].equals(descriptor)||!PackageFiles.digest(body.getBytes(StandardCharsets.US_ASCII)).equals(f[4]))throw new IOException("INSTALL_JOURNAL_CORRUPT");State next;try{next=new State(Integer.parseInt(f[0]),Event.valueOf(f[1]),Integer.parseInt(f[2]));}catch(IllegalArgumentException bad){throw new IOException("INSTALL_JOURNAL_CORRUPT",bad);}validate(next);state=next;start=i+1;}
            if(start<bytes.length){channel.truncate(start);channel.force(true);}channel.position(channel.size());
        }catch(IOException|RuntimeException bad){channel.close();throw bad;}
    }
    State state(){return state;}
    void append(Event event,int index)throws IOException{
        if(poisoned)throw new IOException("INSTALL_JOURNAL_OUTCOME_UNKNOWN");var next=new State(state==null?0:state.sequence()+1,event,index);validate(next);String body=next.sequence()+"|"+event+"|"+index+"|"+descriptor;byte[] bytes=(body+"|"+PackageFiles.digest(body.getBytes(StandardCharsets.US_ASCII))+"\n").getBytes(StandardCharsets.US_ASCII);
        try{if(channel.size()+bytes.length>1024*1024)throw new IOException("INSTALL_JOURNAL_LIMIT");var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);fault.at("journal."+event+".write");channel.force(true);fault.at("journal."+event+".force");state=next;}catch(IOException bad){poisoned=true;throw bad;}
    }
    private void validate(State next)throws IOException{
        if(next.sequence()!=(state==null?0:state.sequence()+1)||next.sequence()>4096)throw new IOException("INSTALL_JOURNAL_SEQUENCE");boolean valid;
        if(state==null)valid=next.event()==Event.PREPARED&&next.index()==-1;
        else valid=switch(next.event()){
            case PREPARED->false;
            case INTENT->(state.event()==Event.PREPARED||state.event()==Event.DONE)&&next.index()==state.index()+1&&next.index()<tasks;
            case DONE->state.event()==Event.INTENT&&next.index()==state.index();
            case COMMIT_INTENT->(tasks==0&&state.event()==Event.PREPARED||state.event()==Event.DONE&&state.index()==tasks-1)&&next.index()==tasks;
            case COMMITTED->state.event()==Event.COMMIT_INTENT&&next.index()==tasks;
            case ROLLBACK_BEGIN->state.event()!=Event.COMMITTED&&state.event()!=Event.ROLLED_BACK&&state.event()!=Event.ROLLBACK_BEGIN&&next.index()==(state.event()==Event.COMMIT_INTENT?tasks+1:state.index());
            case ROLLED_BACK->state.event()==Event.ROLLBACK_BEGIN&&next.index()==state.index();
        };if(!valid)throw new IOException("INSTALL_JOURNAL_TRANSITION");
    }
    public void close()throws IOException{channel.close();}
}
