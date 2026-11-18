package com.fixmer.genesis.technology.server.remote;

import java.util.*;

/** Server-thread consent state only. ACCEPTED is never installed/ready/permission to restart. */
public final class RemoteConsent72 {
    public static final int LIMIT=64;public static final long WINDOW=30000;
    public enum Decision { PENDING, ACCEPTED, DECLINED, EXPIRED }
    public record Session(UUID player,UUID connection){public Session{Objects.requireNonNull(player);nonzero(connection);}}
    public record Choice(UUID round,UUID release,String sha,UUID ticket,UUID request,boolean accept){public Choice{nonzero(round);Objects.requireNonNull(release);hash(sha);nonzero(ticket);nonzero(request);}}
    public record Frame(UUID instance,UUID round,UUID ticket,UUID release,UUID distribution,String sha,String game,String endpoint,String signer,long revision,long serial,long remaining,String phase,String decision){
        public Frame{nonzero(instance);nonzero(round);nonzero(ticket);Objects.requireNonNull(release);Objects.requireNonNull(distribution);hash(sha);hash(signer);if(!RemoteOffer69.gameAddress(game).equals(game)||!RemoteOffer69.base(endpoint).equals(endpoint)||revision<1||serial<1||remaining<0||remaining>WINDOW||!Set.of("ACTIVE","CANCELLED").contains(phase))throw new IllegalArgumentException("REMOTE72_FRAME");Decision.valueOf(decision);}
    }
    public record Stats(int cohort,int accepted,int declined,int expired,int pending,int late,boolean cancelled){}
    private static final class Entry {Session session;UUID ticket,request;Boolean answer;long deadline;boolean connected;Decision decision=Decision.PENDING;Entry(Session session,long now){bind(session,now);}void bind(Session next,long now){session=next;ticket=UUID.randomUUID();request=null;answer=null;connected=true;if(decision!=Decision.ACCEPTED)decision=Decision.PENDING;deadline=Math.addExact(now,WINDOW);}}
    private final UUID instance,round=UUID.randomUUID();private final RemoteOffer69 offer;private final Set<UUID> cohort;private final Map<UUID,Entry> entries=new HashMap<>();private long serial;private boolean cancelled;
    public RemoteConsent72(UUID instance,RemoteOffer69 verifiedOffer,Collection<Session> initial,long now){
        nonzero(instance);this.instance=instance;offer=Objects.requireNonNull(verifiedOffer);if(initial.size()>LIMIT)throw new IllegalArgumentException("REMOTE72_LIMIT");var ids=new HashSet<UUID>();for(Session s:initial)if(!ids.add(s.player()))throw new IllegalArgumentException("REMOTE72_DUPLICATE");cohort=Set.copyOf(ids);for(Session s:initial)entries.put(s.player(),new Entry(s,now));
    }
    public static RemoteOffer69 verifyPublished(java.nio.file.Path folder,String minecraft,String neoforge)throws Exception{var o=RemoteUpdateCli69.verifyPublic(folder);if(!o.minecraft().equals(minecraft)||!o.neoforge().equals(neoforge))throw new java.io.IOException("REMOTE72_VERSION");requireWindow(o,System.currentTimeMillis());return o;}
    public static void requireWindow(RemoteOffer69 o,long wall)throws java.io.IOException{o.validAt(wall);if(o.expires()-wall<WINDOW)throw new IllegalArgumentException("REMOTE72_EXPIRING");}
    public RemoteOffer69 offer(){return offer;}public UUID round(){return round;}public boolean cancelled(){return cancelled;}
    public void bind(Session s,long now){if(cancelled)throw new IllegalStateException("REMOTE72_CANCELLED");Entry e=entries.get(s.player());if(e==null){if(entries.size()>=LIMIT)throw new IllegalStateException("REMOTE72_LIMIT");entries.put(s.player(),new Entry(s,now));}else if(!e.connected||!e.session.equals(s))e.bind(s,now);}
    public void detach(Session s){Entry e=entries.get(s.player());if(e!=null&&e.session.equals(s))e.connected=false;}
    private Entry current(Session s){Entry e=entries.get(s.player());if(e==null||!e.connected||!e.session.equals(s))throw new IllegalStateException("REMOTE72_CONNECTION");return e;}
    public void tick(long now){if(cancelled)return;for(Entry e:entries.values())expire(e,now);}
    private void expire(Entry e,long now){if(e.decision==Decision.PENDING&&now>=e.deadline)e.decision=Decision.EXPIRED;}
    public boolean choose(Session s,Choice choice,long now){
        if(cancelled)return false;Entry e;try{e=current(s);}catch(IllegalStateException stale){return false;}
        if(!round.equals(choice.round())||!offer.release().equals(choice.release())||!offer.archiveSha().equals(choice.sha())||!e.ticket.equals(choice.ticket()))return false;
        expire(e,now);if(e.request!=null)return e.request.equals(choice.request())&&Objects.equals(e.answer,choice.accept());if(e.decision!=Decision.PENDING)return false;
        e.request=choice.request();e.answer=choice.accept();e.decision=choice.accept()?Decision.ACCEPTED:Decision.DECLINED;return true;
    }
    public boolean disconnect(Session s,long now){if(cancelled)return false;Entry e=current(s);expire(e,now);return e.decision==Decision.DECLINED||e.decision==Decision.EXPIRED;}
    public Frame frame(Session s,long now){Entry e=current(s);if(!cancelled)expire(e,now);return new Frame(instance,round,e.ticket,offer.release(),offer.distribution(),offer.archiveSha(),offer.game(),offer.endpoint(),offer.signer(),offer.sequence(),++serial,cancelled||e.decision!=Decision.PENDING?0:Math.max(0,e.deadline-now),cancelled?"CANCELLED":"ACTIVE",e.decision.name());}
    public void cancel(){cancelled=true;}
    public Stats stats(long now){tick(now);int a=0,d=0,e=0,p=0;for(UUID id:cohort)switch(entries.get(id).decision){case ACCEPTED->a++;case DECLINED->d++;case EXPIRED->e++;case PENDING->p++;}return new Stats(cohort.size(),a,d,e,p,entries.size()-cohort.size(),cancelled);}
    private static void nonzero(UUID v){if(v==null||v.equals(new UUID(0,0)))throw new IllegalArgumentException("REMOTE72_UUID");}
    private static void hash(String v){if(v==null||!v.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("REMOTE72_HASH");}
}
