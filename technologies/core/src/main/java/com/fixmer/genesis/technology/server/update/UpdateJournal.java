package com.fixmer.genesis.technology.server.update;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static com.fixmer.genesis.technology.server.update.UpdateProtocol.*;

/**
 * Append-only, forced-to-disk local journal. One writer holds an OS lock until close.
 * Assumes a trusted local filesystem, not a shared/network directory. No world is touched.
 * Full invalid records fail closed; only an unfinished final record may be discarded.
 */
public final class UpdateJournal implements AutoCloseable {
    public static final int MAX_RECEIPTS=128,MAX_BYTES=16*1024*1024,MAX_FRAME_BYTES=64*1024;
    private static final Set<Path> JVM_OWNERS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Path ownershipKey;
    private static final String JOURNAL="update-journal.v1",LOCK="update-owner.lock";
    private record Entry(State state,List<Receipt> receipts) {}
    private record Loaded(Entry entry,int validBytes,int tailBytes,Set<UUID> updateIds) {}
    public record Inspection(State state,int retainedReceipts,int unfinishedTailBytes,int journalBytes) {}
    private final FileChannel owner,channel;
    private final FileLock lock;
    private final Object fileKey;
    private final Path file;
    private Entry entry;
    private final Set<UUID> updateIds;
    private long length;
    private final int repairedTailBytes;
    private boolean closed,uncertain;

    private UpdateJournal(Path directory,boolean create)throws IOException{
        safePath(directory);
        if(create)Files.createDirectory(directory);
        if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("State directory does not exist");
        file=directory.resolve(JOURNAL);Path ownerFile=directory.resolve(LOCK);safePath(ownerFile);safePath(file);
        ownershipKey=directory.toRealPath();
        // A failed second open must not close another descriptor for the same lock
        // file: on some OSes that releases the first owner's process-wide lock.
        if(!JVM_OWNERS.add(ownershipKey))throw new IOException("Another coordinator owns this state directory");
        FileChannel acquiredOwner;
        try{acquiredOwner=FileChannel.open(ownerFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);}
        catch(IOException|RuntimeException bad){JVM_OWNERS.remove(ownershipKey);throw bad;}
        FileLock acquiredLock=null;FileChannel acquiredJournal=null;
        try{
            try{acquiredLock=acquiredOwner.tryLock();}catch(OverlappingFileLockException conflict){throw new IOException("Another coordinator owns this state directory",conflict);}
            if(acquiredLock==null)throw new IOException("Another coordinator owns this state directory");
            acquiredJournal=create?FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
                :FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            if(!create&&acquiredJournal.size()==0)throw new IOException("Existing journal is empty; recovery required");
            var loaded=load(read(acquiredJournal));updateIds=new HashSet<>(loaded.updateIds());entry=loaded.entry();length=loaded.validBytes();repairedTailBytes=loaded.tailBytes();
            if(repairedTailBytes>0){acquiredJournal.truncate(length);acquiredJournal.force(true);}
            owner=acquiredOwner;lock=acquiredLock;channel=acquiredJournal;
            fileKey=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
            append(new Entry(entry.state().acquire(),entry.receipts()));
        }catch(IOException|RuntimeException failure){
            if(acquiredJournal!=null)try{acquiredJournal.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}
            if(acquiredLock!=null)try{acquiredLock.release();}catch(IOException cleanup){failure.addSuppressed(cleanup);}
            try{acquiredOwner.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}JVM_OWNERS.remove(ownershipKey);throw failure;
        }
    }
    /** Explicit creation refuses an existing directory; opening never initializes a missing log. */
    public static UpdateJournal create(Path directory)throws IOException{return new UpdateJournal(directory.toAbsolutePath().normalize(),true);}
    public static UpdateJournal open(Path directory)throws IOException{return new UpdateJournal(directory.toAbsolutePath().normalize(),false);}
    public synchronized State state()throws IOException{healthy();return entry.state();}
    public int repairedTailBytes(){return repairedTailBytes;}
    public synchronized Result execute(Command command)throws IOException{
        healthy();Objects.requireNonNull(command);String fingerprint=command.fingerprint();
        for(Receipt receipt:entry.receipts())if(receipt.requestId().equals(command.requestId())){
            if(!receipt.fingerprint().equals(fingerprint))return new Result(Outcome.REJECTED,Rejection.REQUEST_ID_REUSED,0,entry.state());
            return new Result(Outcome.REPLAY,Rejection.NONE,receipt.appliedRevision(),entry.state());
        }
        if(command.action()==Action.BEGIN&&updateIds.contains(command.updateId()))return new Result(Outcome.REJECTED,Rejection.UPDATE_ID_REUSED,0,entry.state());
        var next=UpdateMachine.advance(entry.state(),command);
        if(!next.accepted())return new Result(Outcome.REJECTED,next.rejection(),0,entry.state());
        var receipts=new ArrayList<>(entry.receipts());receipts.add(new Receipt(command.requestId(),fingerprint,next.state().revision()));
        if(receipts.size()>MAX_RECEIPTS)receipts.remove(0);
        append(new Entry(next.state(),List.copyOf(receipts)));
        return new Result(Outcome.APPLIED,Rejection.NONE,entry.state().revision(),entry.state());
    }
    private void healthy()throws IOException{
        if(closed)throw new IOException("Coordinator is closed");
        if(uncertain)throw new IOException("Previous write outcome is uncertain; close and reopen before further commands");
        if(!lock.isValid())throw new IOException("Coordinator ownership was lost");
        safePath(file);
        var attributes=Files.readAttributes(file,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attributes.isRegularFile()||fileKey!=null&&!fileKey.equals(attributes.fileKey())||channel.size()!=length)throw new IOException("Journal changed outside the coordinator");
    }
    private void append(Entry next)throws IOException{
        byte[] body=encode(next);String frame=sha(body)+"\t"+Base64.getEncoder().encodeToString(body)+"\n";byte[] bytes=frame.getBytes(StandardCharsets.US_ASCII);
        if(bytes.length>MAX_FRAME_BYTES||length+bytes.length>MAX_BYTES)throw new IOException("Journal size limit; archive/rotation support is required before continuing");
        try{
            channel.position(length);ByteBuffer buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);
            channel.force(true);length+=bytes.length;entry=next;if(!next.state().updateId().equals(NONE))updateIds.add(next.state().updateId());
        }catch(IOException failure){uncertain=true;throw failure;}
    }
    /** Read-only: no ownership change, truncation, directory creation or replay execution. */
    public static Inspection inspect(Path directory)throws IOException{
        Path file=directory.toAbsolutePath().normalize().resolve(JOURNAL);safePath(file);
        try(var channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] bytes=read(channel);if(bytes.length==0)throw new IOException("Existing journal is empty; recovery required");var loaded=load(bytes);
            return new Inspection(loaded.entry().state(),loaded.entry().receipts().size(),loaded.tailBytes(),bytes.length);
        }
    }
    private static void safePath(Path path)throws IOException{
        for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked state paths are unsupported");
    }
    private static byte[] read(FileChannel channel)throws IOException{
        if(channel.size()>MAX_BYTES)throw new IOException("Journal size limit");
        channel.position(0);var out=new ByteArrayOutputStream();var buffer=ByteBuffer.allocate(8192);
        while(channel.read(buffer)>0){buffer.flip();if(out.size()+buffer.remaining()>MAX_BYTES)throw new IOException("Journal size limit");out.write(buffer.array(),0,buffer.remaining());buffer.clear();}
        return out.toByteArray();
    }
    private static Loaded load(byte[] bytes)throws IOException{
        Entry last=new Entry(State.initial(),List.of());int start=0;var updateIds=new HashSet<UUID>();
        for(int i=0;i<bytes.length;i++)if(bytes[i]=='\n'){
            if(i-start>MAX_FRAME_BYTES)throw new IOException("Oversized journal record");
            String line=new String(bytes,start,i-start,StandardCharsets.US_ASCII);int tab=line.indexOf('\t');
            if(tab!=64)throw new IOException("Invalid journal frame at byte "+start);
            byte[] body;try{body=Base64.getDecoder().decode(line.substring(tab+1));}catch(IllegalArgumentException bad){throw new IOException("Invalid journal encoding at byte "+start,bad);}
            if(!sha(body).equals(line.substring(0,tab)))throw new IOException("Journal checksum mismatch at byte "+start);
            Entry decoded=decode(body);
            if(decoded.state().revision()!=last.state().revision()+1||decoded.state().epoch()<last.state().epoch()||decoded.state().epoch()>last.state().epoch()+1)throw new IOException("Invalid journal sequence");
            if(decoded.state().epoch()==last.state().epoch()+1){
                if(!decoded.state().equals(last.state().acquire())||!decoded.receipts().equals(last.receipts()))throw new IOException("Invalid ownership record");
            }else if(decoded.receipts().isEmpty()||decoded.receipts().get(decoded.receipts().size()-1).appliedRevision()!=decoded.state().revision())throw new IOException("Missing command receipt");
            last=decoded;if(!last.state().updateId().equals(NONE))updateIds.add(last.state().updateId());start=i+1;
        }
        if(bytes.length-start>MAX_FRAME_BYTES)throw new IOException("Oversized unfinished journal record");
        return new Loaded(last,start,bytes.length-start,Set.copyOf(updateIds));
    }
    private static byte[] encode(Entry entry)throws IOException{
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            var state=entry.state();out.writeInt(VERSION);out.writeLong(state.revision());out.writeLong(state.epoch());writeUuid(out,state.updateId());
            out.writeUTF(state.packageSha256());out.writeUTF(state.phase().name());out.writeBoolean(state.gameplayOpened());out.writeUTF(state.failureCode());
            out.writeInt(entry.receipts().size());for(Receipt receipt:entry.receipts()){writeUuid(out,receipt.requestId());out.writeUTF(receipt.fingerprint());out.writeLong(receipt.appliedRevision());}
        }return bytes.toByteArray();
    }
    private static Entry decode(byte[] bytes)throws IOException{
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=VERSION)throw new IOException("Unsupported journal schema");
            var state=new State(in.readLong(),in.readLong(),readUuid(in),in.readUTF(),Phase.valueOf(in.readUTF()),in.readBoolean(),in.readUTF());
            int count=in.readInt();if(count<0||count>MAX_RECEIPTS)throw new IOException("Invalid receipt count");
            var receipts=new ArrayList<Receipt>();var ids=new HashSet<UUID>();long previous=0;
            for(int i=0;i<count;i++){
                var receipt=new Receipt(readUuid(in),in.readUTF(),in.readLong());
                if(!ids.add(receipt.requestId())||receipt.appliedRevision()<=previous||receipt.appliedRevision()>state.revision())throw new IOException("Invalid receipt sequence");
                receipts.add(receipt);previous=receipt.appliedRevision();
            }
            if(in.read()!=-1)throw new IOException("Trailing journal payload");
            return new Entry(state,List.copyOf(receipts));
        }catch(IllegalArgumentException bad){throw new IOException("Invalid journal payload",bad);}
    }
    private static void writeUuid(DataOutput out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID readUuid(DataInput in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    @Override public synchronized void close()throws IOException{
        if(closed)return;closed=true;IOException problem=null;
        try{channel.close();}catch(IOException bad){problem=bad;}
        try{lock.release();}catch(IOException bad){if(problem==null)problem=bad;else problem.addSuppressed(bad);}
        try{owner.close();}catch(IOException bad){if(problem==null)problem=bad;else problem.addSuppressed(bad);}
        JVM_OWNERS.remove(ownershipKey);
        if(problem!=null)throw problem;
    }
}
