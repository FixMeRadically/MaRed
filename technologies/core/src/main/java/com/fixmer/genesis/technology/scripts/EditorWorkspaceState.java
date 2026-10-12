package com.fixmer.genesis.technology.scripts;

import com.fixmer.genesis.technology.storage.ProjectPath;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

/** Portable local editor context. Stores paths/UI state, never source text or executable settings. */
public final class EditorWorkspaceState {
    public static final int MAX_BYTES=64*1024,MAX_DOCUMENTS=16,MAX_VIEWS=32,MAX_EXPANDED=128;
    private static final String NAME=".genesis-workspace.properties";
    public record Document(String project,String file,boolean selected) {}
    public record View(String project,List<String> expanded,String folder,String entry,int scroll){public View{expanded=List.copyOf(expanded);}}
    public record State(String project,List<Document> documents,List<View> views){public State{documents=List.copyOf(documents);views=List.copyOf(views);}}
    public static State empty(){return new State("",List.of(),List.of());}
    private final Path root;
    public EditorWorkspaceState(Path root){this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();}
    private static void require(boolean condition,String reason)throws IOException{if(!condition)throw new IOException(reason);}
    private static boolean path(String value,boolean empty){return value!=null&&(empty&&value.isEmpty()||ProjectPath.valid(value));}
    private static void validate(State state)throws IOException{
        require(state!=null&&state.project()!=null&&(state.project().isEmpty()||SourceProjects.validProject(state.project())),"Invalid workspace project");
        require(state.documents().size()<=MAX_DOCUMENTS&&state.views().size()<=MAX_VIEWS,"Workspace count limit");
        var docs=new HashSet<String>();var selected=new HashSet<String>();var views=new HashSet<String>();
        for(Document d:state.documents()){
            require(d!=null&&SourceProjects.validProject(d.project())&&path(d.file(),false),"Invalid workspace document");
            require(docs.add(d.project().toLowerCase(Locale.ROOT)+"/"+d.file().toLowerCase(Locale.ROOT)),"Duplicate workspace document");
            require(!d.selected()||selected.add(d.project().toLowerCase(Locale.ROOT)),"Multiple selected documents in project");
        }
        for(View v:state.views()){
            require(v!=null&&SourceProjects.validProject(v.project())&&views.add(v.project().toLowerCase(Locale.ROOT)),"Invalid or duplicate workspace view");
            require(v.expanded().size()<=MAX_EXPANDED&&path(v.folder(),true)&&path(v.entry(),true)&&v.scroll()>=0&&v.scroll()<=10000,"Invalid workspace view state");
            var paths=new HashSet<String>();for(String p:v.expanded())require(path(p,false)&&paths.add(p.toLowerCase(Locale.ROOT)),"Invalid expanded workspace folder");
        }
    }
    private void safeRoot()throws IOException{
        for(Path p=root;p!=null;p=p.getParent())require(!Files.isSymbolicLink(p),"Linked workspace root is not supported");
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Workspace root is not a directory");
    }
    public State load()throws IOException{
        safeRoot();Path file=root.resolve(NAME);if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return empty();
        require(!Files.isSymbolicLink(file)&&Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Invalid workspace file");
        require(file.toRealPath().getParent().equals(root.toRealPath()),"Workspace file escapes root");
        byte[] bytes;try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){bytes=in.readNBytes(MAX_BYTES+1);}
        require(bytes.length<=MAX_BYTES,"Workspace file exceeds 64 KiB");Properties p=new Properties();
        try{String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();p.load(new StringReader(text));}
        catch(IllegalArgumentException|CharacterCodingException error){throw new IOException("Malformed workspace context",error);}
        require("1".equals(p.getProperty("format")),"Unsupported workspace context format");
        int dc=number(p,"documents",MAX_DOCUMENTS),vc=number(p,"views",MAX_VIEWS);var docs=new ArrayList<Document>();var views=new ArrayList<View>();
        for(int i=0;i<dc;i++){String k="document."+i+".";String selected=p.getProperty(k+"selected","");require(selected.equals("true")||selected.equals("false"),"Invalid workspace selection");docs.add(new Document(p.getProperty(k+"project"),p.getProperty(k+"file"),Boolean.parseBoolean(selected)));}
        for(int i=0;i<vc;i++){String k="view."+i+".";int count=number(p,k+"expanded",MAX_EXPANDED);var expanded=new ArrayList<String>();for(int j=0;j<count;j++)expanded.add(p.getProperty(k+"folder."+j,""));views.add(new View(p.getProperty(k+"project"),expanded,p.getProperty(k+"folder",""),p.getProperty(k+"entry",""),number(p,k+"scroll",10000)));}
        State state=new State(p.getProperty("project",""),docs,views);validate(state);return state;
    }
    private static int number(Properties p,String key,int max)throws IOException{try{int n=Integer.parseInt(p.getProperty(key,""));require(n>=0&&n<=max,"Invalid workspace count: "+key);return n;}catch(NumberFormatException error){throw new IOException("Invalid workspace number: "+key,error);}}
    public void save(State state)throws IOException{
        validate(state);Properties p=new Properties();p.setProperty("format","1");p.setProperty("project",state.project());p.setProperty("documents",Integer.toString(state.documents().size()));p.setProperty("views",Integer.toString(state.views().size()));
        for(int i=0;i<state.documents().size();i++){var d=state.documents().get(i);String k="document."+i+".";p.setProperty(k+"project",d.project());p.setProperty(k+"file",d.file());p.setProperty(k+"selected",Boolean.toString(d.selected()));}
        for(int i=0;i<state.views().size();i++){var v=state.views().get(i);String k="view."+i+".";p.setProperty(k+"project",v.project());p.setProperty(k+"folder",v.folder());p.setProperty(k+"entry",v.entry());p.setProperty(k+"scroll",Integer.toString(v.scroll()));p.setProperty(k+"expanded",Integer.toString(v.expanded().size()));for(int j=0;j<v.expanded().size();j++)p.setProperty(k+"folder."+j,v.expanded().get(j));}
        var text=new StringWriter();p.store(text,"Genesis editor context; no source text");byte[] bytes=text.toString().getBytes(StandardCharsets.UTF_8);require(bytes.length<=MAX_BYTES,"Workspace context exceeds 64 KiB");
        safeRoot();Files.createDirectories(root);safeRoot();Path target=root.resolve(NAME),tmp=null;
        require(!Files.isSymbolicLink(target),"Linked workspace file is not supported");
        try{tmp=Files.createTempFile(root,".workspace-",".tmp");Files.write(tmp,bytes,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}
        finally{if(tmp!=null)Files.deleteIfExists(tmp);}
    }
}
