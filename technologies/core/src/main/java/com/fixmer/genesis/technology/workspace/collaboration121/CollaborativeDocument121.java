package com.fixmer.genesis.technology.workspace.collaboration121;

import java.util.*;

/** Server-authoritative RGA. Positions are Unicode code points, never UTF-16 offsets.
 * Missing dependencies must be retried/resynchronized by the transport adapter. */
public final class CollaborativeDocument121 {
    public static final int MAX_NODES=200_000, MAX_BATCH=4096;
    public record Id(UUID actor,long clock) implements Comparable<Id> {
        public Id { Objects.requireNonNull(actor);if(clock<0)throw new IllegalArgumentException("CLOCK"); }
        @Override public int compareTo(Id b){int c=Long.compare(clock,b.clock);return c!=0?c:actor.toString().compareTo(b.actor.toString());}
    }
    public static final Id ROOT=new Id(new UUID(0,0),0);
    public record Insert(Id id,Id after,int codepoint) {
        public Insert { Objects.requireNonNull(id);Objects.requireNonNull(after);if(id.equals(ROOT)||id.actor().equals(ROOT.actor())||id.clock()<=after.clock()||!Character.isValidCodePoint(codepoint)||codepoint==0||codepoint>=0xD800&&codepoint<=0xDFFF)throw new IllegalArgumentException("INSERT"); }
    }
    public record Batch(UUID actor,List<Insert> inserts,List<Id> deletes) {
        public Batch { Objects.requireNonNull(actor);inserts=List.copyOf(inserts);deletes=List.copyOf(deletes);if(actor.equals(ROOT.actor())||inserts.size()+deletes.size()>MAX_BATCH||inserts.isEmpty()&&deletes.isEmpty())throw new IllegalArgumentException("BATCH");for(var i:inserts)if(!i.id.actor.equals(actor))throw new IllegalArgumentException("ACTOR"); }
    }
    public record Snapshot(List<Insert> nodes,Set<Id> deleted,long revision) {
        public Snapshot { nodes=List.copyOf(nodes);deleted=Set.copyOf(deleted);if(revision<0)throw new IllegalArgumentException("REVISION"); }
    }
    private final Map<Id,Insert> nodes=new HashMap<>();
    private final Set<Id> deleted=new HashSet<>();
    private long revision,clock;
    private List<Id> visibleCache148;
    private Map<Id,Integer> positionCache148;
    private String textCache148;
    private Snapshot snapshotCache148;
    private void invalidate148(){visibleCache148=null;positionCache148=null;textCache148=null;snapshotCache148=null;}
    private void index148(){
        if(visibleCache148!=null)return;
        var visible=new ArrayList<Id>();var positions=new HashMap<Id,Integer>();var text=new StringBuilder();int offset=0;
        for(var id:ordered(false)){if(!deleted.contains(id)){visible.add(id);text.appendCodePoint(nodes.get(id).codepoint);offset++;}positions.put(id,offset);}
        visibleCache148=List.copyOf(visible);positionCache148=positions;textCache148=text.toString();
    }
    public CollaborativeDocument121(){}
    public CollaborativeDocument121(Snapshot s){
        if(s.nodes.size()>MAX_NODES)throw new IllegalArgumentException("LIMIT");
        for(var i:s.nodes){if(nodes.putIfAbsent(i.id,i)!=null)throw new IllegalArgumentException("DUPLICATE");clock=Math.max(clock,i.id.clock);}
        for(var i:nodes.values())if(!i.after.equals(ROOT)&&!nodes.containsKey(i.after))throw new IllegalArgumentException("DEPENDENCY");
        if(!nodes.keySet().containsAll(s.deleted))throw new IllegalArgumentException("DELETE");deleted.addAll(s.deleted);revision=s.revision;
    }
    /** A durable ACK must contain the exact inserts and deletion tombstones of the acknowledged batch. */
    public synchronized boolean containsBatch150(Batch batch){for(var insert:batch.inserts)if(!insert.equals(nodes.get(insert.id)))return false;return deleted.containsAll(batch.deletes);}
    public synchronized boolean apply(Batch batch){
        var next=new HashMap<>(nodes);boolean changed=false;
        for(var i:batch.inserts){
            var prior=next.get(i.id);
            if(prior!=null){if(!prior.equals(i))throw new IllegalArgumentException("ID_REUSED");continue;}
            if(!i.after.equals(ROOT)&&!next.containsKey(i.after))throw new IllegalArgumentException("DEPENDENCY");
            next.put(i.id,i);changed=true;
        }
        if(next.size()>MAX_NODES)throw new IllegalArgumentException("LIMIT");
        var tombstones=new HashSet<>(deleted);
        for(var id:batch.deletes){if(!next.containsKey(id))throw new IllegalArgumentException("DEPENDENCY");changed|=tombstones.add(id);}
        if(changed){if(revision==Long.MAX_VALUE)throw new IllegalArgumentException("LIMIT");nodes.clear();nodes.putAll(next);deleted.clear();deleted.addAll(tombstones);revision++;invalidate148();for(var i:batch.inserts)clock=Math.max(clock,i.id.clock);}
        return changed;
    }
    private List<Id> ordered(boolean visible){
        var children=new HashMap<Id,List<Id>>();for(var i:nodes.values())children.computeIfAbsent(i.after,k->new ArrayList<>()).add(i.id);
        children.values().forEach(x->x.sort(Comparator.reverseOrder()));var stack=new ArrayDeque<Id>();var result=new ArrayList<Id>();
        push(stack,children.get(ROOT));while(!stack.isEmpty()){var id=stack.pop();if(!visible||!deleted.contains(id))result.add(id);push(stack,children.get(id));}return result;
    }
    private static void push(ArrayDeque<Id> stack,List<Id> ids){if(ids!=null)for(int i=ids.size()-1;i>=0;i--)stack.push(ids.get(i));}
    public synchronized String text(){index148();return textCache148;}
    public synchronized List<Id> visibleIds(){index148();return visibleCache148;}
    public synchronized Batch replace(UUID actor,int start,int end,String text){
        Objects.requireNonNull(text);var ids=visibleIds();if(start<0||end<start||end>ids.size())throw new IllegalArgumentException("RANGE");
        int[] points=text.codePoints().toArray();if(points.length+end-start>MAX_BATCH||clock>Long.MAX_VALUE-points.length)throw new IllegalArgumentException("LIMIT");
        var inserts=new ArrayList<Insert>();Id after=start==0?ROOT:ids.get(start-1);long time=clock;
        for(int cp:points){var id=new Id(actor,++time);inserts.add(new Insert(id,after,cp));after=id;}
        return new Batch(actor,inserts,ids.subList(start,end));
    }
    public synchronized Snapshot snapshot(){if(snapshotCache148==null)snapshotCache148=new Snapshot(nodes.values().stream().sorted(Comparator.comparing(Insert::id)).toList(),deleted,revision);return snapshotCache148;}
    /** Stable left anchor survives insertion and deletion. */
    public synchronized int position(Id anchor){if(anchor.equals(ROOT))return 0;index148();Integer p=positionCache148.get(anchor);if(p==null)throw new IllegalArgumentException("ANCHOR");return p;}
    public record Attribution(UUID actor,int start,int end){}
    public synchronized List<Attribution> attribution(){var result=new ArrayList<Attribution>();int index=0;for(var id:visibleIds()){if(!result.isEmpty()&&result.get(result.size()-1).actor.equals(id.actor)){var last=result.remove(result.size()-1);result.add(new Attribution(id.actor,last.start,index+1));}else result.add(new Attribution(id.actor,index,index+1));index++;}return List.copyOf(result);}
    public synchronized long revision(){return revision;}
}
