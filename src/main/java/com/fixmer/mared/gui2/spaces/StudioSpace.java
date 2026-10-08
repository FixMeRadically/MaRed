package com.fixmer.mared.gui2.spaces;

import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.navigation.*;
import com.fixmer.mared.gui2.runtime.RuntimeProvider;
import com.fixmer.mared.gui2.studio.StudioSession;
import com.fixmer.mared.technology.editor.GenesisEditorVisuals;

/** Thin host; the runtime session owns documents, workbench state and subscriptions. */
public class StudioSpace implements MaredSpace,Disposable {
    private final SpaceId id;
    private StudioSession session;
    public StudioSpace(){this(SpaceId.STUDIO);}
    protected StudioSpace(SpaceId id){this.id=id;}
    @Override public SpaceId id(){return id;}
    @Override public String titleKey(){return "mared.space."+id.id();}
    @Override public int accentColor(){return GenesisEditorVisuals.LOGIC;}
    @Override public void onEnter(SpaceCameraState camera){if(RuntimeProvider.isInstalled())session=RuntimeProvider.get().session();}
    @Override public void onExit(){if(session!=null)session.workbench().suspended();session=null;}
    @Override public void dispose(){onExit();}
    @Override public void tick(float dt){if(session!=null)session.workbench().tick(dt);}
    @Override public void render(MaredRenderContext ctx){
        if(session==null||!session.isInitialized())return;
        session.workbench().render(ctx);
        ctx.graphics().pose().pushPose();
        ctx.graphics().pose().translate(0,0,300);
        try { session.overlayManager().render(ctx.graphics(),ctx.font(),ctx.width(),ctx.height(),ctx.mouseX(),ctx.mouseY()); }
        finally { ctx.graphics().pose().popPose(); }
    }
    @Override public boolean mouseClicked(double x,double y,int b){return session!=null&&session.workbench().click(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return session!=null&&session.workbench().drag(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){return session!=null&&session.workbench().release(x,y,b);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){return session!=null&&session.workbench().scroll(x,y,dx,dy);}
    @Override public boolean keyPressed(int key,int scan,int mods){return session!=null&&session.workbench().key(key,scan,mods);}
    @Override public boolean charTyped(char c,int mods){return session!=null&&session.workbench().character(c,mods);}
}
