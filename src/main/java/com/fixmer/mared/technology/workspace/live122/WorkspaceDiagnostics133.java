package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.gui2.framework.components.editor.MaredMultiLineEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.io.*;import java.nio.file.*;import java.time.Instant;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;
/** The actual error is displayed and copyable; its location is explicit rather than promised off-screen. */
public final class WorkspaceDiagnostics133 extends MaredScreen {
 private final Screen previous;private final String details;private static volatile String last="";
 public WorkspaceDiagnostics133(Screen previous,String details){super(WorkspaceScreen122.tr("error_details133"));this.previous=previous;this.details=details.isEmpty()?last:details;}
 public static Path logFile(){return Minecraft.getInstance().gameDirectory.toPath().resolve("logs/mared-workspace.log").toAbsolutePath();}
 public static synchronized String report(Exception error){
  last=com.fixmer.genesis.technology.workspace.live122.WorkspaceFailure133.report(logFile(),error);
  return last;
 }
 @Override protected void init(){int x=16,w=Math.max(1,width-32);var box=addRenderableWidget(new MaredMultiLineEditBox(x,48,w,Math.max(20,height-112),text(),()->{}));box.setValue(details.isEmpty()?WorkspaceScreen122.tr("no_error133").getString():details);box.setEditable(false);addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("copy_error133"),b->minecraft.keyboardHandler.setClipboard(details+"\n"+logFile())).bounds(x,height-54,Math.max(1,(w-6)/2),20).build());addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("open_log133"),b->net.minecraft.Util.getPlatform().openPath(logFile().getParent())).bounds(x+(w+6)/2,height-54,Math.max(1,(w-6)/2),20).build());addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("back"),b->onClose()).bounds(x,height-28,w,20).build());}
 @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,bg());g.drawCenteredString(font,title,width/2,10,text());g.drawString(font,font.plainSubstrByWidth(logFile().toString(),Math.max(1,width-32)),16,29,dim(),false);super.render(g,mx,my,dt);}
 @Override public void onClose(){minecraft.setScreen(previous);}@Override public boolean isPauseScreen(){return false;}
}
