package com.fixmer.mared.commands.registry;

import com.fixmer.genesis.technology.catalog.CommandTree;
import com.fixmer.mared.commands.handbook.MaredCommandsHandbookData;
import java.util.*;

/** Compatibility facade over a live MC snapshot and separate MR documentation. No bundled MC templates. */
public final class MaredCommandRegistry {
    private MaredCommandRegistry() {}
    public enum Source { MC, MR }
    public static final class NbtHint {
        public final String tag,what,why,example;
        public NbtHint(String tag,String what,String why,String example){this.tag=tag;this.what=what;this.why=why;this.example=example;}
    }
    public static final class Argument {
        public final String value,description;
        public final List<String> examples;
        public Argument(String value,String description,List<String> examples){this.value=value;this.description=description;this.examples=List.copyOf(examples);}
    }
    public static final class CommandInfo {
        public final String name,category,description,example;
        /** -1: unknown. A client command tree does not expose server permission levels. */
        public final int opLevel;
        public final List<Argument> arguments;
        public final List<NbtHint> nbtHints;
        public final Source source;
        public final List<String> usages;
        public final boolean truncated;
        public CommandInfo(String name,String category,int opLevel,String description,String example,List<Argument> arguments,List<NbtHint> nbtHints){
            this(name,category,opLevel,description,example,arguments,nbtHints,Source.MC,List.of(example),false);
        }
        public CommandInfo(String name,String category,int opLevel,String description,String example,List<Argument> arguments,List<NbtHint> nbtHints,Source source,List<String> usages,boolean truncated){
            this.name=name;this.category=category;this.opLevel=opLevel;this.description=description;this.example=example;
            this.arguments=List.copyOf(arguments);this.nbtHints=List.copyOf(nbtHints);this.source=source;this.usages=List.copyOf(usages);this.truncated=truncated;
        }
    }
    private static CommandTree tree=CommandTree.empty();
    private static long revision;
    private static List<CommandInfo> mc=List.of(),mr;
    private static final Map<String,CommandInfo> details=new HashMap<>();
    /** Call on client main thread after receiving/replacing a dispatcher, or with empty() on disconnect. */
    public static synchronized void install(CommandTree next){
        tree=Objects.requireNonNull(next);details.clear();revision++;
        mc=tree.names().stream().map(name->new CommandInfo(name,category(name),-1,"Данные текущего подключения Minecraft",name,List.of(),List.of(),Source.MC,List.of(),tree.truncated())).toList();
    }
    public static synchronized long revision(){return revision;}
    public static synchronized CommandTree snapshot(){return tree;}
    private static String category(String name){int colon=name.indexOf(':');return colon<0?"MC":"MC / "+name.substring(0,colon);}
    public static synchronized void load(){
        if(mr!=null)return;
        mr=new MaredCommandsHandbookData().mrEntries().stream().map(e->new CommandInfo(e.name,e.category,-1,
            e.shortDescription+(e.fullDescription==null?"":"\n"+e.fullDescription),e.syntax,
            e.parameters.stream().map(p->new Argument(p.name,p.type+": "+p.description,List.of())).toList(),List.of(),Source.MR,List.of(e.syntax),false)).toList();
    }
    public static synchronized void reload(){mr=null;details.clear();load();revision++;}
    /** Kept for callers of the old resource cache API; MC no longer uses resources. */
    public static void invalidateFileCache(){}
    public static List<CommandInfo> all(){return all(Source.MC);}
    public static synchronized List<CommandInfo> all(Source source){load();return source==Source.MC?mc:mr;}
    public static CommandInfo findByName(String name){return findByName(name,Source.MC);}
    public static synchronized CommandInfo findByName(String name,Source source){
        if(name==null)return null;String key=name.startsWith("/")?name.substring(1):name;
        if(source==Source.MR)return all(source).stream().filter(c->c.name.equals(key)).findFirst().orElse(null);
        if(mc.stream().noneMatch(c->c.name.equals(key)))return null;
        return details.computeIfAbsent(key,n->{
            var detail=tree.describe(n);var args=detail.parameters().stream().map(p->new Argument(p.path(),p.type(),p.examples())).toList();
            String description="Синтаксис из текущего дерева Minecraft. Доступные команды определяет подключение.\n"+
                "Уровень OP, описание и схема произвольного NBT не передаются в дереве команд.";
            if(!detail.redirects().isEmpty())description+="\n"+String.join("\n",detail.redirects());
            if(detail.truncated())description+="\nСложное дерево сокращено по лимиту; список вариантов неполный.";
            return new CommandInfo(n,category(n),-1,description,detail.usages().isEmpty()?n:detail.usages().getFirst(),args,List.of(),Source.MC,detail.usages(),detail.truncated());
        });
    }
    public static List<CommandInfo> search(String query){return search(query,Source.MC,null);}
    public static List<CommandInfo> search(String query,Source source,String category){
        String q=query==null?"":query.toLowerCase(Locale.ROOT).strip();
        return all(source).stream().filter(c->category==null||category.equals(c.category))
            .filter(c->c.name.toLowerCase(Locale.ROOT).contains(q)||c.description.toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    public static List<String> categories(){return categories(Source.MC);}
    public static List<String> categories(Source source){return all(source).stream().map(c->c.category).distinct().sorted().toList();}
}
