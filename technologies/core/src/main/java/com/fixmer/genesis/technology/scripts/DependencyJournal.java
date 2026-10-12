package com.fixmer.genesis.technology.scripts;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
/** Durable bounded intent log for the four owned dependency files. */
final class DependencyJournal  {
    static final String NAME=".genesis-dependency-update";
    private DependencyJournal() {
    }
    static Path path(Path root)throws IOException {
        Path p=root.resolve(NAME);
        for(Path a=p;a!=null;a=a.getParent())if(Files.isSymbolicLink(a))throw new IOException("Linked dependency journal");
        return p;
    }
    static final class Transaction implements AutoCloseable {
        final FileChannel channel;
        final java.nio.channels.FileLock lock;
        Transaction(FileChannel c,java.nio.channels.FileLock l) {
            channel=c;
            lock=l;
        }
        public void close()throws IOException {
            try {
                lock.close();
            }
            finally {
                channel.close();
            }
        }
    }
    static Transaction acquire(Path root)throws IOException {
        Path dir=root.resolve(".genesis-build");
        if(Files.isSymbolicLink(dir))throw new IOException("Linked dependency lock directory");
        Files.createDirectories(dir);
        if(!dir.toRealPath().startsWith(root.toRealPath()))throw new IOException("Dependency lock directory escapes project");
        Path file=dir.resolve("dependencies.lock");
        var channel=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        try {
            var lock=channel.tryLock();
            if(lock==null)throw new IOException("Another process is updating dependencies");
            return new Transaction(channel,lock);
        }
        catch(java.nio.channels.OverlappingFileLockException busy) {
            channel.close();
            throw new IOException("Another editor is updating dependencies",busy);
        }
        catch(IOException|RuntimeException bad) {
            channel.close();
            throw bad;
        }
    }
    static Transaction start(Path root,Map<String,byte[]> before,Map<String,byte[]> after)throws IOException {
        var transaction=acquire(root);
        try {
            Path file=path(root);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unrecovered dependency transaction");
            var data=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(data)) {
                out.writeInt(0x47445431);
                out.writeInt(after.size());
                for(var e:after.entrySet()) {
                    out.writeUTF(e.getKey());
                    bytes(out,before.get(e.getKey()));
                    bytes(out,e.getValue());
                }
            }
            Path pending=Files.createTempFile(root,".dependency-journal-",".tmp");
            try {
                Files.write(pending,data.toByteArray());
                try(var channel=FileChannel.open(pending,StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE);
            }
            finally {
                Files.deleteIfExists(pending);
            }
            return transaction;
        }
        catch(IOException|RuntimeException failure) {
            transaction.close();
            throw failure;
        }
    }
    static void bytes(DataOutputStream out,byte[] bytes)throws IOException {
        out.writeInt(bytes==null?-1:bytes.length);
        if(bytes!=null)out.write(bytes);
    }
    static byte[] bytes(DataInputStream in)throws IOException {
        int size=in.readInt();
        if(size==-1)return null;
        if(size<0||size>SourceProjects.MAX_BYTES)throw new IOException("Dependency journal file limit");
        byte[] b=in.readNBytes(size);
        if(b.length!=size)throw new EOFException("Truncated dependency journal");
        return b;
    }
    static void recover(Path root)throws IOException {
        if(!Files.exists(path(root),LinkOption.NOFOLLOW_LINKS))return;
        try(var lock=acquire(root)) {
            recoverOwned(root);
        }
    }
    static void recoverOwned(Path root)throws IOException {
        Path journal=path(root);
        if(!Files.exists(journal,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.size(journal)>40L*1024*1024)throw new IOException("Dependency journal limit");
        var before=new LinkedHashMap<String,byte[]>();
        var after=new LinkedHashMap<String,byte[]>();
        try(var in=new DataInputStream(Files.newInputStream(journal,LinkOption.NOFOLLOW_LINKS))) {
            if(in.readInt()!=0x47445431)throw new IOException("Invalid dependency journal");
            int count=in.readInt();
            if(count<1||count>4)throw new IOException("Invalid dependency journal count");
            for(int i=0;i<count;i++) {
                String name=in.readUTF();
                if(!ProjectDependencies.FILES.contains(name)||after.containsKey(name))throw new IOException("Invalid dependency journal path");
                before.put(name,bytes(in));
                byte[] next=bytes(in);
                if(next==null)throw new IOException("Missing journal after bytes");
                after.put(name,next);
            }
            if(in.read()!=-1)throw new IOException("Dependency journal trailing data");
        }
        boolean completed=true;
        var current=new HashMap<String,byte[]>();
        for(String name:after.keySet()) {
            Path p=safe(root,name);
            byte[] actual=Files.exists(p,LinkOption.NOFOLLOW_LINKS)?read(p):null;
            current.put(name,actual);
            boolean done=Arrays.equals(actual,after.get(name));
            completed&=done;
            if(!done&&!Arrays.equals(actual,before.get(name)))throw new IOException("Dependency transaction conflicts with external edit: "+name+"; preserve the journal and resolve the file before retrying");
        }
        if(!completed)for(String name:after.keySet())if(Arrays.equals(current.get(name),after.get(name))) {
            Path p=safe(root,name);
            byte[] original=before.get(name);
            if(original==null)Files.deleteIfExists(p);
            else {
                Files.createDirectories(p.getParent());
                Path temp=Files.createTempFile(p.getParent(),".dependency-recover-",".tmp");
                try {
                    Files.write(temp,original);
                    Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                }
                finally {
                    Files.deleteIfExists(temp);
                }
            }
        }
        Files.delete(journal);
    }
    private static byte[] read(Path p)throws IOException {
        try(var in=Files.newInputStream(p,LinkOption.NOFOLLOW_LINKS)) {
            byte[] b=in.readNBytes(SourceProjects.MAX_BYTES+1);
            if(b.length>SourceProjects.MAX_BYTES)throw new IOException("Dependency file limit");
            return b;
        }
    }
    private static Path safe(Path root,String name)throws IOException {
        Path p=root.resolve(name).normalize();
        for(Path a=p;a!=null;a=a.getParent())if(Files.isSymbolicLink(a))throw new IOException("Linked dependency recovery file");
        Path ancestor=p;
        while(!Files.exists(ancestor,LinkOption.NOFOLLOW_LINKS))ancestor=ancestor.getParent();
        if(!ancestor.toRealPath().startsWith(root.toRealPath()))throw new IOException("Dependency recovery path escapes project");
        return p;
    }
}
