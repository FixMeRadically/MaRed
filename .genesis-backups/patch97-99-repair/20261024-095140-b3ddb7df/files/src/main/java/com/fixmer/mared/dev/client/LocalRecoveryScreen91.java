package com.fixmer.mared.dev.client;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;
import java.util.*;
/** Review is bound to the displayed journal revision; errors require an explicit refresh. */
public final class LocalRecoveryScreen91 extends MaredScreen {
 private final Screen previous;private String revision="",confirm="";private int left,w,scroll,maxScroll;private Button keep,restore,ack,apply,restart;private String displayed="";private boolean restarting;private String launchStatus="";
 public LocalRecoveryScreen91(Screen previous){super(tr("title"));this.previous=previous;LocalRecovery91.refresh();}
 private static Component tr(String key,Object...args){return Component.translatable("mared.recovery91."+key,args);}
 private Button button(String key,int y,Runnable run){return addRenderableWidget(Button.builder(tr(key),b->run.run()).bounds(left,y,w,20).build());}
 @Override protected void init(){w=Math.max(180,Math.min(490,width-24));left=(width-w)/2;
  keep=button("keep",height-142,()->choose("KEEP"));restore=button("restore",height-118,()->choose("RESTORE"));ack=button("ack",height-94,()->choose("ACK"));apply=button("apply",height-70,()->{if(allowed())LocalRecovery91.decide(revision,confirm);confirm="";});
  restart=button("restart",height-46,()->{if(menu()&&!LocalRecovery91.busy()&&!restarting){restarting=true;launchStatus="restarting";var request=DevRestartService.isUnderSupervisor()?DevRestartService.restartAsync():DevActivationService.activate();request.whenComplete((ok,error)->minecraft.execute(()->{restarting=false;launchStatus=error==null&&Boolean.TRUE.equals(ok)?"restarting":"restart_failed";}));}});
  addRenderableWidget(Button.builder(tr("refresh"),b->{confirm="";LocalRecovery91.refresh();}).bounds(left,height-22,(w-6)/2,20).build());addRenderableWidget(Button.builder(tr("back"),b->onClose()).bounds(left+(w+6)/2,height-22,(w-6)/2,20).build());tick();
 }
 private boolean menu(){return minecraft.level==null&&minecraft.getSingleplayerServer()==null;}
 private boolean allowed(){return menu()&&!LocalRecovery91.busy()&&!restarting&&!revision.isEmpty()&&revision.equals(LocalRecovery91.view().revision())&&!confirm.isEmpty();}
 private void choose(String action){revision=LocalRecovery91.view().revision();confirm=action;}
 @Override public void tick(){var v=LocalRecovery91.view();if(!displayed.equals(v.phase()+v.revision())){displayed=v.phase()+v.revision();confirm="";revision=v.revision();}if(keep==null)return;
  boolean ready=menu()&&!LocalRecovery91.busy()&&!restarting;keep.active=ready&&v.keepAllowed();restore.active=ready&&v.needsChoice();ack.active=ready&&v.phase().equals("RESTORED_REVIEW");apply.active=allowed();restart.active=ready&&v.phase().equals("RESTORE_REQUESTED");
 }
 @Override public void onClose(){if(!LocalRecovery91.busy()&&!restarting)minecraft.setScreen(previous);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(maxScroll,scroll-(int)(vertical*22)));return true;}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xF0141420);g.drawCenteredString(font,title,width/2,8,0xFFFFFFFF);var v=LocalRecovery91.view();List<Component> lines=new ArrayList<>();lines.add(tr("phase."+v.phase()));if(!v.modId().isEmpty())lines.add(tr("versions",v.modId(),v.version(),v.previousVersion().isEmpty()?"—":v.previousVersion()));lines.add(tr("warning"));if(v.needsChoice()&&!v.keepAllowed())lines.add(tr("restore_only"));if(!menu())lines.add(tr("leave_world"));if(!launchStatus.isEmpty())lines.add(tr(launchStatus));if(!confirm.isEmpty())lines.add(tr("confirm."+confirm));List<net.minecraft.util.FormattedCharSequence> wrapped=new ArrayList<>();for(var line:lines)wrapped.addAll(font.split(line,w));int bottom=Math.max(31,height-154);maxScroll=Math.max(0,wrapped.size()*11-(bottom-30));scroll=Math.min(scroll,maxScroll);g.enableScissor(left,30,left+w,bottom);int y=30-scroll;for(var part:wrapped){g.drawString(font,part,left,y,0xFFDCDCE7);y+=11;}g.disableScissor();super.render(g,mx,my,dt);}
}
