package com.fixmer.genesis.supervisor;
import java.util.*;import java.io.*;
/** One current owner offer, consumed at reservation. Owner supplies reaping or cold94 admission evidence. */
final class RecoveryControl92 {
 private String ticket="",launch="",revision="";private RecoveryView91 view;private boolean used;private String error="";
 synchronized void offer(String launch,RecoveryView91 view,boolean reaped,String error){
  if(!reaped||!UUID.fromString(launch).toString().equals(launch))throw new IllegalStateException("RECOVERY92_NO_EXIT_PROOF");
  this.ticket=UUID.randomUUID().toString();this.launch=launch;this.view=view;this.revision=view==null?"":view.revision();this.used=false;this.error=error;
 }
 synchronized boolean reserve(String ticket,String revision,String action){
  if(this.ticket.isEmpty()||action==null||used||!this.ticket.equals(ticket)||!this.revision.equals(revision)||!Set.of("RESTORE","RETRY","REFRESH","CLOSE").contains(action))return false;
  if((action.equals("RESTORE")||action.equals("RETRY"))&&(view==null||!Set.of("CHOICE_REQUIRED","RESTORE_REQUESTED").contains(view.phase())))return false;
  if(action.equals("RETRY")&&!view.keepAllowed())return false;
  used=true;return true;
 }
 synchronized String properties(String state,boolean busy){
  Properties p=new Properties();p.setProperty("schema","92");p.setProperty("state",state);p.setProperty("busy",Boolean.toString(busy));p.setProperty("ticket",ticket);p.setProperty("revision",revision);p.setProperty("launch",launch);p.setProperty("used",Boolean.toString(used));p.setProperty("error",error);
  if(view!=null){p.setProperty("phase",view.phase());p.setProperty("mod",display(view.modId()));p.setProperty("version",display(view.version()));p.setProperty("previous",display(view.previousVersion()));p.setProperty("retryAllowed",Boolean.toString(view.keepAllowed()));}
  StringWriter out=new StringWriter();try{p.store(out,"Genesis recovery92 status — no credentials");}catch(IOException impossible){throw new UncheckedIOException(impossible);}return out.toString();
 }
 synchronized String ticket(){return ticket;}
 private static String display(String s){return s.codePoints().filter(c->c>=32&&c!=127&&!(c>=0x202a&&c<=0x202e)&&!(c>=0x2066&&c<=0x2069)).limit(128).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();}
}
