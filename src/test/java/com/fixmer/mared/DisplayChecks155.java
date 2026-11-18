package com.fixmer.mared;
import com.fixmer.genesis.technology.editor.EmbeddedClip155;
import java.util.*;import java.util.concurrent.atomic.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;
/** Headless coordinate regression and validation of the exact target/registered injection. No native GUI claimed. */
public final class DisplayChecks155 {
 static int count;static void ok(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
 static boolean contains(int[] rect,int x,int y){return x>=rect[0]&&x<rect[2]&&y>=rect[1]&&y<rect[3];}
 static int[] clip(int x1,int y1,int x2,int y2){return new int[]{EmbeddedClip155.x(x1),EmbeddedClip155.y(y1),EmbeddedClip155.x(x2),EmbeddedClip155.y(y2)};}
 public static void main(String[] args)throws Exception{
  ok(Arrays.equals(clip(10,20,110,40),new int[]{10,20,110,40}),"standalone clip unchanged");
  int labelX=130,labelY=100;ok(!contains(new int[]{10,20,110,40},labelX,labelY),"old local clip reproduces invisible translated label");
  try(var outer=EmbeddedClip155.enter(80,70)){
   var translated=clip(10,20,110,40);ok(Arrays.equals(translated,new int[]{90,90,190,110}),"all four edges translated");ok(contains(translated,labelX,labelY),"translated label inside corrected clip");
   ok(translated[2]-translated[0]==100&&translated[3]-translated[1]==20,"button dimensions preserved");
   try(var inner=EmbeddedClip155.enter(5,-30)){ok(EmbeddedClip155.x(0)==85&&EmbeddedClip155.y(0)==40,"nested scrolled panel composes offsets");}
   ok(EmbeddedClip155.x(0)==80&&EmbeddedClip155.y(0)==70,"nested close restores outer");
   var threadResult=new AtomicReference<int[]>();var t=new Thread(()->threadResult.set(clip(1,2,3,4)));t.start();t.join();ok(Arrays.equals(threadResult.get(),new int[]{1,2,3,4}),"render scope does not leak into another thread");
  }
  ok(EmbeddedClip155.x(0)==0&&EmbeddedClip155.y(0)==0,"outer close restores standalone coordinates");
  try{try(var scope=EmbeddedClip155.enter(90,-200)){throw new IllegalStateException("render failure");}}catch(IllegalStateException expected){}
  ok(EmbeddedClip155.x(0)==0&&EmbeddedClip155.y(0)==0,"render failure cannot leave stale global offsets");
  var scope=EmbeddedClip155.enter(-5,8);scope.close();scope.close();ok(EmbeddedClip155.x(7)==7,"idempotent close");
  ClassNode target=new ClassNode();try(var in=DisplayChecks155.class.getResourceAsStream("/net/minecraft/client/gui/GuiGraphics.class")){if(in==null)throw new AssertionError("missing actual Minecraft GUI class");new ClassReader(in).accept(target,0);}
  var method=target.methods.stream().filter(m->m.name.equals("enableScissor")&&m.desc.equals("(IIII)V")).findFirst().orElseThrow();
  ok(method.instructions.iterator().hasNext(),"actual Minecraft 1.21.1 target exists");
  boolean rawRectangle=false,pose=false;for(var ins:method.instructions){if(ins instanceof MethodInsnNode m){if(m.owner.endsWith("ScreenRectangle")&&m.name.equals("<init>"))rawRectangle=true;if(m.owner.contains("PoseStack"))pose=true;}}
  ok(rawRectangle&&!pose,"actual vanilla clip ignores pose translation");
  ClassNode mixin=new ClassNode();try(var in=DisplayChecks155.class.getResourceAsStream("/com/fixmer/mared/mixin/EmbeddedScissor155.class")){new ClassReader(in).accept(mixin,0);}
  var ordinals=new HashSet<Integer>();var mouseOrdinals=new HashSet<Integer>();int hooks=0;
  for(var m:mixin.methods){var annotations=new ArrayList<AnnotationNode>();if(m.invisibleAnnotations!=null)annotations.addAll(m.invisibleAnnotations);if(m.visibleAnnotations!=null)annotations.addAll(m.visibleAnnotations);for(var a:annotations)if(a.desc.endsWith("/ModifyVariable;")){
   var values=new HashMap<String,Object>();for(int i=0;i<a.values.size();i+=2)values.put((String)a.values.get(i),a.values.get(i+1));
   ok(Boolean.TRUE.equals(values.get("argsOnly"))&&Integer.valueOf(1).equals(values.get("require")),"required argument hook");
   String descriptor=values.get("method").toString();boolean mouse=descriptor.contains("containsPointInScissor(II)Z");if(mouse)mouseOrdinals.add((Integer)values.get("ordinal"));else ordinals.add((Integer)values.get("ordinal"));ok(mouse||descriptor.contains("enableScissor(IIII)V"),"exact target descriptor");hooks++;
  }}
  ok(hooks==6&&ordinals.equals(Set.of(0,1,2,3))&&mouseOrdinals.equals(Set.of(0,1)),"both clip corners covered exactly once");
  String config;try(var in=DisplayChecks155.class.getResourceAsStream("/mared.mixins.json")){config=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  ok(config.contains("\"EmbeddedScissor155\""),"client hook registered");
  System.out.println("DisplayChecks155: "+count+" passed; native GUI acceptance remains separate");
 }
}
