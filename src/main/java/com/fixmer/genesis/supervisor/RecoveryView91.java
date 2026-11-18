package com.fixmer.genesis.supervisor;
/** Bounded local review; no account tokens or executable commands. */
public record RecoveryView91(String phase,String revision,String transaction,String modId,String version,String previousVersion,boolean keepAllowed) {
 public boolean blocked(){return !phase.equals("NONE");}
 public boolean needsChoice(){return phase.equals("CHOICE_REQUIRED");}
}
