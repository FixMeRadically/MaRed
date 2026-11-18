package com.fixmer.genesis.technology.server.players;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Small bounded binary format. No Java object deserialization or executable values. */
final class PlayerUpdateCodec {
    private PlayerUpdateCodec() {}
    interface Writer {void write(DataOutputStream out)throws IOException;}
    static byte[] bytes(Writer writer)throws IOException {var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){writer.write(out);}return bytes.toByteArray();}
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static void id(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    static UUID id(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    static byte[] initial(State s)throws IOException{return bytes(out->{
        out.writeByte(0);out.writeInt(SCHEMA);id(out,s.update());out.writeUTF(s.packageSha256());
        out.writeLong(s.policy().decisionMillis());out.writeInt(s.policy().readyPercent());out.writeLong(s.policy().barrierMillis());out.writeUTF(s.policy().timeoutPolicy().name());out.writeLong(s.now());
        out.writeInt(s.players().size());for(UUID id:new TreeSet<>(s.players().keySet())){Player p=s.players().get(id);id(out,p.id());id(out,p.connection());id(out,p.offer());}
    });}
    static State readInitial(DataInputStream in)throws IOException{
        if(in.readInt()!=SCHEMA)throw new IOException("Unsupported player journal schema");
        UUID update=id(in);String hash=in.readUTF();Policy policy=new Policy(in.readLong(),in.readInt(),in.readLong(),TimeoutPolicy.valueOf(in.readUTF()));long now=in.readLong();
        int size=in.readInt();if(size<0||size>MAX_PLAYERS)throw new IOException("Player limit");var offers=new ArrayList<Offer>();for(int i=0;i<size;i++)offers.add(new Offer(id(in),id(in),id(in)));
        return start(update,hash,policy,now,offers);
    }
    static byte[] command(Command c)throws IOException{return bytes(out->{out.writeByte(1);writeCommand(out,c);});}
    static void writeCommand(DataOutputStream out,Command c)throws IOException{
        id(out,c.request());out.writeLong(c.epoch());out.writeLong(c.revision());id(out,c.update());out.writeLong(c.now());out.writeUTF(c.action().name());id(out,c.player());id(out,c.connection());id(out,c.offer());out.writeUTF(c.packageSha256());id(out,c.serverRun());
    }
    static Command readCommand(DataInputStream in)throws IOException{return new Command(id(in),in.readLong(),in.readLong(),id(in),in.readLong(),Action.valueOf(in.readUTF()),id(in),id(in),id(in),in.readUTF(),id(in));}
    static String fingerprint(Command c)throws IOException{return sha(command(c));}
}
