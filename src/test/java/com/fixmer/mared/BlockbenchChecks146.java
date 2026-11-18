package com.fixmer.mared;

import com.fixmer.genesis.technology.creation.*;
import com.fixmer.genesis.technology.scripts.SourceProjects;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Real project I/O checks. These do not claim to test the desktop application. */
public final class BlockbenchChecks146 {
    private static int count;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
    @FunctionalInterface private interface IO {void run()throws Exception;}
    private static void refused(IO operation,String message)throws Exception{try{operation.run();throw new AssertionError(message);}catch(IOException expected){count++;}}
    public static void main(String[] args)throws Exception{
        for(String path:List.of("model.bbmodel","models/example.bbmodel","assets/test/Модель.bbmodel","models/My Model.BBMODEL"))check(BlockbenchDocument146.validPath(path),"valid model path: "+path);
        for(String path:List.of("../model.bbmodel","/tmp/model.bbmodel","C:/model.bbmodel","models\\bad.bbmodel","model.json","models/CON.bbmodel","models//bad.bbmodel","model.bbmodel.exe",".genesis/model.bbmodel"))check(!BlockbenchDocument146.validPath(path),"reject unsafe path: "+path);
        for(String language:List.of("ru","en")){
            check(com.fixmer.mared.technology.scripts.ScriptHelpManual.load(language).article("blockbench146").id().equals("blockbench146"),"Scripts help includes Blockbench: "+language);
            check(com.fixmer.mared.technology.scripts.ScriptHelpManual.loadCommands133(language).article("blockbench146").id().equals("blockbench146"),"Commands help includes Blockbench: "+language);
        }
        Path root=Files.createTempDirectory("blockbench146-");
        try{
            SourceProjects storage=new SourceProjects(root);storage.createContent145("models",CreationCatalog145.Direction.RESOURCEPACKS);storage.createContent145("other",CreationCatalog145.Direction.RESOURCEPACKS);
            var document=BlockbenchDocument146.create(storage,"models");String first="{\"meta\":{\"model_format\":\"free\"},\"name\":\"Модель\",\"elements\":[],\"animations\":[{\"name\":\"idle\"}]}";
            check(document.content()==null&&document.path().isEmpty(),"new model has no disk baseline");
            document.save("models/model.bbmodel",first);check(storage.read("models","models/model.bbmodel").equals(first),"model + animation + Unicode saved in the ordinary project");
            check(document.path().equals("models/model.bbmodel")&&document.content().equals(first),"baseline follows a successful save");
            var reopened=BlockbenchDocument146.open(storage,"models",document.path());check(reopened.content().equals(first),"model can be reopened");
            storage.checkpoint("models",document.path(),first,first+" ");
            String next=first.replace("idle","walk");document.save(document.path(),next);check(storage.read("models",document.path()).equals(next),"subsequent save uses CAS");
            check(storage.recovery("models",document.path())==null,"obsolete text recovery cleared after model save");
            refused(()->reopened.save(reopened.path(),first+" "),"a stale editor must not overwrite an external save");
            check(reopened.content().equals(first),"failed save retains the old baseline");
            document.save("models/copy.bbmodel",next);check(storage.read("models","models/model.bbmodel").equals(next),"Save As preserves original");
            refused(()->document.save("models/model.bbmodel",first),"Save As cannot replace an existing model");
            check(document.path().equals("models/copy.bbmodel"),"failed Save As retains current target");
            var fresh=BlockbenchDocument146.create(storage,"models");refused(()->fresh.save("models/copy.bbmodel",first),"new model cannot overwrite an existing file");
            refused(()->fresh.save("../escape.bbmodel",first),"no traversal");
            refused(()->fresh.save("wrong.json",first),"no unrelated source write");
            refused(()->BlockbenchDocument146.open(storage,"models","missing.bbmodel"),"missing file does not become an empty project");
            check(storage.files("other").stream().noneMatch(p->p.endsWith(".bbmodel")),"captured project isolation");
            String oversized="x".repeat(SourceProjects.MAX_BYTES+1);refused(()->fresh.save("huge.bbmodel",oversized),"size bound");
            check(!storage.files("models").contains("huge.bbmodel"),"oversized model creates no file");
            String utf8="я".repeat(SourceProjects.MAX_BYTES/2+1);refused(()->fresh.save("utf8.bbmodel",utf8),"byte bound, not just character count");
            Path external=root.getParent().resolve(root.getFileName()+"-external");Files.createDirectory(external);
            try{
                storage.linkFolder133("linked",external);var linked=BlockbenchDocument146.create(storage,"linked");linked.save("models/linked.bbmodel",first);
                check(Files.readString(external.resolve("models/linked.bbmodel")).equals(first),"linked folder saves to its real root");
                try{Files.createSymbolicLink(external.resolve("escape"),root.resolve("other"));refused(()->linked.save("escape/no.bbmodel",first),"symlink cannot leave the project");}catch(UnsupportedOperationException|java.nio.file.FileSystemException unavailable){System.out.println("Symlink check unavailable: "+unavailable);}
            }finally{try(var paths=Files.walk(external)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
            check(CreationCatalog145.section(CreationCatalog145.Direction.MODS,"animations").accepts("models/model.bbmodel"),"animation source visible in nested animation tab");
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("Blockbench checks: "+count+" passed. Native game/window acceptance remains separate.");
    }
}
