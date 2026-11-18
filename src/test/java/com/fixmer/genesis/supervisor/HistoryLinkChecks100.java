package com.fixmer.genesis.supervisor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Test-only capability boundary. Never changes runtime link guards or requires administrator access. */
public final class HistoryLinkChecks100 {
    @FunctionalInterface interface Creator { void create(Path link,Path target)throws IOException; }
    private HistoryLinkChecks100() {}
    private static final ThreadLocal<Creator> deniedFixture100=new ThreadLocal<>();
    static boolean create(Path link,Path target,String kind)throws IOException {
        Creator creator=deniedFixture100.get();
        return attempt(link,target,kind,creator==null?(l,t)->Files.createSymbolicLink(l,t):creator,System.out::println);
    }
    static boolean attempt(Path link,Path target,String kind,Creator creator,Consumer<String> report)throws IOException {
        // Fixture mistakes are failures, not evidence that host capabilities are unavailable.
        if(!Files.isDirectory(link.getParent())||!Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new NoSuchFileException("History100 fixture parent/target missing");
        if(Files.exists(link,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException("History100 fixture link already exists");
        try { creator.create(link,target);return true; }
        catch(FileAlreadyExistsException|NoSuchFileException fixtureError){throw fixtureError;}
        catch(FileSystemException|UnsupportedOperationException|SecurityException unavailable){
            // ASCII and no provider message/path: Windows reasons can have a different console encoding.
            report.accept("SKIP History97 "+kind+" symlink checks: creation unavailable ("+unavailable.getClass().getSimpleName()+"). Other history checks continue.");
            return false;
        }
    }
    private static int checks;
    private static void ok(boolean result){checks++;if(!result)throw new AssertionError("History100 capability check "+checks);}
    @FunctionalInterface interface IO { void run()throws IOException; }
    private static void rejects(IO work)throws IOException {
        checks++;try{work.run();}catch(IOException expected){return;}throw new AssertionError("History100 swallowed an unexpected IO error");
    }
    public static void verify()throws IOException {
        checks=0;Path root=Files.createTempDirectory("history-capability100-");
        try {
            Path target=Files.writeString(root.resolve("target"),"protected"),link=root.resolve("link");var reports=new ArrayList<String>();
            ok(!attempt(link,target,"file",(l,t)->{throw new FileSystemException("PRIVATE_PATH",null,"PRIVATE_NATIVE_REASON");},reports::add));
            ok(reports.size()==1&&!reports.get(0).contains("PRIVATE_"));
            ok(!attempt(link,root,"directory",(l,t)->{throw new AccessDeniedException("PRIVATE_PATH");},reports::add));
            ok(!attempt(link,target,"file",(l,t)->{throw new UnsupportedOperationException();},reports::add));
            ok(!attempt(link,target,"file",(l,t)->{throw new SecurityException();},reports::add));
            rejects(()->attempt(link,target,"file",(l,t)->{throw new IOException("unexpected IO");},reports::add));
            rejects(()->attempt(link,target,"file",(l,t)->{throw new FileAlreadyExistsException("bad fixture");},reports::add));
            rejects(()->attempt(link,target,"file",(l,t)->{throw new NoSuchFileException("bad fixture");},reports::add));
            rejects(()->attempt(link,root.resolve("missing"),"file",(l,t)->{},reports::add));
            Files.writeString(link,"existing");rejects(()->attempt(link,target,"file",(l,t)->{},reports::add));Files.delete(link);
            ok(attempt(link,target,"file",(l,t)->{},reports::add));
            int before=reports.size();try{attempt(link,target,"file",(l,t)->{throw new AssertionError("security test failure");},reports::add);throw new IOException("AssertionError swallowed");}catch(AssertionError expected){ok(reports.size()==before);}
            ok(Files.readString(target).equals("protected"));
            System.out.println("History100 creation-capability/error-boundary checks passed: "+checks+"; injected provider denials are fixtures, not a Windows runtime test.");
        } finally {try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    public static void main(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("--without-links")){
            System.out.println("History100: injecting creation denial for this test thread only.");
            deniedFixture100.set((l,t)->{throw new FileSystemException("fixture path",null,"fixture privilege unavailable");});
            try{HistoryChecks97.main(new String[0]);}finally{deniedFixture100.remove();}
        }else if(args.length==0)verify();else throw new IllegalArgumentException("Unknown history test mode");
    }
}
