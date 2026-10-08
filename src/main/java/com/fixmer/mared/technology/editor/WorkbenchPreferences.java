package com.fixmer.mared.technology.editor;

import com.fixmer.mared.Mared;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import net.neoforged.fml.loading.FMLPaths;

/** One load per Studio session; atomic writes on exit, never from the render loop. */
public final class WorkbenchPreferences {
    public int version=2,selectedTab=0;
    public double filesRatio=.18,helpRatio=.29,logsRatio=.24;
    public boolean filesOpen=true,helpOpen=false,logsOpen=false,background=true,animation=true,depth=true;
    public boolean helpDocked=false;
    public double helpWindowX=.18,helpWindowY=.10,helpWindowW=.70,helpWindowH=.80;
    private static Path path(){return FMLPaths.CONFIGDIR.get().resolve("mared/editor_workbench.json");}
    public static WorkbenchPreferences load(){
        Path path=path();
        try{
            if(!Files.exists(path))return new WorkbenchPreferences();
            if(Files.size(path)>16384)throw new java.io.IOException("Oversized workbench settings");
            var p=new GsonBuilder().create().fromJson(Files.readString(path),WorkbenchPreferences.class);
            if(p==null||(p.version!=1&&p.version!=2))throw new java.io.IOException("Unsupported workbench settings version");
            if(p.version==1){p.version=2;p.helpOpen=false;p.helpDocked=false;}
            p.helpWindowX=position(p.helpWindowX,.18);p.helpWindowY=position(p.helpWindowY,.10);
            p.helpWindowW=position(p.helpWindowW,.70);p.helpWindowH=position(p.helpWindowH,.80);
            p.selectedTab=Math.max(0,Math.min(7,p.selectedTab));p.filesRatio=ratio(p.filesRatio,.18);p.helpRatio=ratio(p.helpRatio,.29);p.logsRatio=ratio(p.logsRatio,.24);return p;
        }catch(Exception e){Mared.LOGGER.warn("[editor] Cannot load workbench settings; using defaults",e);return new WorkbenchPreferences();}
    }
    private static double position(double value,double fallback){return Double.isFinite(value)?Math.max(0,Math.min(1,value)):fallback;}
    private static double ratio(double value,double fallback){return Double.isFinite(value)?Math.max(.08,Math.min(.6,value)):fallback;}
    public void save(){
        Path path=path(),tmp=null;
        try{
            Files.createDirectories(path.getParent());tmp=Files.createTempFile(path.getParent(),"workbench-",".tmp");
            Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(this));
            try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception e){Mared.LOGGER.warn("[editor] Cannot save workbench settings",e);}
        finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(Exception ignored){}}
    }
}
