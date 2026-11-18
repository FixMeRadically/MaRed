package com.fixmer.genesis.supervisor;
import java.io.*;
/** Offline named-owner repair. Never adopts PID, launches Minecraft, or restores world data. */
public final class SetRecovery99 {
 private SetRecovery99(){}
 public static void main(String[] args){try{
  if(args.length<2||args.length>3||!args[0].startsWith("--config="))throw new IOException("SET99_ARGUMENTS");
  boolean restore=args[1].equals("--restore"),cancel=args[1].equals("--cancel-pending");if(!restore&&!cancel&&!args[1].equals("--inspect")||(restore||cancel)&&(args.length!=3||!args[2].startsWith("--revision="))||!restore&&!cancel&&args.length!=2)throw new IOException("SET99_ARGUMENTS");
  SupervisorConfig c=SupervisorConfig.load(new String[]{args[0]});
  try(var owner=ColdOwner94.acquireCold(c);var profile=ProfileLease92.writer(c.gameDir())){
   var d=new ModDeployment(c.gameDir());RecoveryView91 v;
   try{v=d.inspectInterruptedSet99(c);}catch(IOException noTrial){if(!"SET99_NO_TRIAL".equals(noTrial.getMessage()))throw noTrial;v=d.pendingSet99();}
   if(cancel){if(!v.phase().equals("PENDING99"))throw new IOException("SET99_NO_PENDING");d.cancelReviewedSet99(args[2].substring(11));System.out.println("SET99_PENDING_CANCELLED: installed mods and worlds unchanged");return;}
   if(restore){String revision=args[2].substring(11);if(!v.revision().equals(revision))throw new IOException("RECOVERY91_STALE");d.restoreAfterExit92(revision);System.out.println("SET99_RESTORED_TO_MENU_HOLD: no game launched; no world file changed");}
   else System.out.println("schema=99\nphase="+v.phase()+"\nrevision="+v.revision()+"\ntransaction="+v.transaction()+"\nretryAllowed="+v.keepAllowed()+"\nNo files changed. Inspection grants no later authority.");
  }
 }catch(Exception failure){String reason=failure.getMessage();System.err.println(reason!=null&&reason.matches("(?:SET99|PROFILE92|RECOVERY91|COLD94)_[A-Z0-9_]+")?reason:"SET99_REPAIR_REFUSED");System.exit(2);}}
}
