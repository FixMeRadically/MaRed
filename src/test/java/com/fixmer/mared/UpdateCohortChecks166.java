package com.fixmer.mared;
import com.fixmer.genesis.technology.server.remote.*;
import com.fixmer.genesis.technology.server.remote.RemoteConsent72.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
/** Actual consent state, signed receipts and durable room; player connections are modeled. */
public final class UpdateCohortChecks166 {
 static int checks;
 static void check(boolean b){if(!b)throw new AssertionError("cohort check "+checks);checks++;}
 static Choice answer(Frame f,boolean yes){return new Choice(f.round(),f.release(),f.sha(),f.ticket(),UUID.randomUUID(),yes);}
 static void run(Path root,int count)throws Exception{
  KeyPair kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Path keys=root.resolve("keys");RemoteControlChecks73.keys(keys,kp);
  var offer=RemoteControlChecks73.offer(kp);List<Session> sessions=new ArrayList<>();for(int i=0;i<count;i++)sessions.add(new Session(UUID.randomUUID(),UUID.randomUUID()));
  var consent=new RemoteConsent72(UUID.randomUUID(),offer,sessions,0);
  var room=ControlRoom73.create(root.resolve("control"),keys,offer,UUID.randomUUID(),consent.round(),sessions.stream().map(Session::player).toList());
  // Every participant explicitly agrees; neither consent nor one receipt commits the group.
  for(Session s:sessions){Frame f=consent.frame(s,100);Choice c=answer(f,true);check(consent.choose(s,c,100));check(consent.choose(s,c,101));room.decision(s.player(),consent.frame(s,101).decision());}
  RemoteControlChecks73.refuse(room::commit);check(room.installed()==0);
  List<ControlRoom73.Member> members=new ArrayList<>(room.members());Collections.shuffle(members,new Random(count));
  for(int i=0;i<count;i++){
   var m=members.get(i);RemoteControlChecks73.receipt(room,m,"PREPARED");
   if(i<count-1)RemoteControlChecks73.refuse(room::commit);
  }
  room.commit();check(room.denominator()==count&&room.installed()==0);
  // Receipt signed by player A cannot impersonate player B.
  if(count>1){var a=members.get(0);var b=members.get(1);byte[] original=RemoteControlChecks73.packet(room,a,"INSTALLED",RemoteControlChecks73.INVENTORY,UUID.randomUUID());
   var forged=new HashMap<>(com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.verify(a.secret,original));forged.put("client",b.id.toString());
   var currentRoom=room;RemoteControlChecks73.refuse(()->currentRoom.receipt(com.fixmer.genesis.technology.server.firstlaunch.LocalApply67.sign(a.secret,forged)));check(room.installed()==0);
  }
  for(int i=0;i<count;i++){
   var m=members.get(i);byte[] receipt=RemoteControlChecks73.packet(room,m,"INSTALLED",RemoteControlChecks73.INVENTORY,UUID.randomUUID());room.receipt(receipt);room.receipt(receipt);
   check(room.installed()==i+1);room=ControlRoom73.restore(root.resolve("control"),keys);check(room.installed()==i+1);
  }
  check(room.installed()==room.denominator());room.admit();for(var m:room.members())RemoteControlChecks73.receipt(room,m,"JOINED");
  check(room.members().stream().allMatch(m->m.state.equals("JOINED")));room.closeRound();
 }
 static void mixed(Path root)throws Exception{
  var kp=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Path keys=root.resolve("keys");RemoteControlChecks73.keys(keys,kp);var offer=RemoteControlChecks73.offer(kp);
  List<Session> ss=new ArrayList<>();for(int i=0;i<5;i++)ss.add(new Session(UUID.randomUUID(),UUID.randomUUID()));
  var c=new RemoteConsent72(UUID.randomUUID(),offer,ss,0);var r=ControlRoom73.create(root.resolve("control"),keys,offer,UUID.randomUUID(),c.round(),ss.stream().map(Session::player).toList());
  for(int i=0;i<4;i++)check(c.choose(ss.get(i),answer(c.frame(ss.get(i),1),i!=2),1));c.tick(RemoteConsent72.WINDOW);
  c.decisions().forEach(r::decision);RemoteControlChecks73.receipt(r,r.member(ss.get(0).player(),false),"PREPARED");
  RemoteControlChecks73.refuse(r::commit); // accepted but unfinished clients still block
  RemoteControlChecks73.receipt(r,r.member(ss.get(1).player(),false),"ABORT");RemoteControlChecks73.receipt(r,r.member(ss.get(3).player(),false),"PREPARED");
  r.commit();check(r.denominator()==2);check(!r.member(ss.get(1).player(),false).counted);check(c.stats(RemoteConsent72.WINDOW).declined()==1&&c.stats(RemoteConsent72.WINDOW).expired()==1);
  // Retrying a stale connection cannot authorize a new connection.
  var old=ss.get(0);Frame previous=c.frame(old,1);c.detach(old);var fresh=new Session(old.player(),UUID.randomUUID());c.bind(fresh,50000);
  check(!c.choose(fresh,answer(previous,true),50000));check(c.frame(fresh,50000).decision().equals("ACCEPTED"));
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("cohort166-");try{for(int n:new int[]{1,2,8,64})run(Files.createDirectories(root.resolve("n"+n)),n);mixed(Files.createDirectories(root.resolve("mixed")));System.out.println("Cohort166: "+checks+" assertions + rejection checks; 1/2/8/64 members, retries, receipt isolation, signed restore, decline/expiry/abort: PASS. Minecraft connections modeled.");}
  finally{try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
}
