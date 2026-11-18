package com.fixmer.genesis.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.collaboration121.*;import java.io.*;import java.util.*;
/** Local burst with deterministic retry IDs; foreign operations survive acknowledgement. */
public final class WorkspaceReplica122 {
 public record Pending(UUID request,String batch,String lease){}
 private static final class Grant{final String token;long next,last;Grant(String token,long next,long last){this.token=token;this.next=next;this.last=last;}}
 private Set<CollaborativeDocument121.Id> confirmedIds150;
 private long version148;public long version148(){return version148;}
 private final UUID actor;private CollaborativeDocument121.Snapshot confirmed;private CollaborativeDocument121 optimistic;private final ArrayDeque<Pending> pending=new ArrayDeque<>();private final ArrayList<Grant> grants=new ArrayList<>();
 public WorkspaceReplica122(UUID actor,CollaborativeDocument121.Snapshot snapshot){this.actor=actor;confirmed=snapshot;optimistic=new CollaborativeDocument121(snapshot);}
 public void grant(String token,long first,long last){if(token.isEmpty()||token.length()>4096||first<1||last<first||last==Long.MAX_VALUE||last-first>4095)throw new IllegalArgumentException("LEASE");for(var grant:grants){if(grant.token.equals(token)){if(grant.last!=last||first>grant.next)throw new IllegalArgumentException("LEASE");return;}if(first<=grant.last&&last>=grant.next)throw new IllegalArgumentException("LEASE_OVERLAP");}grants.add(new Grant(token,first,last));version148++;if(grants.size()>16)grants.remove(0);}
 public long confirmedRevision148(){return confirmed.revision();}
 public int pendingCount130(){return pending.size();}public boolean pending(){return !pending.isEmpty();}public Pending first(){return pending.peekFirst();}public CollaborativeDocument121 document(){return optimistic;}public String text(){return optimistic.text();}
 public CollaborativeDocument121.Id confirmedAnchor130(CollaborativeDocument121.Id id){
  if(id.equals(CollaborativeDocument121.ROOT))return id;
  if(confirmedIds150==null){var ids=new HashSet<CollaborativeDocument121.Id>();for(var node:confirmed.nodes())ids.add(node.id());confirmedIds150=ids;}var known=confirmedIds150;
  if(id.equals(CollaborativeDocument121.ROOT)||known.contains(id))return id;
  var ids=optimistic.visibleIds();int index=ids.indexOf(id);for(int i=index-1;i>=0;i--)if(known.contains(ids.get(i)))return ids.get(i);return CollaborativeDocument121.ROOT;
 }
 public boolean prepare(String target)throws IOException{
  int[] old=text().codePoints().toArray(),next=target.codePoints().toArray();if(next.length>16384)throw new IOException("TEXT_LIMIT");for(int cp:next)if(cp==0||cp>=0xD800&&cp<=0xDFFF)throw new IOException("UNICODE");int start=0;while(start<old.length&&start<next.length&&old[start]==next[start])start++;int end=old.length,endNext=next.length;while(end>start&&endNext>start&&old[end-1]==next[endNext-1]){end--;endNext--;}
  if(start==end&&start==endNext)return true;if(pending.size()+(Math.max(endNext-start,end-start)+255)/256+grants.size()>256)throw new IOException("LOCAL_LIMIT");long max=optimistic.snapshot().nodes().stream().mapToLong(n->n.id().clock()).max().orElse(0),available=0;if(max==Long.MAX_VALUE)throw new IOException("LIMIT");for(var g:grants){g.next=Math.min(g.last+1,Math.max(g.next,max+1));if(g.next<=g.last)available+=g.last-g.next+1;}if(available<endNext-start||grants.isEmpty())return false;
  var ids=optimistic.visibleIds();var deletes=ids.subList(start,end);int inserted=start,removed=0;var after=start==0?CollaborativeDocument121.ROOT:ids.get(start-1);var generated=new ArrayList<Pending>();var local=new CollaborativeDocument121(optimistic.snapshot());
  while(inserted<endNext||removed<deletes.size()){Grant grant=null;for(var g:grants)if(g.next<=g.last){grant=g;break;}if(grant==null)grant=grants.get(grants.size()-1);var add=new ArrayList<CollaborativeDocument121.Insert>();while(inserted<endNext&&add.size()<256&&grant.next<=grant.last){var id=new CollaborativeDocument121.Id(actor,grant.next++);add.add(new CollaborativeDocument121.Insert(id,after,next[inserted++]));after=id;}int count=Math.min(256,deletes.size()-removed);var batch=new CollaborativeDocument121.Batch(actor,add,deletes.subList(removed,removed+count));removed+=count;local.apply(batch);generated.add(new Pending(UUID.randomUUID(),WorkspaceCodec122.batch(batch),grant.token));}
  if(!local.text().equals(target))throw new IOException("LOCAL_ORDER");pending.addAll(generated);optimistic=local;version148++;return true;
 }
 public void acknowledge(UUID request,CollaborativeDocument121.Snapshot snapshot)throws IOException{if(pending.isEmpty()||!pending.peekFirst().request.equals(request))throw new IOException("ACK");var remaining=new ArrayList<>(pending);remaining.remove(0);var accepted=snapshot.revision()<=confirmed.revision()?confirmed:snapshot;var acknowledged=WorkspaceCodec122.batch(actor,pending.peekFirst().batch);var local=new CollaborativeDocument121(accepted);if(!local.containsBatch150(acknowledged))throw new IOException("ACK");for(var p:remaining)local.apply(WorkspaceCodec122.batch(actor,p.batch));pending.removeFirst();if(confirmed!=accepted)confirmedIds150=null;confirmed=accepted;optimistic=local;version148++;}
 public void merge(CollaborativeDocument121.Snapshot snapshot)throws IOException{if(snapshot.revision()<=confirmed.revision())return;var local=new CollaborativeDocument121(snapshot);for(var p:pending)local.apply(WorkspaceCodec122.batch(actor,p.batch));confirmedIds150=null;confirmed=snapshot;optimistic=local;version148++;}
 public Map<String,String> checkpoint(String target)throws IOException{var q=new StringBuilder();for(var p:pending)q.append(p.request).append('|').append(p.batch).append('|').append(p.lease).append('\n');var gs=new StringBuilder();for(var g:grants)gs.append(g.next).append('|').append(g.last).append('|').append(g.token).append('\n');return Map.of("document",WorkspaceCodec122.document(confirmed),"target",target,"queue",q.toString(),"grants",gs.toString());}
 /** Keep a recovered draft bound to its ORIGINAL file identity even when it is saved again. */
 public record Recovery(WorkspaceReplica122 replica,String target,String epoch,boolean stale){}
 public static Recovery recover(UUID actor,Map<String,String> saved,String currentEpoch,CollaborativeDocument121.Snapshot snapshot)throws IOException{
  var checkpoint=new TreeMap<>(saved);String epoch=checkpoint.remove("epoch"),target=checkpoint.get("target");
  if(epoch==null||target==null||!UUID.fromString(epoch).toString().equals(epoch))throw new IOException("CACHE");
  if(!currentEpoch.equals(epoch))return new Recovery(new WorkspaceReplica122(actor,snapshot),target,epoch,true);
  var replica=restore(actor,checkpoint);boolean dirty=!target.equals(replica.text());
  if(!dirty||replica.pending())replica.merge(snapshot);
  return new Recovery(replica,dirty?target:replica.text(),epoch,false);
 }
 public static WorkspaceReplica122 restore(UUID actor,Map<String,String> saved)throws IOException{if(!saved.keySet().equals(Set.of("document","target","queue","grants")))throw new IOException("CACHE");var r=new WorkspaceReplica122(actor,WorkspaceCodec122.document(saved.get("document")));int count=0;for(String row:saved.get("queue").split("\n")){if(row.isEmpty())continue;if(++count>256)throw new IOException("CACHE");String[] p=row.split("\\|",-1);if(p.length!=3)throw new IOException("CACHE");WorkspaceCodec122.batch(actor,p[1]);r.pending.add(new Pending(UUID.fromString(p[0]),p[1],p[2]));}count=0;for(String row:saved.get("grants").split("\n")){if(row.isEmpty())continue;if(++count>16)throw new IOException("CACHE");String[] p=row.split("\\|",-1);if(p.length!=3)throw new IOException("CACHE");long next=Long.parseLong(p[0]),last=Long.parseLong(p[1]);if(next<1||last<0||last==Long.MAX_VALUE||next>last+1)throw new IOException("CACHE");r.grants.add(new Grant(p[2],next,last));}for(var p:r.pending)r.optimistic.apply(WorkspaceCodec122.batch(actor,p.batch));return r;}
}
