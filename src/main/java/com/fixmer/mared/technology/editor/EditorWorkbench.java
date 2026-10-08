package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.EditorLayout;
import com.fixmer.genesis.technology.editor.EditorLayout.Rect;
import com.fixmer.mared.MaredSettings;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.runtime.RuntimeProvider;
import com.fixmer.mared.gui2.studio.StudioSession;
import com.fixmer.mared.gui2.studio.action.StudioActions;
import com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent;
import net.minecraft.client.gui.GuiGraphics;
import java.util.*;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Session-owned Logic workbench. Adapts existing document/actions without copying their storage or save logic. */
public final class EditorWorkbench implements Disposable {
    private record Button(Rect bounds,String label,String hint,Runnable action,boolean floating) {}
    private static final String[] TABS={"Commands","Scripts","Events / Triggers","Blueprint","Behavior / States","Variables / Data","Network","Sandbox"};
    private static final String[] ICONS={"C","{}","E","N","AI","V","S","#"};
    private final StudioSession session;
    private final WorkbenchPreferences prefs=WorkbenchPreferences.load();
    private final CommandHelpBrowser help;
    private final ScriptLinksPanel links;
    private boolean showLinks;
    private final List<Button> buttons=new ArrayList<>();
    private EditorLayout.Frame frame;
    private Rect helpWindow;
    private double dragMouseX,dragMouseY;
    private Rect dragWindow;
    private int width,height,selected,railScroll,drag,settingsScroll;
    private boolean settings,buildingHelpWindow;
    private double phase;
    private MaredComponent captured;
    private String pluginPanelId;
    private com.fixmer.mared.gui2.docking.DockPanel pluginPanel(){return session.controller().extensionPanels().stream().filter(p->p.id().equals(pluginPanelId)).findFirst().orElse(null);}
    private void nextPluginPanel(){var panels=session.controller().extensionPanels();int current=-1;for(int i=0;i<panels.size();i++)if(panels.get(i).id().equals(pluginPanelId))current=i;pluginPanelId=current+1<panels.size()?panels.get(current+1).id():null;showLinks=false;if(selected==4){selected=1;prefs.selectedTab=1;}clearFocus();}
    public EditorWorkbench(StudioSession session){
        this.session=session;
        selected=prefs.selectedTab;
        var controller=session.controller();
        controller.workspacePanel().workspaceComponent().setSpanResolver(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.INSTANCE);
        controller.explorerPanel().explorerComponent().setFilesOnly(true);
        controller.consolePanel().consoleComponent().logPanel().setCompact(true);
        help=new CommandHelpBrowser(controller.bus(),controller.inspectorPanel().inspectorComponent());
        session.focusManager().attachTo(help);
        links=new ScriptLinksPanel(controller.bus(),()->{selected=1;prefs.selectedTab=1;showLinks=false;pluginPanelId=null;clearFocus();});
        links.setAi(selected==4);session.focusManager().attachTo(links);
        if(selected==1)help.setSource(com.fixmer.mared.commands.registry.MaredCommandRegistry.Source.MR);
    }
    private WorkspaceComponent workspace(){return session.controller().workspacePanel().workspaceComponent();}
    private MaredComponent files(){return session.controller().explorerPanel().component();}
    private MaredComponent logs(){return session.controller().consolePanel().component();}
    public void tick(float dt){if(prefs.animation&&!MaredSettings.isReducedMotion())phase=(phase+Math.max(0,Math.min(.1,dt))*.18)%(Math.PI*2);}
    public void suspended(){drag=0;captured=null;help.cancelInteraction();links.cancelInteraction();session.pointerManager().releaseAll();session.focusManager().clear();}
    @Override public void dispose(){suspended();prefs.save();}
    private void layout(int w,int h){width=w;height=h;frame=EditorLayout.calculate(w,h,prefs.filesRatio,prefs.helpRatio,prefs.logsRatio,prefs.filesOpen,helpColumn(),prefs.logsOpen);helpWindow=HelpWindowGeometry.calculate(w,h,prefs.helpWindowX,prefs.helpWindowY,prefs.helpWindowW,prefs.helpWindowH);}
    private boolean helpColumn(){return settings||(prefs.helpOpen&&prefs.helpDocked);}
    private boolean helpFloating(){return prefs.helpOpen&&!prefs.helpDocked;}
    public void render(MaredRenderContext ctx){
        session.actionContext().runs().update(session.controller().consolePanel().consoleComponent().logPanel());
        layout(ctx.width(),ctx.height());buttons.clear();var g=ctx.graphics();var font=ctx.font();
        if(width<260||height<150){g.fill(0,0,width,height,bg());g.drawString(font,EditorText.translate("Reduce GUI scale · Esc to return"),6,8,text(),false);return;}
        g.fill(0,0,width,height,bg());
        Rect editor=frame.editor();
        if(prefs.background&&editor.width()>0&&editor.height()>0){
            g.enableScissor(editor.x(),editor.y(),editor.right(),editor.bottom());
            int cx=editor.x()+editor.width()*3/4,cy=editor.y()+editor.height()/2;
            int radius=Math.min(95,Math.min(editor.width(),editor.height())/3);
            octahedron(g,cx,cy,radius,phase,quiet());
            for(int i=0;i<24;i++){double a=i*Math.PI/12+phase*.4;int x=cx+(int)(Math.cos(a)*radius*1.4),y=cy+(int)(Math.sin(a)*radius*.35);g.fill(x,y,x+1,y+1,quiet());}
            g.disableScissor();
        }
        rail(ctx);toolbar(ctx);
        face(g,frame.files(),panel(),prefs.depth);
        if(prefs.filesOpen&&frame.files().width()>25){
            Rect body=new Rect(frame.files().x()+2,frame.files().y()+2,Math.max(0,frame.files().width()-4),Math.max(0,frame.files().height()-4));renderComponent(files(),body,ctx);
            button(ctx,new Rect(frame.files().right()-30,frame.files().y()+2,12,12),"<","Collapse files",()->{prefs.filesOpen=false;clearFocus();});
        }else button(ctx,new Rect(frame.files().x(),frame.files().y(),frame.files().width(),Math.min(22,frame.files().height())),">","Expand files",()->prefs.filesOpen=true);
        var plugin=pluginPanel();
        if(showLinks||selected==4){renderComponent(links,editor.inset(3),ctx);}
        else if(plugin!=null){renderComponent(plugin.component(),editor.inset(3),ctx);}
        else if(selected<2){
            renderComponent(workspace(),editor.inset(3),ctx);
            if(workspace().host().isEmpty()&&editor.width()>150&&editor.height()>100){
                // The projected core is deliberately quiet; the text remains the primary focus.
                if(prefs.background)octahedron(g,editor.x()+editor.width()/2,editor.y()+editor.height()/2-3,Math.min(36,editor.height()/5),phase,quiet());
                g.drawString(font,EditorText.translate(TABS[selected]).toUpperCase(Locale.ROOT),editor.x()+12,editor.y()+43,text(),false);
                g.drawString(font,EditorText.translate("Open a file, or create one with Ctrl+N"),editor.x()+12,editor.y()+57,dim(),false);
            }
        }else{
            face(g,editor.inset(3),panel(),prefs.depth);
            g.enableScissor(editor.x(),editor.y(),editor.right(),editor.bottom());
            g.drawString(font,EditorText.translate(TABS[selected]),editor.x()+12,editor.y()+15,text(),false);
            g.drawString(font,EditorText.translate("This editor is planned for a later stage."),editor.x()+12,editor.y()+33,dim(),false);
            g.drawString(font,EditorText.translate("Your open documents remain in Commands / Scripts."),editor.x()+12,editor.y()+47,dim(),false);g.disableScissor();
        }
        face(g,frame.logs(),panel(),prefs.depth);
        Rect logHead=new Rect(frame.logs().x(),frame.logs().y(),frame.logs().width(),Math.min(15,frame.logs().height()));
        button(ctx,new Rect(logHead.x()+2,logHead.y()+1,Math.min(90,Math.max(0,logHead.width()-4)),Math.max(0,logHead.height()-2)),(prefs.logsOpen?"v  ":"^  ")+EditorText.translate("LOGS"),"Expand / collapse logs",()->{prefs.logsOpen=!prefs.logsOpen;clearFocus();});
        if(logHead.width()>225){
            var runs=session.actionContext().runs();
            int lx=logHead.x()+94;
            button(ctx,new Rect(lx,logHead.y()+1,18,13),"<","Previous run",runs::previous);
            button(ctx,new Rect(lx+20,logHead.y()+1,18,13),">","Next run",runs::next);
            String caption=runs.logCaption();
            button(ctx,new Rect(lx+40,logHead.y()+1,Math.max(1,logHead.right()-lx-44),13),font.plainSubstrByWidth(caption,Math.max(1,logHead.right()-lx-52)),"Switch all logs / selected run",runs::toggleAllLogs);
        }
        if(prefs.logsOpen&&frame.logs().height()>20)renderComponent(logs(),new Rect(frame.logs().x()+2,frame.logs().y()+16,Math.max(0,frame.logs().width()-4),Math.max(0,frame.logs().height()-18)),ctx);
        if(helpColumn()&&frame.help().width()>20){
            face(g,frame.help(),panel(),prefs.depth);
            if(settings)renderSettings(ctx);
            else {
                button(ctx,new Rect(frame.help().x()+4,frame.help().y()+2,Math.max(1,frame.help().width()-28),16),"Open window","Open full handbook window",()->dockHelp(false));
                button(ctx,new Rect(frame.help().right()-20,frame.help().y()+2,16,16),"x","Close handbook",this::closeHelp);
                help.setWide(false);
                renderComponent(help,new Rect(frame.help().x()+2,frame.help().y()+20,Math.max(0,frame.help().width()-4),Math.max(0,frame.help().height()-22)),ctx);
            }
        }
        face(g,frame.status(),bg(),false);
        var doc=workspace().editor().document();
        String state=workspace().isSaving()?"SAVING":workspace().isDirty()?"UNSAVED":"READY";
        String runStatus=session.actionContext().runs().status();
        String status="GENESIS / LOGIC   ·   "+(runStatus.isEmpty()?"":runStatus+"   ·   ")+EditorText.translate(TABS[selected])+"   ·   "+EditorText.translate(state);
        g.drawString(font,font.plainSubstrByWidth(status,Math.max(0,frame.status().width()-105)),frame.status().x()+6,frame.status().y()+3,dim(),false);
        if(frame.status().width()>150)g.drawString(font,"Ln "+(doc.cursorLine()+1)+", Col "+(doc.cursorCol()+1),frame.status().right()-95,frame.status().y()+3,dim(),false);
        divider(ctx,frame.files().right(),frame.files().y(),frame.files().bottom(),prefs.filesOpen);
        if(helpColumn())divider(ctx,frame.help().x(),frame.help().y(),frame.help().bottom(),true);
        if(prefs.logsOpen)g.fill(frame.logs().x(),frame.logs().y(),frame.logs().right(),frame.logs().y()+1,drag==3?accent():edge());
        if(helpFloating())renderHelpWindow(ctx);
        for(Button b:buttons)if((!helpFloating()||!helpWindow.contains(ctx.mouseX(),ctx.mouseY())||b.floating)&&b.bounds.contains(ctx.mouseX(),ctx.mouseY())){
            int tw=font.width(b.hint)+10,tx=Math.max(0,Math.min(width-tw,ctx.mouseX()+10)),ty=Math.max(0,Math.min(height-16,ctx.mouseY()+14));
            g.pose().pushPose();g.pose().translate(0,0,200);g.fill(tx,ty,tx+tw,ty+14,raised());g.drawString(font,b.hint,tx+5,ty+3,text(),false);g.pose().popPose();break;
        }
    }
    private void rail(MaredRenderContext ctx){
        var g=ctx.graphics();face(g,frame.rail(),panel(),prefs.depth);
        button(ctx,new Rect(3,3,24,25),"","Return to Genesis",()->{suspended();RuntimeProvider.get().navigation().returnToGenesis();});
        octahedron(g,15,15,8,phase,accent());
        int bottom=Math.max(34,height-28),max=Math.max(0,TABS.length*22-(bottom-34));railScroll=Math.min(railScroll,max);
        g.enableScissor(0,34,frame.rail().right(),bottom);
        for(int i=0;i<TABS.length;i++){int y=34+i*22-railScroll;if(y<34||y+20>bottom)continue;final int index=i;
            button(ctx,new Rect(3,y,24,20),ICONS[i],TABS[i],()->{selected=index;prefs.selectedTab=index;pluginPanelId=null;showLinks=false;links.setAi(index==4);settings=false;clearFocus();});
            if(i==selected)g.fill(0,y+5,2,y+15,accent());
        }g.disableScissor();
        button(ctx,new Rect(3,Math.max(0,height-24),24,20),"*","Logic editor settings",()->{settings=!settings;if(settings)prefs.helpOpen=false;clearFocus();});
    }
    private void toolbar(MaredRenderContext ctx){
        face(ctx.graphics(),frame.toolbar(),panel(),prefs.depth);
        int x=frame.toolbar().x()+6;
        int helpW=Math.min(70,Math.max(40,ctx.font().width(EditorText.translate("Help"))+8));
        // Compact fixed actions; all actions, including plugins, remain in Ctrl+Shift+P.
        for(String[] pair:new String[][]{{"+",StudioActions.FILE_NEW,"New file · Ctrl+N"},{"S",StudioActions.FILE_SAVE,"Save · Ctrl+S"},{">",StudioActions.FILE_RUN,"Run · Ctrl+R"},{"[]",StudioActions.FILE_STOP,"Stop this run · Ctrl+Shift+R"},{"R",StudioActions.FILE_RELOAD_PERSISTENT,"Reload persistent"},{"/",StudioActions.EDIT_FIND,"Find in document · Ctrl+F"},{"V",StudioActions.EDIT_CHECK,"Check MR + MC syntax · F7"}}){
            if(x+20>frame.toolbar().right()-helpW-9)break;
            String action=pair[1];button(ctx,new Rect(x,3,20,16),pair[0],pair[2],()->session.executeAction(action));x+=23;
        }
        if(!session.controller().extensionPanels().isEmpty()&&x+20<frame.toolbar().right()-helpW-9){button(ctx,new Rect(x,3,20,16),"P","Next plugin panel / return to editor",this::nextPluginPanel);x+=23;}
        if(x+20<frame.toolbar().right()-helpW-9){button(ctx,new Rect(x,3,20,16),"L","Script exports and usages",()->{showLinks=!showLinks;pluginPanelId=null;links.setAi(false);if(selected==4){selected=1;prefs.selectedTab=1;}clearFocus();});x+=23;}
        var plugin=pluginPanel();
        String caption="LOGIC / "+(plugin==null?EditorText.translate(TABS[selected]).toUpperCase(Locale.ROOT):plugin.title());
        if(x+12+ctx.font().width(caption)<frame.toolbar().right()-helpW-9)ctx.graphics().drawString(ctx.font(),caption,x+6,7,dim(),false);
        button(ctx,new Rect(frame.toolbar().right()-helpW-5,3,helpW,16),"Help", "Command handbook",()->{prefs.helpOpen=!prefs.helpOpen;settings=false;clearFocus();});
    }
    private void renderSettings(MaredRenderContext ctx){
        int x=frame.help().x()+8,y=frame.help().y()+8,w=Math.max(1,frame.help().width()-16);
        ctx.graphics().drawString(ctx.font(),EditorText.translate("EDITOR SETTINGS"),x,y,text(),false);y+=22-settingsScroll;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Theme")+": "+com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().displayName,"Change interface theme",this::nextTheme);y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Background")+": "+EditorText.translate(prefs.background?"ON":"OFF"),"Genesis background",()->prefs.background=!prefs.background);y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Animation")+": "+EditorText.translate(prefs.animation?"ON":"OFF"),"Also respects Reduced Motion",()->prefs.animation=!prefs.animation);y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),EditorText.translate("Pixel depth")+": "+EditorText.translate(prefs.depth?"ON":"OFF"),"Bevelled panel faces",()->prefs.depth=!prefs.depth);y+=26;
        settingsButton(ctx,new Rect(x,y,w,18),"Reset panel sizes","Restore compact layout",()->{prefs.filesRatio=.18;prefs.helpRatio=.29;prefs.logsRatio=.24;prefs.filesOpen=true;prefs.logsOpen=false;prefs.helpWindowX=.18;prefs.helpWindowY=.10;prefs.helpWindowW=.70;prefs.helpWindowH=.80;});y+=22;
        settingsButton(ctx,new Rect(x,y,w,18),"All settings…","Open existing settings",()->session.executeAction(StudioActions.VIEW_SETTINGS));
    }
    private void nextTheme(){
        var registry=com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.all();
        if(registry.isEmpty())return;
        int current=registry.indexOf(com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active());
        var next=registry.get((current+1)%registry.size());
        com.fixmer.mared.MaredSettings.setThemeId(next.id);
        com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.setActive(next);
        com.fixmer.mared.MaredSettings.save();
    }
    private void dockHelp(boolean docked){
        prefs.helpDocked=docked;prefs.helpOpen=true;settings=false;help.cancelInteraction();clearFocus();
    }
    private void closeHelp(){prefs.helpOpen=false;help.cancelInteraction();clearFocus();}
    private void beginWindowDrag(int kind,double mx,double my){
        clearFocus();help.cancelInteraction();drag=kind;dragWindow=helpWindow;dragMouseX=mx;dragMouseY=my;
    }
    private void renderHelpWindow(MaredRenderContext ctx){
        Rect r=helpWindow;var g=ctx.graphics();
        g.pose().pushPose();g.pose().translate(0,0,150);
        buildingHelpWindow=true;
        try {
            EditorDialogStyle.active(LOGIC).panel(g,r.x(),r.y(),r.width(),r.height());
            g.drawString(ctx.font(),ctx.font().plainSubstrByWidth(EditorText.translate("Command handbook"),Math.max(0,r.width()-141)),r.x()+10,r.y()+10,text(),false);
            button(ctx,new Rect(r.right()-125,r.y()+3,100,16),"Dock right","Show compact handbook panel",()->dockHelp(true));
            button(ctx,new Rect(r.right()-21,r.y()+3,16,16),"x","Close handbook",this::closeHelp);
            help.setWide(true);
            renderComponent(help,new Rect(r.x()+3,r.y()+23,Math.max(0,r.width()-6),Math.max(0,r.height()-35)),ctx);
            g.fill(r.right()-11,r.bottom()-4,r.right()-3,r.bottom()-3,dim());
            g.fill(r.right()-7,r.bottom()-8,r.right()-3,r.bottom()-7,dim());
        } finally { buildingHelpWindow=false;g.pose().popPose(); }
    }
    private void settingsButton(MaredRenderContext ctx,Rect rect,String label,String hint,Runnable action){
        if(rect.y()>=frame.help().y()+26&&rect.bottom()<=frame.help().bottom()-2)button(ctx,rect,label,hint,action);
    }
    private void button(MaredRenderContext ctx,Rect r,String label,String hint,Runnable action){
        if(r.width()<1||r.height()<1)return;
        label=EditorText.translate(label);hint=EditorText.translate(hint);
        boolean hover=r.contains(ctx.mouseX(),ctx.mouseY());if(hover)face(ctx.graphics(),r,raised(),prefs.depth);
        String text=ctx.font().plainSubstrByWidth(label,Math.max(0,r.width()-4));ctx.graphics().drawString(ctx.font(),text,r.x()+(r.width()-ctx.font().width(text))/2,r.y()+(r.height()-8)/2,hover?text():dim(),false);
        buttons.add(new Button(r,label,hint,action,buildingHelpWindow));
    }
    private void renderComponent(MaredComponent c,Rect r,MaredRenderContext ctx){
        if(r.width()<1||r.height()<1)return;
        c.layout(new MaredBounds(r.x(),r.y(),r.width(),r.height()));c.mouseMoved(ctx.mouseX(),ctx.mouseY());
        ctx.graphics().enableScissor(r.x(),r.y(),r.right(),r.bottom());c.render(ctx);ctx.graphics().disableScissor();
    }
    private void divider(MaredRenderContext ctx,int x,int y,int bottom,boolean resizable){boolean hover=resizable&&Math.abs(ctx.mouseX()-x)<=2&&ctx.mouseY()>=y&&ctx.mouseY()<bottom;ctx.graphics().fill(x,y,x+1,bottom,hover?accent():edge());}
    private void clearFocus(){captured=null;session.pointerManager().releaseAll();session.focusManager().clear();}
    private List<MaredComponent> visible(){var result=new ArrayList<MaredComponent>(4);if(prefs.filesOpen)result.add(files());var plugin=pluginPanel();if(showLinks||selected==4)result.add(links);else if(plugin!=null)result.add(plugin.component());else if(selected<2)result.add(workspace());if(prefs.logsOpen)result.add(logs());if(prefs.helpOpen&&!settings)result.add(help);return result;}
    public boolean click(double mx,double my,int button){
        if(frame==null)return false;
        if(session.overlayManager().mouseClicked(mx,my,button)||session.overlayManager().shouldBlockGenericInput())return true;
        if(helpFloating()&&helpWindow!=null&&helpWindow.contains(mx,my)){
            if(button==0){
                for(int i=buttons.size()-1;i>=0;i--){Button b=buttons.get(i);if(b.floating&&b.bounds.contains(mx,my)){b.action.run();return true;}}
                if(mx>=helpWindow.right()-12&&my>=helpWindow.bottom()-12){beginWindowDrag(5,mx,my);return true;}
                if(my<helpWindow.y()+22){beginWindowDrag(4,mx,my);return true;}
            }
            if(help.mouseClicked(mx,my,button))captured=help;
            return true;
        }
        if(button==0){
            // Header buttons have priority over neighbouring resize handles.
            for(Button b:buttons)if(!b.floating&&b.bounds.contains(mx,my)){b.action.run();return true;}
            if(my>=frame.files().y()&&my<frame.files().bottom()){
                if(prefs.filesOpen&&Math.abs(mx-frame.files().right())<=3){drag=1;clearFocus();return true;}
                if(helpColumn()&&Math.abs(mx-frame.help().x())<=3){drag=2;clearFocus();return true;}
            }
            if(prefs.logsOpen&&mx>=frame.logs().x()&&mx<frame.logs().right()&&Math.abs(my-frame.logs().y())<=3){drag=3;clearFocus();return true;}
        }
        for(var c:visible())if(!(c==help&&helpFloating())&&c.mouseClicked(mx,my,button)){captured=c;return true;}
        clearFocus();return true;
    }
    public boolean drag(double mx,double my,int button,double dx,double dy){
        if(session.overlayManager().shouldBlockGenericInput())return true;
        if(drag==4||drag==5){
            double offsetX=mx-dragMouseX,offsetY=my-dragMouseY;
            Rect next=HelpWindowGeometry.calculate(width,height,
                drag==4?(dragWindow.x()+offsetX)/Math.max(1,width):prefs.helpWindowX,
                drag==4?(dragWindow.y()+offsetY)/Math.max(1,height):prefs.helpWindowY,
                drag==5?(dragWindow.width()+offsetX)/Math.max(1,width):prefs.helpWindowW,
                drag==5?(dragWindow.height()+offsetY)/Math.max(1,height):prefs.helpWindowH);
            prefs.helpWindowX=next.x()/(double)Math.max(1,width);prefs.helpWindowY=next.y()/(double)Math.max(1,height);
            prefs.helpWindowW=next.width()/(double)Math.max(1,width);prefs.helpWindowH=next.height()/(double)Math.max(1,height);
            return true;
        }
        if(drag!=0){
            if(drag==1)prefs.filesRatio=Math.max(.08,Math.min(.6,(mx-frame.rail().width())/Math.max(1,width-frame.rail().width())));
            if(drag==2)prefs.helpRatio=Math.max(.08,Math.min(.6,(width-mx)/Math.max(1,width-frame.rail().width())));
            if(drag==3)prefs.logsRatio=Math.max(.08,Math.min(.65,(frame.status().y()-my)/Math.max(1,frame.status().y()-frame.toolbar().height())));
            return true;
        }
        return captured!=null&&captured.mouseDragged(mx,my,button,dx,dy);
    }
    public boolean release(double mx,double my,int button){boolean handled=drag!=0;drag=0;if(captured!=null){handled|=captured.mouseReleased(mx,my,button);captured=null;}return handled;}
    public boolean scroll(double mx,double my,double dx,double dy){
        if(session.overlayManager().mouseScrolled(mx,my,dx,dy)||session.overlayManager().shouldBlockGenericInput())return true;
        if(helpFloating()&&helpWindow!=null&&helpWindow.contains(mx,my)){help.mouseScrolled(mx,my,dx,dy);return true;}
        if(frame!=null&&frame.rail().contains(mx,my)){railScroll=Math.max(0,railScroll+(dy<0?22:dy>0?-22:0));return true;}
        if(frame!=null&&settings&&frame.help().contains(mx,my)){settingsScroll=Math.max(0,Math.min(Math.max(0,197-frame.help().height()),settingsScroll+(dy<0?22:dy>0?-22:0)));return true;}
        for(var c:visible())if(c.mouseScrolled(mx,my,dx,dy))return true;return false;
    }
    public boolean key(int key,int scan,int mods){
        if(session.overlayManager().keyPressed(key,scan,mods)||session.overlayManager().shouldBlockGenericInput())return true;
        if(key==80&&(mods&6)==6){nextPluginPanel();return true;}
        if(key==256&&helpFloating()){
            if(help.keyPressed(key,scan,mods))return true;
            closeHelp();return true;
        }
        if(helpFloating()&&key!=295&&session.focusManager().owner()==help)return help.keyPressed(key,scan,mods);
        if(key==256){var owner=session.focusManager().owner();return owner!=null&&visible().contains(owner)&&owner.keyPressed(key,scan,mods);}
        if((showLinks||selected==4)&&session.focusManager().owner()==links&&links.keyPressed(key,scan,mods))return true;
        if(session.actions().executeByKey(key,mods,session.actionContext()))return true;
        if(key==295){var focusable=visible().stream().filter(MaredComponent::focusable).toList();if(!focusable.isEmpty()){int i=focusable.indexOf(session.focusManager().owner());session.focusManager().request(focusable.get((i+1)%focusable.size()));return true;}}
        MaredComponent owner=session.focusManager().owner();if(owner!=null&&visible().contains(owner))return owner.keyPressed(key,scan,mods);return false;
    }
    public boolean character(char c,int mods){if(session.overlayManager().charTyped(c,mods)||session.overlayManager().shouldBlockGenericInput())return true;var owner=session.focusManager().owner();return owner!=null&&visible().contains(owner)&&owner.charTyped(c,mods);}
}
