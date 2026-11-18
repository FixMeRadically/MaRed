package com.fixmer.genesis.technology.server.firstlaunch;
/** Actual Windows Job self-test child. No Minecraft/game profile I/O. */
public final class FirstLaunchProbe {
    public static void main(String[] a)throws Exception{
        if(a.length==1&&a[0].equals("leaf")){Thread.sleep(120000);return;}
        if(a.length==2&&a[0].equals("tree")){
            var child=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-cp",System.getProperty("java.class.path"),FirstLaunchProbe.class.getName(),"leaf").start();
            java.nio.file.Files.writeString(java.nio.file.Path.of(a[1]),Long.toString(child.pid()));return;
        }
        throw new IllegalArgumentException("Fixed selftest only");
    }
}
