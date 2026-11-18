import com.fixmer.mared.technology.server.setup.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicBoolean;
/** Actual UI state-machine code with an explicit external-worker phase fixture. No Minecraft tick/focus/Job test. */
public final class BootstrapPreparationChecks87 {
 static int n;static void ok(boolean b){if(!b)throw new AssertionError("bootstrap87 "+n);n++;}
 static void put(Object o,String field,Object value)throws Exception{Field f=o.getClass().getDeclaredField(field);f.setAccessible(true);f.set(o,value);}
 static void phase(String s,boolean active)throws Exception{Field f=RemoteApplyService70.class.getDeclaredField("phase");f.setAccessible(true);f.set(null,s);Field b=RemoteApplyService70.class.getDeclaredField("busy");b.setAccessible(true);((AtomicBoolean)b.get(null)).set(active);}
 public static void main(String[] args)throws Exception{
  BootstrapService76 s=new BootstrapService76();put(s,"download",new com.fixmer.genesis.technology.server.remote.RemoteClient69.Download(java.nio.file.Path.of("/fixture"),java.nio.file.Path.of("/fixture/client.zip"),null));put(s,"preparing",true);put(s,"armed",true);phase("ARMING",true);ok(s.canCancelPreparation87());ok(!s.armed());
  phase("ARMED",true);ok(!s.canCancelPreparation87());ok(s.armed());
  phase("FAILED",false);s.tick();ok(!s.running());ok(!s.armed());ok(s.phase().equals("APPLY_REFUSED"));ok(s.canApply87());
  put(s,"preparing",true);put(s,"armed",true);phase("CANCELLED",false);s.tick();ok(!s.running());ok(s.phase().equals("APPLY_REFUSED"));
  put(s,"preparing",true);put(s,"armed",false);phase("PREPARING",true);ok(s.canCancelPreparation87());s.tick();ok(s.running());ok(!s.armed());
  phase("NO_CANDIDATE",false);s.tick();ok(!s.running());ok(!s.canCancelPreparation87());phase("IDLE",false);
  System.out.println("Bootstrap87 actual UI transition/cancel/recovery checks passed: "+n+"; worker phases are fixtures, no Minecraft or Windows.");
 }
}
