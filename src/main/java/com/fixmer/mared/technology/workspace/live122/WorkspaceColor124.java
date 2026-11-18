package com.fixmer.mared.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.live122.*;
import com.fixmer.mared.gui2.framework.screen.MaredScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.function.IntConsumer;
/** Paint-style popup: saturation/value field, hue bar, RGB and hex entry, live swatch. */
public final class WorkspaceColor124 extends MaredScreen {
    private final net.minecraft.client.gui.screens.Screen previous;private final IntConsumer accepted;private final Runnable before152,back152,tick152;
    private final EditBox[] channels=new EditBox[3];private EditBox hex;private Button save;private float hue,saturation,value;private int rgb,x,y,size,drag;private boolean updating;
    WorkspaceColor124(WorkspaceScreen122 previous,int rgb,IntConsumer accepted){this(previous,rgb,accepted,previous::attachClient,previous::backFromPersonal131,previous::tickClient,WorkspaceScreen122.tr("color"));}
    public WorkspaceColor124(net.minecraft.client.gui.screens.Screen previous,int rgb,IntConsumer accepted,Runnable before,Runnable back,Runnable tick,Component title){super(title);this.previous=previous;this.accepted=accepted;before152=before;back152=back;tick152=tick;setColor(rgb);}
    private void setColor(int color){rgb=color;float[] hsv=WorkspaceColorMath131.hsv(rgb);hue=hsv[0];saturation=hsv[1];value=hsv[2];}
    @Override protected void init(){before152.run();size=Math.max(60,Math.min(180,height-130));x=(width-Math.min(430,width-20))/2;y=Math.max(30,(height-size-100)/2);int right=x+size+42,ew=Math.max(40,Math.min(145,width-right-15));
        for(int i=0;i<3;i++){final int channel=i;channels[i]=addRenderableWidget(new EditBox(font,right+20,y+i*28,Math.max(25,ew-20),20,Component.literal("RGB")));channels[i].setMaxLength(3);channels[i].setResponder(text->{if(updating)return;try{int r=Integer.parseInt(channels[0].getValue()),g=Integer.parseInt(channels[1].getValue()),b=Integer.parseInt(channels[2].getValue());if((r|g|b)<0||Math.max(r,Math.max(g,b))>255)return;setColor(r<<16|g<<8|b);sync();}catch(Exception ignored){}});}
        hex=addRenderableWidget(new EditBox(font,right,y+88,ew,20,WorkspaceScreen122.tr("color_hex")));hex.setMaxLength(7);hex.setResponder(text->{if(!updating)try{setColor(WorkspaceAppearance124.parse(text));sync();}catch(Exception ignored){}});
        save=addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("color_save"),b->{accepted.accept(rgb);back152.run();}).bounds(x,y+size+56,Math.max(80,(size+42+ew-4)/2),22).build());
        addRenderableWidget(WorkspaceButton131.builder(WorkspaceScreen122.tr("back"),b->onClose()).bounds(x+(size+42+ew)/2+2,y+size+56,Math.max(80,(size+42+ew-4)/2),22).build());sync();
    }
    private void sync(){if(hex==null)return;updating=true;try{hex.setValue(String.format("#%06X",rgb));for(int i=0;i<3;i++)channels[i].setValue(Integer.toString(rgb>>(16-8*i)&255));}finally{updating=false;}}
    private void pick(double mx,double my){if(drag==1){saturation=(float)Math.max(0,Math.min(1,(mx-x)/(size-1)));value=1-(float)Math.max(0,Math.min(1,(my-y)/(size-1)));}else if(drag==2)hue=(float)Math.max(0,Math.min(.999999,(my-y)/(size-1)));rgb=WorkspaceColorMath131.rgb(hue,saturation,value);sync();}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&mx>=x&&mx<x+size+28&&my>=y&&my<y+size){drag=mx<x+size?1:mx>=x+size+10?2:0;if(drag!=0){pick(mx,my);return true;}}if(button==0&&my>=y+size+14&&my<y+size+38){int[] colors={0xFFFFFF,0x121212,0xFF5252,0xFFAD42,0xFFE45C,0x73D47A,0x40CCD4,0x639BFF,0xA879F5,0xF080C8};int index=(int)(mx-x)/28;if(mx>=x&&index<colors.length){setColor(colors[index]);sync();return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(drag!=0){pick(mx,my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){drag=0;return super.mouseReleased(mx,my,button);}
    @Override public void tick(){tick152.run();if(save!=null)try{WorkspaceAppearance124.parse(hex.getValue());for(var field:channels){int channel=Integer.parseInt(field.getValue());if(channel<0||channel>255)throw new IllegalArgumentException();}save.active=!(previous instanceof WorkspaceScreen122 workspace&&workspace.colorSaving152());}catch(Exception invalid){save.active=false;}}@Override public void onClose(){back152.run();}@Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,(0xEF000000|com.fixmer.mared.technology.editor.GenesisEditorVisuals.bg()&0xFFFFFF));g.fill(x-14,y-34,Math.min(width-4,x+size+225),y+size+90,com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised());g.drawString(font,title,x,y-22,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(),false);
        for(int yy=0;yy<size;yy+=2)for(int xx=0;xx<size;xx+=2)g.fill(x+xx,y+yy,x+Math.min(size,xx+2),y+Math.min(size,yy+2),0xFF000000|WorkspaceColorMath131.rgb(hue,xx/(float)(size-1),1-yy/(float)(size-1)));
        for(int yy=0;yy<size;yy++)g.fill(x+size+10,y+yy,x+size+28,y+yy+1,0xFF000000|WorkspaceColorMath131.rgb(yy/(float)size,1,1));
        g.renderOutline(x+(int)(saturation*(size-1))-4,y+(int)((1-value)*(size-1))-4,9,9,0xFF111111);g.renderOutline(x+(int)(saturation*(size-1))-3,y+(int)((1-value)*(size-1))-3,7,7,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());g.renderOutline(x+size+8,y+(int)(hue*(size-1))-2,22,5,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
        if(previous instanceof WorkspaceScreen122 workspace&&workspace.colorError152())g.drawString(font,WorkspaceScreen122.tr("color_error152"),x,y+size+43,0xffff8888,false);
        int right=x+size+42;for(int i=0;i<3;i++)g.drawString(font,"RGB".substring(i,i+1),right,y+i*28+6,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text(),false);g.fill(right,y+116,Math.min(width-15,right+140),y+148,0xFF000000|rgb);
        int[] palette={0xFFFFFF,0x121212,0xFF5252,0xFFAD42,0xFFE45C,0x73D47A,0x40CCD4,0x639BFF,0xA879F5,0xF080C8};for(int i=0;i<palette.length;i++){g.fill(x+i*28,y+size+14,x+i*28+24,y+size+38,0xFF000000|palette[i]);g.renderOutline(x+i*28,y+size+14,24,24,0xFFB9C0CB);}super.render(g,mx,my,dt);
    }
}
