package com.fixmer.genesis.technology.workspace.collaboration121;
import java.util.*;
/** Project-scoped capabilities. Minecraft OP is deliberately not an input. */
public final class WorkspacePolicy121 {
 public enum Capability { READ,EDIT,CREATE,DELETE,RESTORE,BUILD,APPROVE,PUBLISH,MANAGE_MEMBERS,VIEW_HISTORY,ERASE_CODE,SELECT_FILES }
 public enum Role { OWNER,MAINTAINER,DEVELOPER,REVIEWER,VIEWER }
 public enum Panel { CODE,ASSETS,BUILD,HISTORY,MEMBERS,RELEASES }
 public record Rule(String prefix,Set<Capability> denied,Set<Panel> hidden){public Rule{prefix=path(prefix);denied=Set.copyOf(denied);hidden=Set.copyOf(hidden);}}
 public record Member(Role role,Set<Capability> denied,Set<Panel> hidden,List<Rule> rules){public Member{Objects.requireNonNull(role);denied=Set.copyOf(denied);hidden=Set.copyOf(hidden);rules=List.copyOf(rules);if(rules.size()>128)throw new IllegalArgumentException("LIMIT");}}
 private final UUID owner;private final Map<UUID,Member> members=new HashMap<>();private long revision;
 public WorkspacePolicy121(UUID owner){this.owner=Objects.requireNonNull(owner);members.put(owner,new Member(Role.OWNER,Set.of(),Set.of(),List.of()));}
 public static String path(String p){
  if(p==null||p.length()>240||!com.fixmer.genesis.technology.storage.ProjectPath.valid(p))throw new IllegalArgumentException("PATH");
  for(String part:p.split("/"))if(part.startsWith(".")&&!Set.of(".gitignore",".gitattributes",".editorconfig",".gitkeep",".github").contains(part))throw new IllegalArgumentException("PATH");
  return p;
 }
 public static final int MAX_MEMBERS=64;
 public synchronized void grant(UUID actor,long expected,UUID target,Member value){
  if(!owner.equals(actor)||expected!=revision||owner.equals(target)||value.role==Role.OWNER||members.size()>=MAX_MEMBERS&&!members.containsKey(target))throw new SecurityException("MEMBER_CHANGE");members.put(Objects.requireNonNull(target),value);revision++;
 }
 public synchronized void revoke(UUID actor,long expected,UUID target){if(!owner.equals(actor)||expected!=revision||owner.equals(target))throw new SecurityException("MEMBER_CHANGE");members.remove(target);revision++;}
 private static Set<Capability> defaults(Role r){return switch(r){case OWNER->EnumSet.allOf(Capability.class);case MAINTAINER->EnumSet.complementOf(EnumSet.of(Capability.MANAGE_MEMBERS));case DEVELOPER->EnumSet.of(Capability.READ,Capability.EDIT,Capability.CREATE,Capability.BUILD,Capability.VIEW_HISTORY,Capability.ERASE_CODE,Capability.SELECT_FILES);case REVIEWER->EnumSet.of(Capability.READ,Capability.VIEW_HISTORY,Capability.APPROVE,Capability.SELECT_FILES);case VIEWER->EnumSet.of(Capability.READ);};}
 private static boolean within(String file,String prefix){return file.equalsIgnoreCase(prefix)||file.length()>prefix.length()&&file.charAt(prefix.length())=='/'&&file.regionMatches(true,0,prefix,0,prefix.length());}
 public synchronized boolean allows(UUID actor,Capability capability,String file){file=path(file);var m=members.get(actor);if(m==null)return false;if(owner.equals(actor))return true;if(capability!=Capability.READ&&!allows(actor,Capability.READ,file))return false;if(capability==Capability.ERASE_CODE&&!allows(actor,Capability.EDIT,file))return false;if(capability==Capability.PUBLISH&&!allows(actor,Capability.BUILD,file))return false;if(!defaults(m.role).contains(capability)||m.denied.contains(capability))return false;for(var r:m.rules)if(within(file,r.prefix)&&r.denied.contains(capability))return false;return true;}
 public synchronized void require(UUID actor,Capability capability,String file){if(!allows(actor,capability,file))throw new SecurityException("DENIED");}
 public synchronized boolean panel(UUID actor,Panel panel){var m=members.get(actor);return m!=null&&(owner.equals(actor)||!m.hidden.contains(panel));}
 public synchronized boolean panel(UUID actor,Panel panel,String file){if(!panel(actor,panel))return false;var m=members.get(actor);if(owner.equals(actor))return true;file=path(file);for(var r:m.rules)if(within(file,r.prefix)&&r.hidden.contains(panel))return false;return true;}
 public record Snapshot(UUID owner,long revision,Map<UUID,Member> members){public Snapshot{members=Map.copyOf(members);if(revision<0||members.size()>64||!members.containsKey(owner)||members.get(owner).role()!=Role.OWNER)throw new IllegalArgumentException("POLICY");for(var e:members.entrySet())if(!e.getKey().equals(owner)&&e.getValue().role()==Role.OWNER)throw new IllegalArgumentException("OWNER");}}
 public synchronized Snapshot snapshot(){return new Snapshot(owner,revision,members);}
 public static WorkspacePolicy121 restore(Snapshot s){var p=new WorkspacePolicy121(s.owner);p.members.clear();p.members.putAll(s.members);p.revision=s.revision;return p;}
 public synchronized long revision(){return revision;}public synchronized Map<UUID,Member> members(){return Map.copyOf(members);}
}
