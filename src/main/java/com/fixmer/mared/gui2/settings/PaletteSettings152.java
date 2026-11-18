package com.fixmer.mared.gui2.settings;
import com.fixmer.genesis.technology.creation.CreationCatalog145.Direction;
import com.fixmer.mared.MaredLang;
import com.fixmer.mared.technology.editor.*;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.*;import java.util.*;

/** Independent category accents and optional surface colors; normal Settings Save commits the draft. */
public final class PaletteSettings152 implements MaredSettingsTab {
    private record Row(String key,String label,Map<String,Integer> colors,int fallback){}
    private final WorkbenchPreferences draft;private final MaredSettingsScreen parent;private int scroll;
    public PaletteSettings152(WorkbenchPreferences draft,MaredSettingsScreen parent){this.draft=draft;this.parent=parent;}
    public String id(){return "palette152";}public String displayName(){return MaredLang.get("mared.palette152.title");}
    public int accentColor(){return GenesisEditorVisuals.accent();}
    private List<Row> rows(){var result=new ArrayList<Row>();for(var direction:Direction.values())result.add(new Row(direction.id(),MaredLang.get(direction.key()),draft.directionColors152,direction.color&0xffffff));
        var theme=com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        String[] keys={"background","panel","raised","text","muted","border"};int[] defaults={theme.bgScreen,theme.bgPanel,theme.bgPanelRaised,theme.text,theme.textDim,theme.border};
        for(int i=0;i<keys.length;i++)result.add(new Row(keys[i],MaredLang.get("mared.palette152."+keys[i]),draft.surfaceColors152,defaults[i]&0xffffff));return result;}
    public void render(GuiGraphics g,Font font,int x,int y,int w,int h,SettingsContext context,int mx,int my){
        var rows=rows();scroll=Math.max(0,Math.min(scroll,Math.max(0,(rows.size()+1)*28-h)));
        for(int i=0;i<=rows.size();i++){int yy=y+i*28-scroll;if(yy<y||yy+24>y+h)continue;
            g.fill(x,yy,x+w,yy+24,GenesisEditorVisuals.raised());
            if(i==rows.size()){g.drawString(font,MaredLang.get("mared.palette152.reset"),x+8,yy+8,GenesisEditorVisuals.text(),false);continue;}
            Row row=rows.get(i);int rgb=row.colors.getOrDefault(row.key,row.fallback);g.fill(x+6,yy+5,x+20,yy+19,0xff000000|rgb);
            g.drawString(font,font.plainSubstrByWidth(row.label+"  #"+String.format("%06X",rgb),Math.max(1,w-34)),x+28,yy+8,GenesisEditorVisuals.text(),false);
        }
    }
    public boolean mouseClicked(double mx,double my,int button,int x,int y,int w,int h,SettingsContext context){
        if(button!=0||mx<x||mx>=x+w||my<y||my>=y+h)return false;var rows=rows();int i=(int)(my-y+scroll)/28;
        if(i==rows.size()){draft.directionColors152.clear();draft.surfaceColors152.clear();return true;}
        if(i>=0&&i<rows.size()){Row row=rows.get(i);parent.chooseColor152(row.colors.getOrDefault(row.key,row.fallback),row.label,rgb->row.colors.put(row.key,rgb));return true;}
        return false;
    }
    public boolean mouseScrolled(double mx,double my,double dy,int x,int y,int w,int h,SettingsContext context){if(mx<x||mx>=x+w||my<y||my>=y+h)return false;scroll=Math.max(0,Math.min(Math.max(0,(rows().size()+1)*28-h),scroll-(int)(dy*28)));return true;}
}
