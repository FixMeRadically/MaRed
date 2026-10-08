package com.fixmer.genesis.technology.links;
import java.util.*;
import com.fixmer.genesis.technology.links.ScriptExports.*;
/** Shared typed index; a host supplies execution payloads. Publishing is atomic and rejects ambiguity. */
public final class CapabilityRegistry<T> {
    public record Symbol(Reference reference,String source,String function,String title,List<String> parameters) {
        public Symbol {
            Objects.requireNonNull(reference);
            Objects.requireNonNull(source);
            Objects.requireNonNull(function);
            Objects.requireNonNull(title);
            parameters=List.copyOf(parameters);
            if(parameters.size()>128||new HashSet<>(parameters).size()!=parameters.size())throw new IllegalArgumentException("Invalid function signature");
        }
    }
    public record Entry<T>(Symbol symbol,T target) {
        public Entry {
            Objects.requireNonNull(symbol);
            Objects.requireNonNull(target);
        }
    }
    public record Snapshot<T>(long revision,Map<Reference,Entry<T>> entries) {
        public Snapshot {
            entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        }
        public Entry<T> resolve(Reference reference) {
            var value=entries.get(reference);
            if(value==null)throw new IllegalArgumentException("Missing export: "+reference);
            return value;
        }
        public List<Entry<T>> all(Kind kind) {
            return entries.values().stream().filter(e->kind==null||e.symbol.reference.kind()==kind).toList();
        }
        public String problem(ScriptLinks.Binding binding) {
            var entry=entries.get(binding.reference());
            if(entry==null)return "Missing or changed export: "+binding.reference().export();
            if(!binding.arguments().keySet().equals(new HashSet<>(entry.symbol.parameters())))return "Arguments differ from "+entry.symbol.function()+entry.symbol.parameters();
            return null;
        }
    }
    private volatile Snapshot<T> snapshot=new Snapshot<>(0,Map.of());
    public Snapshot<T> snapshot() {
        return snapshot;
    }
    public synchronized void replace(Collection<Entry<T>> entries) {
        if(entries.size()>16384)throw new IllegalArgumentException("Export limit exceeded");
        var next=new LinkedHashMap<Reference,Entry<T>>();
        var identities=new HashSet<String>();
        for(var entry:entries) {
            var ref=entry.symbol.reference();
            String identity=ref.module()+"/"+ref.export();
            if(!identities.add(identity))throw new IllegalArgumentException("Duplicate export identity: "+identity);
            next.put(ref,entry);
        }
        if(!next.equals(snapshot.entries()))snapshot=new Snapshot<>(snapshot.revision()+1,next);
    }
}
