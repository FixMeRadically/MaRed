package com.fixmer.genesis.technology.workspace;
import com.fixmer.genesis.technology.server.remote.ControlWire73;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Actor identity is absent from the wire: only the current ServerPlayer supplies it. */
public final class WorkspaceWire112 {
 public static final int MAX_PACKET=32000,MAX_TEXT=16384;
 public static final Set<String> OPS=Set.of("LIST","CREATE","VIEW","READ","SAVE","NEW","RENAME","DELETE","MEMBER");
 private static final Set<String> FIELDS=Set.of("request","operation","project","path","file","text","revision","projectRevision","target","role","other");
 private WorkspaceWire112(){}
 public record Request(UUID request,String operation,String project,String path,String file,String text,long revision,long projectRevision,String target,String role,String other){
  public Request{
   for(Object value:List.of(request,operation,project,path,file,text,target,role,other))Objects.requireNonNull(value);
   if(request.equals(new UUID(0,0))||!OPS.contains(operation)||path.length()>120||other.length()>120||revision<0||projectRevision<0||text.getBytes(StandardCharsets.UTF_8).length>MAX_TEXT)throw new IllegalArgumentException("INVALID");unicode(text);
   for(String id:List.of(project,file,target))if(!id.isEmpty()&&(id.length()!=36||!UUID.fromString(id).toString().equals(id)))throw new IllegalArgumentException("INVALID");
   try{if(!path.isEmpty())WorkspaceStore112.path(path);if(!other.isEmpty())WorkspaceStore112.path(other);}catch(IOException bad){throw new IllegalArgumentException("INVALID_PATH",bad);}
   boolean nm=target.isEmpty()&&role.isEmpty(),nf=path.isEmpty()&&file.isEmpty();
   boolean valid=switch(operation){
    case "LIST","CREATE"->project.isEmpty()&&nf&&nm&&other.isEmpty()&&revision==0&&projectRevision==0&&(operation.equals("CREATE")||text.isEmpty());
    case "VIEW"->!project.isEmpty()&&nf&&text.isEmpty()&&nm&&other.isEmpty()&&revision==0;
    case "READ"->!project.isEmpty()&&!path.isEmpty()&&text.isEmpty()&&nm&&other.isEmpty()&&revision==0;
    case "SAVE"->!project.isEmpty()&&!path.isEmpty()&&!file.isEmpty()&&nm&&other.isEmpty()&&revision>0;
    case "NEW"->!project.isEmpty()&&!path.isEmpty()&&file.isEmpty()&&nm&&other.isEmpty()&&revision==0;
    case "DELETE","RENAME"->!project.isEmpty()&&!path.isEmpty()&&!file.isEmpty()&&text.isEmpty()&&nm&&revision>0&&(operation.equals("RENAME")?!other.isEmpty():other.isEmpty());
    case "MEMBER"->!project.isEmpty()&&nf&&text.isEmpty()&&!target.isEmpty()&&Set.of("NONE","VIEWER","EDITOR","MANAGER","OWNER").contains(role)&&other.isEmpty()&&revision==0;
    default->false;};if(!valid)throw new IllegalArgumentException("INVALID");
  }
 }
 public static void unicode(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("INVALID");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("INVALID");}}
 public static byte[] encode(Map<String,String> d)throws IOException{byte[] bytes=ControlWire73.encode(d);if(bytes.length>MAX_PACKET)throw new IOException("LIMIT");return bytes;}
 public static Map<String,String> decode(byte[] b)throws IOException{if(b.length>MAX_PACKET)throw new IOException("LIMIT");return ControlWire73.decode(b);}
 public static byte[] encode(Request q)throws IOException{var d=new TreeMap<String,String>();d.put("request",q.request().toString());d.put("operation",q.operation());d.put("project",q.project());d.put("path",q.path());d.put("file",q.file());d.put("text",q.text());d.put("revision",Long.toString(q.revision()));d.put("projectRevision",Long.toString(q.projectRevision()));d.put("target",q.target());d.put("role",q.role());d.put("other",q.other());return encode(d);}
 public static Request request(byte[] b)throws IOException{var d=decode(b);if(!d.keySet().equals(FIELDS))throw new IOException("INVALID");try{var q=new Request(UUID.fromString(d.get("request")),d.get("operation"),d.get("project"),d.get("path"),d.get("file"),d.get("text"),Long.parseLong(d.get("revision")),Long.parseLong(d.get("projectRevision")),d.get("target"),d.get("role"),d.get("other"));if(!Arrays.equals(b,encode(q)))throw new IOException("INVALID");return q;}catch(RuntimeException bad){throw new IOException("INVALID",bad);}}
}
