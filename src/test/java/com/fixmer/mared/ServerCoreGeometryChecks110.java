package com.fixmer.mared;
import com.fixmer.mared.gui2.genesis.GenesisCamera;
import com.fixmer.mared.gui2.genesis.render.GenesisCoreRenderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;import java.nio.file.*;
/** Executes the actual Core renderer into a vertex recorder. No GPU/blur/Minecraft window is simulated. */
public final class ServerCoreGeometryChecks110 {
 static int checks;static void ok(boolean b){checks++;if(!b)throw new AssertionError("core110 "+checks);}
 static final class Recorder implements VertexConsumer {
  final List<float[]> points=new ArrayList<>();
  @Override public VertexConsumer addVertex(float x,float y,float z){points.add(new float[]{x,y,0,0,0,0});return this;}
  @Override public VertexConsumer addVertex(org.joml.Matrix4f matrix,float x,float y,float z){return addVertex(x,y,z);}
  @Override public VertexConsumer setColor(float r,float g,float b,float a){var p=points.get(points.size()-1);p[2]=r;p[3]=g;p[4]=b;p[5]=a;return this;}
  @Override public VertexConsumer setColor(int r,int g,int b,int a){return setColor(r/255f,g/255f,b/255f,a/255f);}
  @Override public VertexConsumer setUv(float u,float v){return this;}
  @Override public VertexConsumer setUv1(int u,int v){return this;}
  @Override public VertexConsumer setUv2(int u,int v){return this;}
  @Override public VertexConsumer setNormal(float x,float y,float z){return this;}
 }
 static List<float[]> vertices(float seconds){
  var consumer=new Recorder();new GenesisCoreRenderer().render(consumer,null,new GenesisCamera(),640,360,seconds,48,0xFFFFFFFF);return consumer.points;
 }
 public static void main(String[]args)throws Exception{
  for(float t:new float[]{2,3,5,30,600}){var points=vertices(t);ok(points.size()>100&&points.size()<100000);ok(points.size()%3==0);for(var point:points){for(float v:point)if(!Float.isFinite(v))throw new AssertionError("nonfinite core vertex");for(int i=2;i<6;i++)if(point[i]<0||point[i]>1)throw new AssertionError("invalid color");}checks++;}
  var a=vertices(2);var b=vertices(2);ok(a.size()==b.size());for(int i=0;i<a.size();i++)if(!Arrays.equals(a.get(i),b.get(i)))throw new AssertionError("same input changed geometry");checks++;
  if(args.length==1){StringJoiner json=new StringJoiner(",","[","]");for(var p:a)json.add(Arrays.toString(p));Files.writeString(Path.of(args[0]),json.toString());}
  System.out.println("Core110 actual shared-renderer geometry/determinism assertions passed: "+checks+"; recorded vertices only, GPU/Minecraft visual output untested.");
 }
}
