package com.fixmer.genesis.technology.creation;
import java.util.UUID;
/** Generation and nonce shared by synchronous and deferred desktop work. */
public final class DesktopConnection150 {
    public record Ticket(long generation,String session){}
    private volatile Ticket current=new Ticket(0,"");
    public Ticket ticket(){return current;}
    public boolean observe(long generation){
        if(generation<0)throw new IllegalArgumentException("GENERATION");
        if(current.generation==generation)return false;
        current=new Ticket(generation,generation==0?"":UUID.randomUUID().toString());return true;
    }
    public boolean accepts(Ticket ticket){return ticket!=null&&ticket.generation!=0&&current.equals(ticket);}
}
