package com.fixmer.mared;
/** Child of the native test Job. It only launches the fixed Python broker fixture. */
public final class IndependentProbe104 {
 public static void main(String[]a)throws Exception{
  if(a.length!=3)throw new IllegalArgumentException();Process p=new ProcessBuilder(a[0],a[1],"--broker",a[2]).inheritIO().start();if(!p.waitFor(15,java.util.concurrent.TimeUnit.SECONDS)){p.destroy();throw new java.io.IOException("SERVER104_PROBE_BROKER_TIMEOUT");}if(p.exitValue()!=0)throw new java.io.IOException("SERVER104_PROBE_BROKER_FAILED");
 }
}
