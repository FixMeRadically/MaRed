package com.fixmer.mared.technology.server.setup;

import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class RemoteInstallScreen70 extends MaredScreen {
    private final Screen previous;private String mode="ONLINE";private Button auth,prepare,restart,cancel,reports;private int left,bodyWidth,scroll,maxScroll;
    public RemoteInstallScreen70(Screen previous){super(tr("title"));this.previous=previous;}
    private static Component tr(String key,Object...args){return Component.translatable("mared.remote70."+key,args);}
    private Button button(String key,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){bodyWidth=Math.max(240,Math.min(620,width-24));left=(width-bodyWidth)/2;int bw=(bodyWidth-10)/3;
        auth=button("mode."+mode,left,35,bodyWidth,()->{mode=mode.equals("ONLINE")?"LOCAL_OFFLINE":"ONLINE";auth.setMessage(tr("mode."+mode));});
        prepare=button("prepare",left,62,bw,()->RemoteApplyService70.start(mode));restart=button("restart",left+bw+5,62,bw,()->RemoteApplyService70.command("ARM"));cancel=button("cancel",left+2*(bw+5),62,bw,()->RemoteApplyService70.command("CANCEL"));
        reports=button("reports",left,height-27,(bodyWidth-5)/2,()->{var p=RemoteApplyService70.reports();if(p!=null)net.minecraft.Util.getPlatform().openFile(p.toFile());});button("back",left+(bodyWidth+5)/2,height-27,(bodyWidth-5)/2,this::onClose);tick();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(previous);}
    @Override public void tick(){boolean busy=RemoteApplyService70.busy();String phase=RemoteApplyService70.phase();auth.active=prepare.active=!busy;restart.active=busy&&phase.equals("READY");cancel.active=busy&&Set.of("VALIDATING","PREPARING","READY","ARMING").contains(phase);reports.active=RemoteApplyService70.reports()!=null;}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(y>92&&y<height-36){scroll=Math.max(0,Math.min(maxScroll,scroll+(int)(-dy*25)));return true;}return super.mouseScrolled(x,y,dx,dy);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fillGradient(0,0,width,height,0xFF11141B,0xFF1A1D29);g.drawCenteredString(font,title,width/2,12,0xFFF0F1FC);List<Component> content=new ArrayList<>();content.add(tr("phase."+RemoteApplyService70.phase()));
        JsonObject state=RemoteApplyService70.state();if(state.has("failureReason")&&!state.get("failureReason").getAsString().isBlank())content.add(Component.literal(state.get("failureReason").getAsString()));
        try{JsonObject d=state.getAsJsonObject("delta");if(d!=null)for(String group:List.of("added","changed","removed")){var names=d.getAsJsonArray(group);if(names==null||names.size()==0)continue;content.add(tr(group,names.size()));for(int i=0;i<Math.min(256,names.size());i++)content.add(Component.literal(names.get(i).getAsString()));}}catch(RuntimeException invalid){}
        content.add(tr("scope"));List<net.minecraft.util.FormattedCharSequence> lines=new ArrayList<>();for(Component c:content)lines.addAll(font.split(c,bodyWidth-6));int row=font.lineHeight+3;maxScroll=Math.max(0,lines.size()*row-(height-132));scroll=Math.min(scroll,maxScroll);int y=96-scroll;
        g.enableScissor(left,94,left+bodyWidth,height-37);for(var line:lines){if(y+font.lineHeight>=94&&y<height-37)g.drawString(font,line,left,y,0xFFCCD0DC,false);y+=row;}g.disableScissor();super.render(g,mx,my,partial);
    }
}
