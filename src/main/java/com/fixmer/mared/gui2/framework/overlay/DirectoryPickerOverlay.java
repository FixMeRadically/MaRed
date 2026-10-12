package com.fixmer.mared.gui2.framework.overlay;
import com.fixmer.mared.MaredLang;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;
/** Explicit local folder selection; bounded listing runs outside the render thread. */
public final class DirectoryPickerOverlay implements Overlay {
 private final OverlayManager overlays;private final Consumer<Path> selected;private final int accent;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Genesis folder picker");t.setDaemon(true);return t;});
 private Future<List<Path>> pending;private Path current=Path.of(System.getProperty("user.home")).toAbsolutePath();private List<Path> children=List.of();private int scroll,x,y,w,h,rows;private boolean close,disposed;private String error="";
 public DirectoryPickerOverlay(OverlayManager overlays,Consumer<Path> selected,int accent){this(overlays,selected,accent,Path.of(System.getProperty("user.home")));}
 public DirectoryPickerOverlay(OverlayManager overlays,Consumer<Path> selected,int accent,Path initial){this.overlays=overlays;this.selected=selected;this.accent=accent;load(initial);}
 private String tr(String key){return MaredLang.get("mared.files.picker."+key);}
 private void load(Path path){if(disposed)return;if(pending!=null)pending.cancel(true);worker.purge();current=path.toAbsolutePath().normalize();scroll=0;children=List.of();error="";Path captured=current;
  pending=worker.submit(()->{var list=new ArrayList<Path>();try(var entries=Files.newDirectoryStream(captured)){int count=0;for(Path p:entries){if(Thread.currentThread().isInterrupted())throw new java.io.IOException("Cancelled");if(++count>4096)throw new java.io.IOException("Too many entries; enter a more specific path");if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p))list.add(p);}}list.sort(Comparator.comparing(p->p.getFileName().toString(),String.CASE_INSENSITIVE_ORDER));return List.copyOf(list);});
 }
 private void poll(){if(pending==null||!pending.isDone())return;try{children=pending.get();}catch(Exception bad){error=bad.getCause()==null?bad.toString():bad.getCause().toString();}pending=null;}
 @Override public OverlayLayer layer(){return OverlayLayer.MODAL;}@Override public boolean isInputBarrier(){return true;}@Override public boolean closeOnEscape(){return true;}
 @Override public boolean consumeCloseRequest(){boolean result=close;close=false;return result;}
 @Override public void onClose(){disposed=true;worker.shutdownNow();}
 @Override public void render(GuiGraphics g,Font f,int sw,int sh,int mx,int my){poll();w=Math.max(1,Math.min(560,sw-16));h=Math.max(1,Math.min(340,sh-16));x=(sw-w)/2;y=(sh-h)/2;rows=Math.max(0,(h-105)/18);scroll=Math.max(0,Math.min(scroll,Math.max(0,children.size()-rows)));
  g.fill(x,y,x+w,y+h,panel());g.fill(x,y,x+w,y+1,accent);g.drawString(f,f.plainSubstrByWidth(tr("title"),Math.max(1,w-16)),x+8,y+8,text(),false);
  g.drawString(f,f.plainSubstrByWidth(current.toString(),Math.max(1,w-16)),x+8,y+27,dim(),false);
  g.drawString(f,tr("up"),x+8,y+47,text(),false);g.drawString(f,tr("path"),x+100,y+47,text(),false);g.drawString(f,tr("drives"),x+220,y+47,text(),false);
  g.enableScissor(x+4,y+66,x+w-4,y+h-36);try{for(int i=0;i<rows&&scroll+i<children.size();i++){Path p=children.get(scroll+i);int yy=y+68+i*18;if(mx>=x&&mx<x+w&&my>=yy&&my<yy+18)g.fill(x+4,yy,x+w-4,yy+18,raised());g.drawString(f,f.plainSubstrByWidth("▸ "+(p.getFileName()==null?p.toString():p.getFileName().toString()),Math.max(1,w-16)),x+8,yy+4,text(),false);}}finally{g.disableScissor();}
  if(pending!=null||!error.isEmpty())g.drawString(f,f.plainSubstrByWidth(pending!=null?tr("loading"):error,Math.max(1,w-16)),x+8,y+h-51,dim(),false);
  g.drawString(f,tr("cancel"),x+8,y+h-20,dim(),false);g.drawString(f,tr("select"),x+Math.max(90,w-160),y+h-20,pending==null&&error.isEmpty()?text():dim(),false);
 }
 @Override public boolean mouseClicked(double mx,double my,int button){if(button!=0)return true;if(mx<x||mx>=x+w||my<y||my>=y+h)return true;
  if(my>=y+h-30){if(mx<x+w/2)close=true;else if(pending==null&&error.isEmpty()){selected.accept(current);close=true;}return true;}
  if(my>=y+42&&my<y+62){if(mx<x+92){if(current.getParent()!=null)load(current.getParent());}else if(mx<x+215){overlays.push(new NameDialogOverlay(tr("path"),v->load(Path.of(v)),accent,false,v->{try{return Path.of(v).isAbsolute()?null:tr("absolute");}catch(Exception bad){return tr("absolute");}}).maxLength(2048).initialValue(current.toString()));}else{if(pending!=null)pending.cancel(true);pending=null;children=new ArrayList<>();for(Path root:FileSystems.getDefault().getRootDirectories())children.add(root);scroll=0;}return true;}
  int index=(int)((my-y-68)/18)+scroll;if(my>=y+68&&my<y+h-36&&index>=0&&index<children.size())load(children.get(index));return true;
 }
 @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll+=dy<0?3:dy>0?-3:0;scroll=Math.max(0,Math.min(scroll,Math.max(0,children.size()-rows)));return true;}
 @Override public boolean keyPressed(int key,int scan,int mods){if(key==256){close=true;return true;}return true;}
}
