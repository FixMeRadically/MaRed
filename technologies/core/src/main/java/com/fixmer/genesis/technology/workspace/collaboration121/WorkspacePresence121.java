package com.fixmer.genesis.technology.workspace.collaboration121;
import java.util.*;import java.util.function.LongSupplier;
/** Ephemeral presence: authenticated actor supplied by adapter; no persistence of stale cursors. */
public final class WorkspacePresence121 {
 public record Cursor(UUID actor,String file,String label,int rgb,CollaborativeDocument121.Id anchor,CollaborativeDocument121.Id selection,long sequence,long expires,boolean editing){
  public Cursor(UUID actor,String file,String label,int rgb,CollaborativeDocument121.Id anchor,CollaborativeDocument121.Id selection,long sequence,long expires){this(actor,file,label,rgb,anchor,selection,sequence,expires,false);}
 }
 private final Map<UUID,Cursor> cursors=new HashMap<>();private final Map<UUID,Long> sequences=new HashMap<>();private final LongSupplier clock;private final long ttl;
 public WorkspacePresence121(LongSupplier monotonicMillis,long ttlMillis){clock=Objects.requireNonNull(monotonicMillis);if(ttlMillis<1000||ttlMillis>60000)throw new IllegalArgumentException("TTL");ttl=ttlMillis;}
 public synchronized void update(WorkspacePolicy121 policy,UUID actor,String file,String label,int rgb,CollaborativeDocument121.Id anchor,CollaborativeDocument121.Id selection,long sequence){
  update(policy,actor,file,label,rgb,anchor,selection,sequence,false);
 }
 public synchronized void update(WorkspacePolicy121 policy,UUID actor,String file,String label,int rgb,CollaborativeDocument121.Id anchor,CollaborativeDocument121.Id selection,long sequence,boolean editing){
  policy.require(actor,WorkspacePolicy121.Capability.READ,file);Objects.requireNonNull(anchor);Objects.requireNonNull(selection);
  if(sequence<=sequences.getOrDefault(actor,0L)||rgb<0||rgb>0xffffff||label==null||label.isBlank()||label.length()>48||label.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("PRESENCE");
  if(!sequences.containsKey(actor)&&sequences.size()>=64)throw new IllegalArgumentException("LIMIT");long now=clock.getAsLong();sequences.put(actor,sequence);cursors.put(actor,new Cursor(actor,WorkspacePolicy121.path(file),label,rgb,anchor,selection,sequence,Math.addExact(now,ttl),editing));
 }
 public synchronized List<Cursor> visible(WorkspacePolicy121 policy,UUID viewer){long now=clock.getAsLong();cursors.entrySet().removeIf(e->e.getValue().expires<=now||!policy.allows(e.getKey(),WorkspacePolicy121.Capability.READ,e.getValue().file)||!policy.panel(e.getKey(),WorkspacePolicy121.Panel.CODE,e.getValue().file));return cursors.values().stream().filter(c->policy.allows(viewer,WorkspacePolicy121.Capability.READ,c.file)&&policy.panel(viewer,WorkspacePolicy121.Panel.CODE,c.file)).sorted(Comparator.comparing(c->c.actor.toString())).toList();}
 public synchronized void disconnect(UUID actor){cursors.remove(actor);/* sequence retained until session ends to reject replay */}
 /** Adapter calls only after replacing the authenticated session, never for a packet retry. */
 public synchronized void newSession(UUID actor){cursors.remove(actor);sequences.remove(actor);}
}
