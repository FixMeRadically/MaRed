package com.fixmer.genesis.technology.links;
import java.util.*;
import com.fixmer.genesis.technology.links.ScriptExports.*;
/** Persistent values and dependency index. Function code is owned by its Script module. */
public final class ScriptLinks {
    private ScriptLinks() {
    }
    public record Binding(UUID id,String consumer,String slot,Reference reference,Map<String,String> arguments) {
        public Binding {
            Objects.requireNonNull(id);
            Objects.requireNonNull(reference);
            if(consumer==null||consumer.isBlank()||consumer.length()>256||slot==null||slot.isBlank()||slot.length()>64)throw new IllegalArgumentException("Invalid consumer/slot");
            arguments=Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
            if(arguments.size()>128)throw new IllegalArgumentException("Argument limit");
            for(var e:arguments.entrySet())if(!e.getKey().matches("[a-zA-Z_][a-zA-Z0-9_]*")||e.getValue()==null||e.getValue().isBlank()||e.getValue().length()>8192)throw new IllegalArgumentException("Invalid argument expression");
        }
    }
    public record AiProfile(UUID id,String title,String tag,boolean enabled,int interval,double range,UUID condition,UUID action) {
        public AiProfile {
            Objects.requireNonNull(id);
            Objects.requireNonNull(condition);
            Objects.requireNonNull(action);
            if(title==null||title.isBlank()||title.length()>128||tag==null||!tag.matches("genesis\\.ai\\.[a-zA-Z0-9_.-]{1,64}")||interval<1||interval>1200||!Double.isFinite(range)||range<1||range>128)throw new IllegalArgumentException("Invalid AI profile");
        }
        public String consumer() {
            return "ai:"+id;
        }
    }
    public record Project(List<Binding> bindings,List<AiProfile> profiles) {
        public Project {
            bindings=List.copyOf(bindings);
            profiles=List.copyOf(profiles);
            if(bindings.size()>4096||profiles.size()>256)throw new IllegalArgumentException("Project limit");
            var ids=new HashSet<UUID>();
            var slots=new HashSet<List<String>>();
            for(var b:bindings)if(!ids.add(b.id())||!slots.add(List.of(b.consumer(),b.slot())))throw new IllegalArgumentException("Duplicate binding id/slot");
            var profileIds=new HashSet<UUID>();
            var tags=new HashSet<String>();
            for(var p:profiles) {
                if(!profileIds.add(p.id())||!tags.add(p.tag()))throw new IllegalArgumentException("Duplicate AI profile id/tag");
                var c=find(bindings,p.condition());
                var a=find(bindings,p.action());
                if(c==null||a==null||!c.consumer().equals(p.consumer())||!a.consumer().equals(p.consumer())||!c.slot().equals("condition")||!a.slot().equals("action")||c.reference().kind()!=Kind.CONDITION||a.reference().kind()!=Kind.ACTION)throw new IllegalArgumentException("AI links do not belong to this profile or have wrong kinds");
            }
        }
        public static Project empty() {
            return new Project(List.of(),List.of());
        }
        public Binding binding(UUID id) {
            return find(bindings,id);
        }
        public Project with(AiProfile profile,Binding condition,Binding action) {
            var ps=new ArrayList<>(profiles);
            ps.removeIf(p->p.id().equals(profile.id()));
            ps.add(profile);
            var bs=new ArrayList<>(bindings);
            bs.removeIf(b->b.consumer().equals(profile.consumer()));
            bs.add(condition);
            bs.add(action);
            return new Project(bs,ps);
        }
        public Project without(UUID id) {
            var ps=profiles.stream().filter(p->!p.id().equals(id)).toList();
            var bs=bindings.stream().filter(b->!b.consumer().equals("ai:"+id)).toList();
            return new Project(bs,ps);
        }
    }
    private static Binding find(List<Binding> list,UUID id) {
        return list.stream().filter(b->b.id().equals(id)).findFirst().orElse(null);
    }
    public static final class Index {
        private final Map<Reference,List<Binding>> uses;
        private final Map<String,Set<Reference>> dependencies;
        public Index(Project project) {
            var u=new LinkedHashMap<Reference,List<Binding>>();
            var d=new LinkedHashMap<String,Set<Reference>>();
            for(var b:project.bindings()) {
                u.computeIfAbsent(b.reference(),k->new ArrayList<>()).add(b);
                d.computeIfAbsent(b.consumer(),k->new LinkedHashSet<>()).add(b.reference());
            }
            var frozen=new LinkedHashMap<Reference,List<Binding>>();
            u.forEach((k,v)->frozen.put(k,List.copyOf(v)));
            uses=Map.copyOf(frozen);
            var ds=new LinkedHashMap<String,Set<Reference>>();
            d.forEach((k,v)->ds.put(k,Set.copyOf(v)));
            dependencies=Map.copyOf(ds);
        }
        public List<Binding> usages(Reference reference) {
            return uses.getOrDefault(reference,List.of());
        }
        public Set<Reference> dependencies(String consumer) {
            return dependencies.getOrDefault(consumer,Set.of());
        }
    }
}
