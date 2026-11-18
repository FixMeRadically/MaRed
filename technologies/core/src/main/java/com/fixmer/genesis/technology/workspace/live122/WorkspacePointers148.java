package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.workspace.collaboration121.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Ephemeral, document-anchored mouse pointers; never journalled or sent across project boundaries. */
public final class WorkspacePointers148 {
    private record Key(UUID room,UUID actor){}
    private record Pointer(String path,String label,int color,CollaborativeDocument121.Id anchor,int dx,int dy,long at){}
    private final Map<Key,Pointer> pointers=new HashMap<>();
    private final LongSupplier clock;
    public WorkspacePointers148(LongSupplier clock){this.clock=Objects.requireNonNull(clock);}
    private void prune(){long now=clock.getAsLong();pointers.values().removeIf(p->now-p.at>2_000_000_000L);}
    public synchronized void update(UUID room,UUID actor,String path,String label,int color,CollaborativeDocument121.Id anchor,int dx,int dy){
        prune();WorkspacePolicy121.path(path);Objects.requireNonNull(anchor);
        if(label==null||label.isBlank()||label.length()>48||label.chars().anyMatch(c->Character.isISOControl(c)||c=='|')||color<0||color>0xffffff||dx< -64||dx>64||dy<0||dy>16)throw new IllegalArgumentException("PRESENCE");
        Key key=new Key(room,actor);if(!pointers.containsKey(key)&&pointers.size()>=2048)throw new IllegalArgumentException("LIMIT");
        pointers.put(key,new Pointer(path,label,color,anchor,dx,dy,clock.getAsLong()));
    }
    public synchronized void remove(UUID room,UUID actor){pointers.remove(new Key(room,actor));}
    public synchronized String visible(UUID room,WorkspacePolicy121 policy,UUID viewer){
        prune();var rows=new ArrayList<String>();
        pointers.entrySet().stream().filter(e->e.getKey().room.equals(room)).sorted(Comparator.comparing(e->e.getKey().actor)).forEach(e->{
            var p=e.getValue();UUID actor=e.getKey().actor;
            if(policy.allows(actor,WorkspacePolicy121.Capability.READ,p.path)&&policy.panel(actor,WorkspacePolicy121.Panel.CODE,p.path)&&policy.allows(viewer,WorkspacePolicy121.Capability.READ,p.path)&&policy.panel(viewer,WorkspacePolicy121.Panel.CODE,p.path))
                rows.add(actor+"|"+p.path+"|"+p.label+"|"+p.color+"|"+WorkspaceCodec122.id(p.anchor)+"|"+p.dx+"|"+p.dy+"|148");
        });return String.join("\n",rows);
    }
}
