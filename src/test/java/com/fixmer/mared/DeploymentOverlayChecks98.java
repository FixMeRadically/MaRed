package com.fixmer.mared;
import com.fixmer.mared.technology.scripts.ScriptInfoOverlay;
/** Actual overlay keyboard state, without rendering, window focus or Minecraft event dispatch. */
public final class DeploymentOverlayChecks98 {
 private static int count;
 private static void ok(boolean value){count++;if(!value)throw new AssertionError("overlay98 "+count);}
 public static void main(String[] args){
  int[] applied={0},cancelled={0};
  var overlay=new ScriptInfoOverlay("Review","candidate",0).cancelWith98("Cancel",()->cancelled[0]++).continueWith("Apply",()->applied[0]++);
  ok(overlay.keyPressed(256,0,0));ok(applied[0]==0&&cancelled[0]==1);ok(overlay.consumeCloseRequest());ok(!overlay.consumeCloseRequest());
  overlay.keyPressed(257,0,0);overlay.keyPressed(256,0,0);ok(applied[0]==0&&cancelled[0]==1);
  var confirm=new ScriptInfoOverlay("Review","candidate",0).cancelWith98("Cancel",()->cancelled[0]++).continueWith("Apply",()->applied[0]++);
  ok(confirm.keyPressed(257,0,0));ok(applied[0]==1&&cancelled[0]==1);ok(confirm.consumeCloseRequest());confirm.keyPressed(256,0,0);confirm.keyPressed(335,0,0);ok(applied[0]==1&&cancelled[0]==1);
  var report=new ScriptInfoOverlay("Read only","body",0);report.keyPressed(256,0,0);ok(report.consumeCloseRequest());
  System.out.println("Deployment98 actual overlay cancel/confirm/single-decision checks passed: "+count+"; no render or Minecraft event dispatch.");
 }
}
