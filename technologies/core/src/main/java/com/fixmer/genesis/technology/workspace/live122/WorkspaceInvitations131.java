package com.fixmer.genesis.technology.workspace.live122;

import com.fixmer.genesis.technology.server.firstlaunch.FirstLaunchFiles;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Durable, bounded invitations. Possession of an ID never substitutes for its target's identity. */
public final class WorkspaceInvitations131 {
    public enum Kind { INVITE, REQUEST, JOINED }
    public record Offer(UUID id,UUID project,UUID sender,UUID target,String senderName,String title,Kind kind,Member rights,long expires){}
    private final Path path;
    private final LongSupplier clock;
    private final LinkedHashMap<UUID,Offer> offers=new LinkedHashMap<>();
    public WorkspaceInvitations131(Path root)throws IOException{this(root,System::currentTimeMillis);}
    public WorkspaceInvitations131(Path root,LongSupplier clock)throws IOException{
        this.clock=clock;path=FirstLaunchFiles.safe(root.resolve("invitations131"));
        if(Files.exists(path)){
            var state=WorkspaceCodec122.decode(FirstLaunchFiles.read(path,1024*1024),1024*1024);
            for(String row:state.getOrDefault("offers","").lines().toList()){
                if(row.isEmpty())continue;var d=WorkspaceCodec122.decode(Base64.getDecoder().decode(row),8192);
                var offer=new Offer(UUID.fromString(d.get("id")),UUID.fromString(d.get("project")),UUID.fromString(d.get("sender")),UUID.fromString(d.get("target")),d.get("name"),d.get("title"),Kind.valueOf(d.get("kind")),storedRights135(d),Long.parseLong(d.get("expires")));
                if(offers.put(offer.id(),offer)!=null||offers.size()>128)throw new IOException("FORMAT");
            }
        }
        prune();
    }
    public static Member rights(Map<String,String> d){
        if(d.containsKey("policy135"))return storedRights135(d);Role role=Role.valueOf(d.get("role"));if(role==Role.OWNER)throw new IllegalArgumentException("ROLE");
        var denied=enumSet(d.getOrDefault("deny",""),Capability.class);var hidden=enumSet(d.getOrDefault("hidden",""),Panel.class);String prefix=d.getOrDefault("prefix","");
        return new Member(role,prefix.isEmpty()?denied:Set.of(),prefix.isEmpty()?hidden:Set.of(),prefix.isEmpty()?List.of():List.of(new Rule(prefix,denied,hidden)));
    }
    private static <E extends Enum<E>> Set<E> enumSet(String text,Class<E> type){var set=EnumSet.noneOf(type);if(!text.isEmpty())for(String value:text.split(","))set.add(Enum.valueOf(type,value));return set;}
    public static Map<String,String> fields(Member rights){
        var d=new TreeMap<String,String>();d.put("role",rights.role().name());d.put("deny",names(rights.denied()));d.put("hidden",names(rights.hidden()));d.put("prefix","");
        if(rights.rules().size()>1||!rights.rules().isEmpty()&&(!rights.denied().isEmpty()||!rights.hidden().isEmpty())){d.put("complex135","true");d.put("policy135",encodeRights135(rights));}if(!rights.rules().isEmpty()){var r=rights.rules().get(0);d.put("prefix",r.prefix());d.put("deny",names(r.denied()));d.put("hidden",names(r.hidden()));}return d;
    }
    private static String encodeRights135(Member rights){var text=new StringBuilder(rights.role().name()+"|"+names(rights.denied())+"|"+names(rights.hidden()));for(var rule:rights.rules())text.append('\n').append(rule.prefix()).append('|').append(names(rule.denied())).append('|').append(names(rule.hidden()));return text.toString();}
    private static Member storedRights135(Map<String,String> fields){
        if(!fields.containsKey("policy135"))return rights(fields);
        var rows=fields.get("policy135").split("\n",-1);var header=rows[0].split("\\|",-1);if(header.length!=3||rows.length>129)throw new IllegalArgumentException("RIGHTS");
        Role role=Role.valueOf(header[0]);if(role==Role.OWNER)throw new IllegalArgumentException("ROLE");var rules=new ArrayList<Rule>();
        for(int i=1;i<rows.length;i++){var rule=rows[i].split("\\|",-1);if(rule.length!=3)throw new IllegalArgumentException("RIGHTS");rules.add(new Rule(rule[0],enumSet(rule[1],Capability.class),enumSet(rule[2],Panel.class)));}
        return new Member(role,enumSet(header[1],Capability.class),enumSet(header[2],Panel.class),rules);
    }
    private static String names(Set<? extends Enum<?>> values){return String.join(",",values.stream().map(Enum::name).sorted().toList());}
    private void prune(){long now=clock.getAsLong();offers.values().removeIf(o->o.expires()<=now);}
    public synchronized Offer issue(UUID project,UUID sender,UUID target,String name,String title,Kind kind,Member rights)throws IOException{
        prune();if(rights.role()==Role.OWNER)throw new IllegalArgumentException("ROLE");if(sender.equals(target)||name.indexOf('|')>=0||name.indexOf('\n')>=0||title.indexOf('|')>=0||title.indexOf('\n')>=0)throw new IllegalArgumentException("TARGET");
        var next=new LinkedHashMap<>(offers);
        var prior=next.values().stream().filter(o->o.project().equals(project)&&o.sender().equals(sender)&&o.target().equals(target)&&o.kind()==kind).findFirst().orElse(null);
        if(prior!=null&&prior.rights().equals(rights))return prior;
        if(prior!=null)next.remove(prior.id());
        if(kind==Kind.JOINED&&next.values().stream().filter(o->o.target().equals(target)&&o.kind()==Kind.JOINED).count()>=32){UUID oldest=next.values().stream().filter(o->o.target().equals(target)&&o.kind()==Kind.JOINED).findFirst().orElseThrow().id();next.remove(oldest);}if(next.size()>=128||kind!=Kind.JOINED&&next.values().stream().filter(o->o.target().equals(target)&&o.kind()!=Kind.JOINED).count()>=32)throw new IOException("LIMIT");
        var offer=new Offer(UUID.randomUUID(),project,sender,target,name,title,kind,rights,Math.addExact(clock.getAsLong(),kind==Kind.JOINED?86_400_000:900_000));next.put(offer.id(),offer);save(next);return offer;
    }
    public synchronized Offer get(UUID actor,UUID id){prune();Offer offer=offers.get(id);if(offer==null||!offer.target().equals(actor))throw new SecurityException("DENIED");return offer;}
    public synchronized List<Offer> inbox(UUID actor){prune();return offers.values().stream().filter(o->o.target().equals(actor)).toList();}
    public synchronized List<Offer> project(UUID project){prune();return offers.values().stream().filter(o->o.project().equals(project)).toList();}
    public synchronized void consume(UUID actor,UUID id)throws IOException{get(actor,id);var next=new LinkedHashMap<>(offers);next.remove(id);save(next);}
    public synchronized void cancel(UUID project,UUID target)throws IOException{var next=new LinkedHashMap<>(offers);next.values().removeIf(o->o.project().equals(project)&&o.target().equals(target)&&o.kind()==Kind.INVITE);save(next);}
    public synchronized void removeProject(UUID project)throws IOException{var next=new LinkedHashMap<>(offers);next.values().removeIf(o->o.project().equals(project));save(next);}
    private void save(LinkedHashMap<UUID,Offer> next)throws IOException{
        var rows=new ArrayList<String>();for(var o:next.values()){
            var d=new TreeMap<>(fields(o.rights()));d.put("policy135",encodeRights135(o.rights()));d.putAll(Map.of("id",o.id().toString(),"project",o.project().toString(),"sender",o.sender().toString(),"target",o.target().toString(),"name",o.senderName(),"title",o.title(),"kind",o.kind().name(),"expires",Long.toString(o.expires())));
            rows.add(Base64.getEncoder().encodeToString(WorkspaceCodec122.encode(d,8192)));
        }
        Files.createDirectories(path.getParent());FirstLaunchFiles.write(path,WorkspaceCodec122.encode(Map.of("offers",String.join("\n",rows)),1024*1024),true);offers.clear();offers.putAll(next);
    }
}
