package com.fixmer.genesis.supervisor;
import java.awt.*;import java.awt.image.BufferedImage;import java.net.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import com.sun.net.httpserver.HttpServer;
public final class UiResourceChecks144 {
 static int count;static void check(boolean c,String name){if(!c)throw new AssertionError(name);count++;}
 static RestartSurface surface(int w,int h){return new RestartSurface(0,0,1920,1080,w,h,false,System.currentTimeMillis(),UUID.randomUUID().toString());}
 public static void main(String[] args)throws Exception{
  check(SwingRestartDisplay.rasterSize(surface(640,360),new Rectangle()).equals(new Dimension(640,360)),"ordinary size");
  check(SwingRestartDisplay.rasterSize(surface(2048,2048),new Rectangle()).equals(new Dimension(2048,2048)),"boundary size");
  Dimension d=SwingRestartDisplay.rasterSize(surface(16384,16384),new Rectangle());check((long)d.width*d.height<=4194304&&new BufferedImage(d.width,d.height,BufferedImage.TYPE_INT_RGB).getWidth()==d.width,"bounded allocation");
  d=SwingRestartDisplay.rasterSize(surface(16384,4096),new Rectangle());check(Math.abs(d.width/(double)d.height-4)<.01,"aspect ratio");
  check(SwingRestartDisplay.rasterSize(null,new Rectangle(1920,1080)).equals(new Dimension(640,360)),"default size");
  d=SwingRestartDisplay.rasterSize(null,new Rectangle(Integer.MAX_VALUE,Integer.MAX_VALUE));check((long)d.width*d.height<=4194304,"overflow-safe default");
  Path file=Files.createTempFile("log144-",".log");String quiet=System.getProperty("genesis.supervisor.quiet");System.setProperty("genesis.supervisor.quiet","true");
  try{try(var log=new SupervisorLog(file)){log.info("a"+"😀".repeat(9000));log.info("NEXT_LINE_144");}String text=Files.readString(file);check(text.contains("NEXT_LINE_144")&&text.contains("😀")&&StandardCharsets.UTF_8.newEncoder().canEncode(text),"file log remains writable");}finally{Files.delete(file);if(quiet==null)System.clearProperty("genesis.supervisor.quiet");else System.setProperty("genesis.supervisor.quiet",quiet);}
  var owner=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var owners=new AtomicInteger();var proxies=new AtomicInteger();String token="fixture-token-144-"+"a".repeat(32);
  owner.createContext("/identity",e->{owners.incrementAndGet();byte[] bytes="service=genesis-supervisor\nprotocol=4\nlifecycleVersion=10\n".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(token.equals(e.getRequestHeaders().getFirst("X-Genesis-Token"))?200:403,bytes.length);try(var out=e.getResponseBody()){out.write(bytes);}});
  proxy.createContext("/",e->{proxies.incrementAndGet();e.sendResponseHeaders(502,-1);e.close();});owner.start();proxy.start();ProxySelector previous=ProxySelector.getDefault();
  try{ProxySelector.setDefault(new ProxySelector(){public java.util.List<Proxy> select(URI uri){return java.util.List.of(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",proxy.getAddress().getPort())));}public void connectFailed(URI uri,SocketAddress address,java.io.IOException error){}});
   SupervisorRecovery.identity(owner.getAddress().getPort(),token);check(owners.get()==1&&proxies.get()==0,"local token bypasses global proxy");
  }finally{ProxySelector.setDefault(previous);owner.stop(0);proxy.stop(0);}
  System.out.println("UiResourceChecks144: "+count+" passed");
 }
}
