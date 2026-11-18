package com.fixmer.mared.gui2.framework.overlay;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;
/** Explicit directory selection; listing stays off the render thread. */
public final class DirectoryPickerOverlay implements Overlay {
 private record Hit(int x,int y,int w,int h,Runnable action){boolean contains(double mx,double my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}}
 private final OverlayManager overlays;private final Consumer<Path> selected;private final int accent;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"Genesis folder picker");t.setDaemon(true);return t;});
 private Future<List<Path>> pending;private Path current;private List<Path> children=List.of();private List<Hit> hits=List.of();
 private final Deque<Path> back133=new ArrayDeque<>(),forward133=new ArrayDeque<>();private Path lastClick133;private long lastClickAt133;
 private int scroll,cursor=-1,x,y,w,h,rows;private boolean close,disposed,accepted,drives;private String error="";
 public DirectoryPickerOverlay(OverlayManager overlays,Consumer<Path> selected,int accent){this(overlays,selected,accent,Path.of(System.getProperty("user.home")));}
 public DirectoryPickerOverlay(OverlayManager overlays,Consumer<Path> selected,int accent,Path initial){this.overlays=overlays;this.selected=selected;this.accent=accent;load(initial);}
 private String tr(String key){return MaredLang.get("mared.files.picker."+key);}
 private void cancelPending(){if(pending!=null)pending.cancel(true);pending=null;worker.purge();}
 private void load(Path path){if(current!=null&&!current.equals(path.toAbsolutePath().normalize())){back133.addLast(current);if(back133.size()>64)back133.removeFirst();forward133.clear();}load133(path);}
 private void load133(Path path){
  if(disposed||accepted)return;cancelPending();current=path.toAbsolutePath().normalize();drives=false;scroll=0;cursor=-1;children=List.of();hits=List.of();error="";Path captured=current;
  pending=worker.submit(()->{var list=new ArrayList<Path>();try(var entries=Files.newDirectoryStream(captured)){int count=0;for(Path p:entries){if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException();if(++count>4096)throw new java.io.IOException(tr("too_many"));if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(p))list.add(p);}}list.sort(Comparator.comparing(p->p.getFileName().toString(),String.CASE_INSENSITIVE_ORDER));return List.copyOf(list);});
 }
 private void poll(){if(pending==null||!pending.isDone())return;try{children=pending.get();}catch(Exception bad){Throwable cause=bad.getCause()==null?bad:bad.getCause();error=tr("read_error")+": "+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage());}pending=null;}
 private void showDrives(){cancelPending();drives=true;error="";children=new ArrayList<>();for(Path root:FileSystems.getDefault().getRootDirectories())children.add(root);scroll=0;cursor=-1;hits=List.of();}
 private void up(){if(!drives&&current.getParent()!=null)load(current.getParent());else showDrives();}
 private static Path inputPath(String value){String path=value.trim();if(path.length()>1&&path.startsWith("\"")&&path.endsWith("\""))path=path.substring(1,path.length()-1);return Path.of(path);}
 private void enterPath(){overlays.push(new NameDialogOverlay(tr("path"),v->load(inputPath(v)),accent,false,v->{try{return inputPath(v).isAbsolute()?null:tr("absolute");}catch(InvalidPathException bad){return tr("absolute");}}).customInput().inputLabel("mared.dialog.path_label").acceptLabel("mared.dialog.open").maxLength(2048).initialValue(drives?"":current.toString()));}
 private void newFolder133(){if(!canSelect())return;Path parent=current;overlays.push(new NameDialogOverlay(tr("new_folder133"),name->{try{Files.createDirectory(parent.resolve(name));load(parent);}catch(Exception error){this.error=error.getMessage();}},accent,false,name->com.fixmer.genesis.technology.storage.ProjectPath.valid(name)&&!name.contains("/")?null:tr("absolute")).customInput().acceptLabel("mared.dialog.create").initialValue("New folder"));}
 private boolean canSelect(){return !drives&&!accepted&&!disposed&&pending==null&&error.isEmpty();}
 private void select(){if(!canSelect())return;accepted=true;try{selected.accept(cursor>=0&&cursor<children.size()?children.get(cursor):current);close=true;}catch(RuntimeException bad){accepted=false;error=tr("select_error")+": "+(bad.getMessage()==null?bad.getClass().getSimpleName():bad.getMessage());}}
 @Override public OverlayLayer layer(){return OverlayLayer.MODAL;}@Override public boolean isInputBarrier(){return true;}@Override public boolean closeOnEscape(){return true;}
 @Override public boolean consumeCloseRequest(){boolean result=close;close=false;return result;}@Override public void onClose(){disposed=true;cancelPending();worker.shutdownNow();}
 private void button(GuiGraphics g,Font f,List<Hit> into,int bx,int by,int bw,String label,boolean enabled,int mx,int my,Runnable action){boolean hover=mx>=bx&&mx<bx+bw&&my>=by&&my<by+18;g.fill(bx,by,bx+bw,by+18,hover&&enabled?raised():panel());g.drawString(f,TextUtils.ellipsize(f,label,Math.max(1,bw-8)),bx+4,by+5,enabled?text():dim(),false);if(enabled)into.add(new Hit(bx,by,bw,18,action));}
 @Override public void render(GuiGraphics g,Font f,int sw,int sh,int mx,int my){
  poll();w=Math.max(1,Math.min(560,sw-16));h=Math.max(1,Math.min(340,sh-16));x=(sw-w)/2;y=(sh-h)/2;rows=Math.max(0,(h-128)/18);scroll=Math.max(0,Math.min(scroll,Math.max(0,children.size()-rows)));cursor=Math.min(cursor,children.size()-1);
  var nextHits=new ArrayList<Hit>();g.fill(0,0,sw,sh,0x88000000);g.fill(x,y,x+w,y+h,panel());g.fill(x,y,x+w,y+1,accent);
  g.drawString(f,TextUtils.ellipsize(f,tr("title"),Math.max(1,w-16)),x+8,y+8,text(),false);g.drawString(f,TextUtils.ellipsize(f,drives?tr("drives"):current.toString(),Math.max(1,w-16)),x+8,y+25,dim(),false);
  int gap=3;
  int usable=w-12;
  int shortWidth=Math.min(25,Math.max(15,usable/9));
  int commandsWidth=Math.max(2,usable-shortWidth*3-gap*4);
  int pathWidth=commandsWidth/2, folderWidth=commandsWidth-pathWidth;
  int bx=x+6, by=y+42;
  button(g,f,nextHits,bx,by,shortWidth,"‹",!back133.isEmpty(),mx,my,this::goBack);
  bx+=shortWidth+gap;
  button(g,f,nextHits,bx,by,shortWidth,"›",!forward133.isEmpty(),mx,my,this::goForward);
  bx+=shortWidth+gap;
  button(g,f,nextHits,bx,by,shortWidth,"↑",true,mx,my,this::up);
  bx+=shortWidth+gap;
  button(g,f,nextHits,bx,by,pathWidth,tr("path"),true,mx,my,this::enterPath);
  bx+=pathWidth+gap;
  button(g,f,nextHits,bx,by,folderWidth,tr("new_folder133"),canSelect(),mx,my,this::newFolder133);
  int bottom=Math.max(y+66,y+h-60);g.enableScissor(x+4,y+66,x+w-4,bottom);
  try{if(pending==null&&error.isEmpty()&&children.isEmpty())g.drawString(f,TextUtils.ellipsize(f,tr("empty"),Math.max(1,w-16)),x+8,y+70,dim(),false);
   for(int i=0;i<rows&&scroll+i<children.size();i++){int at=scroll+i,yy=y+68+i*18;Path p=children.get(at);if(at==cursor||mx>=x+4&&mx<x+w-4&&my>=yy&&my<yy+18)g.fill(x+4,yy,x+w-4,yy+18,raised());g.drawString(f,TextUtils.ellipsize(f,"    "+(p.getFileName()==null?p.toString():p.getFileName().toString()),Math.max(1,w-16)),x+8,yy+4,text(),false);g.fill(x+8,yy+7,x+17,yy+14,dim());g.fill(x+8,yy+5,x+12,yy+8,dim());nextHits.add(new Hit(x+4,yy,w-8,18,()->{long now=System.nanoTime();cursor=at;if(p.equals(lastClick133)&&now-lastClickAt133<450_000_000L)load(p);lastClick133=p;lastClickAt133=now;}));}
  }finally{g.disableScissor();}
  String status=pending!=null?tr("loading"):!error.isEmpty()?error:tr(drives?"choose_drive":"keys");g.drawString(f,TextUtils.ellipsize(f,status,Math.max(1,w-16)),x+8,y+h-48,dim(),false);
  int actionW=Math.max(1,(w-18)/2);button(g,f,nextHits,x+6,y+h-27,actionW,tr("cancel"),true,mx,my,()->close=true);button(g,f,nextHits,x+12+actionW,y+h-27,actionW,tr("select"),canSelect(),mx,my,this::select);hits=List.copyOf(nextHits);
 }
 @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&!accepted)for(Hit hit:hits)if(hit.contains(mx,my)){hit.action.run();break;}return true;}
 @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll+=dy<0?3:dy>0?-3:0;scroll=Math.max(0,Math.min(scroll,Math.max(0,children.size()-rows)));return true;}
 private void goBack(){
  if(back133.isEmpty())return;
  forward133.addLast(current);
  load133(back133.removeLast());
 }
 private void goForward(){
  if(forward133.isEmpty())return;
  back133.addLast(current);
  load133(forward133.removeLast());
 }
 @Override public boolean keyPressed(int key,int scan,int mods){
  if(key==256){close=true;return true;}if((mods&2)!=0&&key==76){enterPath();return true;}if((mods&2)!=0&&key==67&&!drives){Minecraft.getInstance().keyboardHandler.setClipboard(current.toString());return true;}if(key==259){up();return true;}if((mods&15)==4&&key==263){goBack();return true;}
  if((mods&15)==4&&key==262){goForward();return true;}
  if((mods&15)==4&&key==265){up();return true;}
  if(key==264||key==265){if(!children.isEmpty()){cursor=Math.max(0,Math.min(children.size()-1,cursor<0?(key==264?0:children.size()-1):cursor+(key==264?1:-1)));if(cursor<scroll)scroll=cursor;if(cursor>=scroll+rows)scroll=Math.max(0,cursor-rows+1);}return true;}
  if(key==257||key==335){if((mods&2)!=0)select();else if(cursor>=0&&cursor<children.size())load(children.get(cursor));return true;}return true;
 }
}
