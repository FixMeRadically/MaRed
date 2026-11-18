package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.*;
import java.util.*;
import java.nio.file.*;

/** Real dependency-free state/connection/deadline tests. Sessions are explicit models, not ServerPlayer. */
public final class RemoteConsentChecks72 {
    private static int checks;private static void check(boolean ok){if(!ok)throw new AssertionError("Check "+checks);checks++;}
    private interface Work {void run()throws Exception;}
    private static void refuse(Work w)throws Exception{try{w.run();throw new AssertionError("Accepted invalid input");}catch(IllegalArgumentException|IllegalStateException|java.io.IOException expected){checks++;}}
    private static RemoteOffer69 offer(){long n=System.currentTimeMillis();return new RemoteOffer69("localhost:25565","https://localhost/genesis/",UUID.randomUUID(),UUID.randomUUID(),null,"1.21.1","21.1.264",1,n,n+3600000,100,"a".repeat(64),"b".repeat(64));}
    private static Session session(){return new Session(UUID.randomUUID(),UUID.randomUUID());}
    private static RemoteConsent72 round(Session...s){return new RemoteConsent72(UUID.randomUUID(),offer(),List.of(s),0);}
    private static Choice choice(Frame f,UUID request,boolean yes){return new Choice(f.round(),f.release(),f.sha(),f.ticket(),request,yes);}
    private static void unit()throws Exception{
        Session a=session(),b=session();var r=round(a,b);Frame f=r.frame(a,0);check(f.remaining()==30000&&f.decision().equals("PENDING"));check(r.stats(0).cohort()==2&&r.stats(0).pending()==2);
        check(!r.disconnect(a,29999));check(r.disconnect(a,30000));check(r.frame(a,30000).decision().equals("EXPIRED"));check(!r.choose(a,choice(f,UUID.randomUUID(),true),30000));check(r.stats(30000).expired()==2);
        r=round(a,b);f=r.frame(a,0);UUID request=UUID.randomUUID();check(r.choose(a,choice(f,request,true),100));check(r.choose(a,choice(f,request,true),500));check(!r.choose(a,choice(f,request,false),500));check(!r.choose(a,choice(f,UUID.randomUUID(),false),500));check(!r.disconnect(a,999999));check(r.frame(a,999999).decision().equals("ACCEPTED"));check(r.stats(999999).accepted()==1&&r.stats(999999).expired()==1);
        var no=round(a,b);Frame nf=no.frame(a,0);check(no.choose(a,choice(nf,UUID.randomUUID(),false),1));check(no.disconnect(a,1)&&!no.disconnect(b,1));no.cancel();check(!no.disconnect(a,999999)&&!no.disconnect(b,999999));check(no.frame(b,999999).phase().equals("CANCELLED"));check(no.stats(999999).pending()==1);check(!no.choose(b,choice(no.frame(b,1),UUID.randomUUID(),true),1));
        var reconnect=round(a);Frame old=reconnect.frame(a,0);reconnect.detach(a);refuse(()->reconnect.frame(a,1));Session again=new Session(a.player(),UUID.randomUUID());reconnect.bind(again,1000);Frame fresh=reconnect.frame(again,1000);check(!fresh.ticket().equals(old.ticket())&&fresh.remaining()==30000);check(!reconnect.choose(again,choice(old,UUID.randomUUID(),true),1000));check(!reconnect.choose(a,choice(fresh,UUID.randomUUID(),true),1000));reconnect.bind(again,2000);check(reconnect.frame(again,2000).remaining()==29000);check(reconnect.choose(again,choice(fresh,UUID.randomUUID(),true),2000));reconnect.detach(again);Session third=new Session(a.player(),UUID.randomUUID());reconnect.bind(third,40000);check(reconnect.frame(third,40000).decision().equals("ACCEPTED"));check(reconnect.stats(40000).cohort()==1&&reconnect.stats(40000).accepted()==1);
        var expired=round(a);expired.tick(30000);expired.detach(a);expired.bind(again,50000);Frame retry=expired.frame(again,50000);check(retry.decision().equals("PENDING")&&retry.remaining()==30000);check(expired.choose(again,choice(retry,UUID.randomUUID(),true),50001));
        var foreign=round(a);Frame ff=foreign.frame(a,0);check(!foreign.choose(a,new Choice(UUID.randomUUID(),ff.release(),ff.sha(),ff.ticket(),UUID.randomUUID(),true),0));check(!foreign.choose(a,new Choice(ff.round(),UUID.randomUUID(),ff.sha(),ff.ticket(),UUID.randomUUID(),true),0));check(!foreign.choose(a,new Choice(ff.round(),ff.release(),"c".repeat(64),ff.ticket(),UUID.randomUUID(),true),0));check(!foreign.choose(a,new Choice(ff.round(),ff.release(),ff.sha(),UUID.randomUUID(),UUID.randomUUID(),true),0));check(foreign.stats(0).pending()==1);
        var late=round(a);late.bind(b,1000);Frame bf=late.frame(b,1000);late.choose(b,choice(bf,UUID.randomUUID(),true),1001);check(late.stats(1001).cohort()==1&&late.stats(1001).late()==1&&late.stats(1001).accepted()==0);late.detach(b);late.bind(new Session(b.player(),UUID.randomUUID()),2000);check(late.stats(2000).late()==1);late.cancel();refuse(()->late.bind(session(),1));
        List<Session> crowd=new ArrayList<>();for(int i=0;i<64;i++)crowd.add(session());var full=new RemoteConsent72(UUID.randomUUID(),offer(),crowd,0);refuse(()->full.bind(session(),1));crowd.add(session());refuse(()->new RemoteConsent72(UUID.randomUUID(),offer(),crowd,0));refuse(()->round(a,a));refuse(()->new Choice(ff.round(),ff.release(),ff.sha(),ff.ticket(),new UUID(0,0),true));refuse(()->new Choice(ff.round(),ff.release(),"not a hash",ff.ticket(),UUID.randomUUID(),true));
        var empty=round();check(empty.stats(0).cohort()==0);empty.bind(a,1);check(empty.stats(1).cohort()==0&&empty.stats(1).late()==1);
        var window=offer();RemoteConsent72.requireWindow(window,window.expires()-30000);checks++;refuse(()->RemoteConsent72.requireWindow(window,window.expires()-29999));
        var serial=round(a);check(serial.frame(a,10).serial()<serial.frame(a,20).serial());
        System.out.println("Remote72 consent core checks passed: "+checks+"; session identities and clock were explicit inputs, not Minecraft runtime.");
    }
    public static void main(String[] args)throws Exception{
        if(args.length==0){unit();return;}Path folder=Path.of(args[0]);var o=RemoteConsent72.verifyPublished(folder,"1.21.1","21.1.264");check(o.sequence()==2);refuse(()->RemoteConsent72.verifyPublished(folder,"1.21.1","21.1.999"));refuse(()->RemoteConsent72.verifyPublished(folder,"1.21.2","21.1.264"));System.out.println("Remote72 actual signed publication checks passed: "+checks);
    }
}
