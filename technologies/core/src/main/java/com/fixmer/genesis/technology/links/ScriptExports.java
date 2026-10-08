package com.fixmer.genesis.technology.links;
import java.util.*;
/** Explicit metadata from top-level comments, never regex matches inside function bodies/strings. */
public final class ScriptExports {
    private ScriptExports() {
    }
    public enum Kind {
        ACTION, CONDITION
    }
    public record Reference(UUID module,UUID export,Kind kind) {
        public Reference {
            Objects.requireNonNull(module);
            Objects.requireNonNull(export);
            Objects.requireNonNull(kind);
        }
    }
    public record Export(UUID id,Kind kind,String function,String title,int line) {
        public Export {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            if(function==null||!function.matches("[a-zA-Z_][a-zA-Z0-9_]*"))throw new IllegalArgumentException("Invalid exported function name");
            if(title==null||title.length()>256)throw new IllegalArgumentException("Invalid export title");
        }
    }
    public record Module(UUID id,List<Export> exports) {
        public Module {
            exports=List.copyOf(exports);
            if(!exports.isEmpty()&&id==null)throw new IllegalArgumentException("Exports need @genesis module UUID");
        }
    }
    public static Module read(String source) {
        Objects.requireNonNull(source);
        if(source.length()>1_048_576)throw new IllegalArgumentException("Module exceeds 1 MiB of text");
        UUID module=null;
        var exports=new ArrayList<Export>();
        var ids=new HashSet<UUID>();
        var names=new HashSet<String>();
        char quote=0;
        int depth=0,line=1;
        for(int i=0;i<source.length();i++) {
            char c=source.charAt(i);
            if(c=='\n')line++;
            if(quote!=0) {
                if(c=='\\') {
                    if(i+1<source.length()&&source.charAt(i+1)=='\n')line++;
                    i++;
                }
                else if(c==quote)quote=0;
                continue;
            }
            if(c=='\''||c=='"') {
                quote=c;
                continue;
            }
            if(c=='/'&&i+1<source.length()&&source.charAt(i+1)=='/') {
                int end=source.indexOf('\n',i);
                if(end<0)end=source.length();
                String comment=source.substring(i+2,end).strip();
                if(depth==0&&comment.startsWith("@genesis ")) {
                    String text=comment.substring(9).strip();
                    try {
                        if(text.startsWith("module ")) {
                            if(module!=null)throw new IllegalArgumentException("Duplicate module directive");
                            module=uuid(text.substring(7).strip());
                        }
                        else if(text.startsWith("export ")) {
                            String[] sections=text.substring(7).split("\\|",2);
                            String[] fields=sections[0].strip().split("\\s+");
                            if(fields.length!=3)throw new IllegalArgumentException("Expected: export UUID action|condition function | title");
                            var id=uuid(fields[0]);
                            var kind=Kind.valueOf(fields[1].toUpperCase(Locale.ROOT));
                            String name=fields[2];
                            if(!ids.add(id)||!names.add(name))throw new IllegalArgumentException("Duplicate export id or function");
                            if(exports.size()>=256)throw new IllegalArgumentException("Too many exports in module");
                            exports.add(new Export(id,kind,name,sections.length==2?sections[1].strip():name,line));
                        }
                        else throw new IllegalArgumentException("Unknown @genesis directive");
                    }
                    catch(IllegalArgumentException error) {
                        throw new IllegalArgumentException("Line "+line+": "+error.getMessage(),error);
                    }
                }
                i=end-1;
                continue;
            }
            if(c=='{')depth++;
            else if(c=='}')depth--;
        }
        return new Module(module,exports);
    }
    public static UUID uuid(String value) {
        UUID id=UUID.fromString(value);
        if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException("UUID must use its full canonical form");
        return id;
    }
}
