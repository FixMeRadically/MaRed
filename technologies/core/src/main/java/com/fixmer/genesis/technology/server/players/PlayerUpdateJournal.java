package com.fixmer.genesis.technology.server.players;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static com.fixmer.genesis.technology.server.players.PlayerUpdateProtocol.*;

/** Single trusted-local-filesystem owner. Run disk operations on a controller worker, never the game tick. */
public final class PlayerUpdateJournal implements AutoCloseable {
    public static final int MAX_BYTES=8*1024*1024, MAX_FRAME=128*1024, MAX_EVENTS=20000;
    private static final String NAME="players.v55", ZERO="0".repeat(64);
    private static final Set<Path> OWNERS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public record Inspection(State state,int unfinishedTailBytes,int events,boolean readOnly) {}
    private record Loaded(State state,LinkedHashMap<UUID,Receipt> receipts,int valid,int tail,int events,String chain,Set<UUID> seen) {}
    private final Path root,file;private final FileChannel owner,data;private final FileLock lock;private final Object fileKey,ownerKey,rootKey;
    private State state;private Set<UUID> seen;private LinkedHashMap<UUID,Receipt> receipts;private String chain,contentHash;private int length,events;private boolean closed,uncertain;
    private PlayerUpdateJournal(Path directory,State initial)throws IOException{
        root=directory.toAbsolutePath().normalize();safe(root);
        if(initial!=null)Files.createDirectory(root);
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Player state directory missing");
        if(!root.toRealPath().equals(root))throw new IOException("Noncanonical state directory");
        file=root.resolve(NAME);safe(file);Path lockFile=root.resolve("players.lock");safe(lockFile);
        if(!OWNERS.add(root))throw new IOException("Another player coordinator owns this directory");
        FileChannel ownerChannel=null,dataChannel=null;FileLock acquired=null;
        try {
            if(Files.exists(lockFile,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(lockFile,LinkOption.NOFOLLOW_LINKS))throw new IOException("Owner lock is not a regular file");
            ownerChannel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            try{acquired=ownerChannel.tryLock();}catch(OverlappingFileLockException e){throw new IOException("Player ownership conflict",e);}
            if(acquired==null)throw new IOException("Player ownership conflict");
            if(initial==null&&!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Player journal is not a regular file");
            dataChannel=initial!=null?FileChannel.open(file,StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS):FileChannel.open(file,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            owner=ownerChannel;data=dataChannel;lock=acquired;
            fileKey=key(file);ownerKey=key(lockFile);rootKey=key(root);
            if(initial!=null){
                // Accept only a genuine initial state reconstructed through the validated factory.
                byte[] header=PlayerUpdateCodec.initial(initial);var parsed=parseHeader(header);if(!parsed.equals(initial))throw new IOException("Initial state was modified");
                state=initial;seen=new HashSet<>();receipts=new LinkedHashMap<>();chain=ZERO;length=0;events=0;contentHash=PlayerUpdateCodec.sha(new byte[0]);append(header);
            }else{
                byte[] raw=read(data);Loaded loaded=load(raw);state=loaded.state();seen=new HashSet<>(loaded.seen());receipts=loaded.receipts();chain=loaded.chain();length=loaded.valid();events=loaded.events();
                if(loaded.tail()>0){data.truncate(length);data.force(true);}contentHash=PlayerUpdateCodec.sha(Arrays.copyOf(raw,length));
                append(new byte[]{2});state=PlayerUpdateMachine.acquired(state);
            }
        }catch(IOException|RuntimeException failure){
            if(dataChannel!=null)try{dataChannel.close();}catch(IOException e){failure.addSuppressed(e);}
            if(acquired!=null)try{acquired.release();}catch(IOException e){failure.addSuppressed(e);}
            if(ownerChannel!=null)try{ownerChannel.close();}catch(IOException e){failure.addSuppressed(e);}OWNERS.remove(root);throw failure;
        }
    }
    public static PlayerUpdateJournal create(Path root,State initial)throws IOException{return new PlayerUpdateJournal(root,Objects.requireNonNull(initial));}
    public static PlayerUpdateJournal open(Path root)throws IOException{return new PlayerUpdateJournal(root,null);}
    public synchronized State state()throws IOException{healthy();return state;}
    public synchronized Result execute(Command c)throws IOException{
        healthy();String fingerprint=PlayerUpdateCodec.fingerprint(c);Receipt old=receipts.get(c.request());
        if(old!=null)return old.fingerprint().equals(fingerprint)?new Result("REPLAY","NONE",state):new Result("REJECTED","REQUEST_ID_REUSED",state);
        if(seen.contains(c.request()))return new Result("REJECTED","REQUEST_HISTORY_EVICTED",state);
        Result result=PlayerUpdateMachine.advance(state,c);if(!result.accepted())return result;
        append(PlayerUpdateCodec.command(c));state=result.state();seen.add(c.request());remember(receipts,new Receipt(c.request(),fingerprint,state.revision()));return result;
    }
    private void healthy()throws IOException{
        if(closed||uncertain||!lock.isValid())throw new IOException("Player journal closed/uncertain; explicit recovery required");
        safe(root);safe(file);safe(root.resolve("players.lock"));
        if(ownerKey!=null&&!ownerKey.equals(key(root.resolve("players.lock")))||rootKey!=null&&!rootKey.equals(key(root)))throw new IOException("Player ownership path replaced");
        var attrs=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||fileKey!=null&&!fileKey.equals(attrs.fileKey())||attrs.size()!=length||!PlayerUpdateCodec.sha(read(data)).equals(contentHash))throw new IOException("Player journal changed outside owner");
    }
    private void append(byte[] body)throws IOException{
        String digest=PlayerUpdateCodec.sha((chain+":"+Base64.getEncoder().encodeToString(body)).getBytes(StandardCharsets.US_ASCII));
        byte[] frame=(digest+"\t"+Base64.getEncoder().encodeToString(body)+"\n").getBytes(StandardCharsets.US_ASCII);
        if(frame.length>MAX_FRAME||length+frame.length>MAX_BYTES||events>=MAX_EVENTS)throw new IOException("Player journal bound reached; new update directory required");
        try{
            data.position(length);var buffer=ByteBuffer.wrap(frame);while(buffer.hasRemaining())data.write(buffer);data.force(true);
            length+=frame.length;events++;chain=digest;contentHash=PlayerUpdateCodec.sha(read(data));
        }catch(IOException failure){uncertain=true;throw failure;}
    }
    public static Inspection inspect(Path root)throws IOException{
        Path normalized=root.toAbsolutePath().normalize();safe(normalized);Path file=normalized.resolve(NAME);safe(file);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Player journal is not a regular file");
        try(var channel=FileChannel.open(file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){Loaded l=load(read(channel));return new Inspection(l.state(),l.tail(),l.events(),true);}
    }
    private static Loaded load(byte[] bytes)throws IOException{
        State state=null;var seen=new HashSet<UUID>();var receipts=new LinkedHashMap<UUID,Receipt>();String chain=ZERO;int start=0,events=0;
        try{
            for(int i=0;i<bytes.length;i++)if(bytes[i]=='\n'){
                if(i-start>MAX_FRAME||++events>MAX_EVENTS)throw new IOException("Player journal bound reached");
                String line=new String(bytes,start,i-start,StandardCharsets.US_ASCII);if(line.length()<66||line.charAt(64)!='\t')throw new IOException("Invalid player frame");
                String payload=line.substring(65);byte[] body=Base64.getDecoder().decode(payload);
                if(!Base64.getEncoder().encodeToString(body).equals(payload)||!PlayerUpdateCodec.sha((chain+":"+payload).getBytes(StandardCharsets.US_ASCII)).equals(line.substring(0,64)))throw new IOException("Player frame checksum mismatch");
                try(var in=new DataInputStream(new ByteArrayInputStream(body))){
                    int kind=in.readUnsignedByte();
                    if(kind==0){if(state!=null)throw new IOException("Duplicate initial frame");state=PlayerUpdateCodec.readInitial(in);}
                    else if(state==null)throw new IOException("Missing initial frame");
                    else if(kind==2)state=PlayerUpdateMachine.acquired(state);
                    else if(kind==1){
                        Command command=PlayerUpdateCodec.readCommand(in);if(!seen.add(command.request()))throw new IOException("Duplicate recorded request");
                        Result result=PlayerUpdateMachine.advance(state,command);if(!result.accepted())throw new IOException("Invalid recorded transition: "+result.reason());state=result.state();remember(receipts,new Receipt(command.request(),PlayerUpdateCodec.fingerprint(command),state.revision()));
                    }else throw new IOException("Unknown player event");
                    if(in.available()!=0)throw new IOException("Trailing event bytes");
                }
                chain=line.substring(0,64);start=i+1;
            }
            if(state==null||bytes.length-start>MAX_FRAME)throw new IOException("Missing/oversized player journal");
            return new Loaded(state,receipts,start,bytes.length-start,events,chain,Set.copyOf(seen));
        }catch(IllegalArgumentException|ArithmeticException e){throw new IOException("Invalid player journal values",e);}
    }
    private static State parseHeader(byte[] bytes)throws IOException{try(var in=new DataInputStream(new ByteArrayInputStream(bytes))){if(in.readByte()!=0)throw new IOException("Header required");return PlayerUpdateCodec.readInitial(in);}}
    private static void remember(LinkedHashMap<UUID,Receipt> receipts,Receipt receipt){receipts.put(receipt.request(),receipt);if(receipts.size()>MAX_RECEIPTS)receipts.remove(receipts.keySet().iterator().next());}
    private static byte[] read(FileChannel data)throws IOException{
        if(data.size()>MAX_BYTES)throw new IOException("Player journal size bound");var out=new ByteArrayOutputStream();var buf=ByteBuffer.allocate(8192);data.position(0);
        while(data.read(buf)>0){buf.flip();if(out.size()+buf.remaining()>MAX_BYTES)throw new IOException("Player journal size bound");out.write(buf.array(),0,buf.remaining());buf.clear();}return out.toByteArray();
    }
    private static Object key(Path path)throws IOException{return Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();}
    private static void safe(Path path)throws IOException{for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked player journal paths unsupported");}
    @Override public synchronized void close()throws IOException{
        if(closed)return;closed=true;IOException failure=null;
        try{data.close();}catch(IOException e){failure=e;}try{lock.release();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}try{owner.close();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}finally{OWNERS.remove(root);}if(failure!=null)throw failure;
    }
}
