package com.fixmer.mared.technology.editor;

import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.fixmer.mared.gui2.development.DevelopmentHub129;
import com.fixmer.mared.technology.server.setup.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Hosts the existing server forms directly in the workbench; no second navigation hub. */
public final class DevelopmentPane151 extends MaredComponent {
    private Screen page;
    private String section="";
    private int width,height,scroll;
    public void select(String id){if(!section.equals(id)){close();section=id;}}
    public void close(){if(page!=null)page.removed();page=null;width=height=scroll=0;}
    private void show(Screen value){
        if(page!=null)page.removed();page=value;
        if(value instanceof MaredScreen nativePage)nativePage.embedded151(Minecraft.getInstance().screen);
        value.init(Minecraft.getInstance(),Math.max(1,width),Math.max(1,height));
    }
    private void prepare(){
        int w=bounds.width(),h=Math.max(300,bounds.height());if(w<=0||bounds.height()<=0)return;
        scroll=Math.max(0,Math.min(scroll,h-bounds.height()));
        boolean resize=w!=width||h!=height;width=w;height=h;
        if(page==null){
            Screen root=Minecraft.getInstance().screen;
            if(section.equals("server")||section.equals("server_configs156")||section.equals("server_settings156"))show(new ServerWorkspaceScreen156(root,section));
            else if(section.equals("supervisor"))show(new DevelopmentHub129(root,DevelopmentHub129.Section.SUPERVISOR));
            else show(new ServerToolsScreen116(root,Minecraft.getInstance().getConnection()!=null));
        }else if(resize){page.removed();page.resize(Minecraft.getInstance(),width,height);}
    }
    public void tick(){if(page!=null)page.tick();}
    @Override public boolean focusable(){return true;}
    @Override protected void safeRender(MaredRenderContext context){
        prepare();if(page==null)return;
        var g=context.graphics();g.pose().pushPose();g.pose().translate(bounds.x(),bounds.y()-scroll,0);
        try(var clip155=com.fixmer.genesis.technology.editor.EmbeddedClip155.enter(bounds.x(),bounds.y()-scroll)){page.renderWithTooltip(g,contains(context.mouseX(),context.mouseY())?context.mouseX()-bounds.x():-10000,contains(context.mouseX(),context.mouseY())?context.mouseY()-bounds.y()+scroll:-10000,0);}finally{g.pose().popPose();}
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(page==null||!contains(x,y))return false;requestFocus();page.mouseClicked(x-bounds.x(),y-bounds.y()+scroll,button);return true;}
    @Override public boolean mouseReleased(double x,double y,int button){return page!=null&&page.mouseReleased(x-bounds.x(),y-bounds.y()+scroll,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return page!=null&&page.mouseDragged(x-bounds.x(),y-bounds.y()+scroll,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(page==null||!contains(x,y))return false;
        if(height>bounds.height()){scroll=Math.max(0,Math.min(height-bounds.height(),scroll-(int)(dy*26)));return true;}
        return page.mouseScrolled(x-bounds.x(),y-bounds.y()+scroll,dx,dy);
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(page==null)return false;
        if(height>bounds.height()&&(key==266||key==267)){scroll=Math.max(0,Math.min(height-bounds.height(),scroll+(key==267?1:-1)*Math.max(1,bounds.height()-24)));return true;}
        boolean handled=page.keyPressed(key,scan,mods);
        if(key==258&&page.getFocused() instanceof net.minecraft.client.gui.components.AbstractWidget widget){
            if(widget.getY()<scroll)scroll=widget.getY();
            else if(widget.getY()+widget.getHeight()>scroll+bounds.height())scroll=widget.getY()+widget.getHeight()-bounds.height();
            scroll=Math.max(0,Math.min(height-bounds.height(),scroll));
        }
        return handled;
    }
    @Override public boolean charTyped(char character,int mods){return page!=null&&page.charTyped(character,mods);}
    @Override public void onFocusLost(){if(page!=null)page.setFocused(null);}
}
