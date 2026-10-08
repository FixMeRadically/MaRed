package com.fixmer.mared.technology.editor;

import com.fixmer.genesis.technology.editor.EditorLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel faces and a projected rotating octahedron: no textures, shaders or per-frame asset loading. */
public final class GenesisEditorVisuals {
    private GenesisEditorVisuals() {}
    public static final int BG=0xFF090A0D,PANEL=0xFF111216,RAISED=0xFF191A20,EDGE=0xFF2B2C34,TEXT=0xFFD6D4DC,DIM=0xFF8C8995;
    // Same hue as Genesis NodeRegistry LOGIC. Full accent only on tiny markers.
    public static final int LOGIC=0xFFAA55FF,ACCENT=0xFF9F80BB;
    private static com.fixmer.mared.gui2.framework.theme.MaredTheme cachedTheme;
    private static EditorDialogStyle cachedStyle;
    private static EditorDialogStyle style(){
        var theme=com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active();
        if(theme!=cachedTheme){cachedTheme=theme;cachedStyle=EditorDialogStyle.from(theme,LOGIC);}
        return cachedStyle;
    }
    public static int bg(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().bgScreen;}
    public static int panel(){return style().panel();}
    public static int raised(){return style().raised();}
    public static int edge(){return style().border();}
    public static int text(){return style().text();}
    public static int dim(){return style().muted();}
    public static int accent(){return style().accent();}
    public static int sunken(){return style().input();}
    public static int hover(){return style().hover();}
    public static int selected(){return style().selection();}
    public static int danger(){return style().danger();}
    public static int warn(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().warn;}
    public static int success(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().success;}
    public static int track(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().scrollTrack;}
    public static int thumb(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().scrollThumb;}
    public static int quiet(){return com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry.active().divider;}
    private static final double[][] V={{0,-1,0},{1,0,0},{0,0,1},{-1,0,0},{0,0,-1},{0,1,0}};
    private static final int[][] E={{0,1},{0,2},{0,3},{0,4},{5,1},{5,2},{5,3},{5,4},{1,2},{2,3},{3,4},{4,1}};
    public static void face(GuiGraphics g,Rect r,int color,boolean depth){
        if(r.width()==0||r.height()==0)return;
        g.fill(r.x(),r.y(),r.right(),r.bottom(),color);
        g.fill(r.x(),r.y(),r.right(),r.y()+1,edge());
        g.fill(r.x(),r.y(),r.x()+1,r.bottom(),edge());
        if(depth){g.fill(r.right()-1,r.y()+1,r.right(),r.bottom(),quiet());g.fill(r.x()+1,r.bottom()-1,r.right(),r.bottom(),quiet());}
    }
    public static void octahedron(GuiGraphics g,int cx,int cy,int radius,double phase,int color){
        double c=Math.cos(phase),s=Math.sin(phase);
        for(int[] edge:E){double[] a=V[edge[0]],b=V[edge[1]];
            line(g,cx+(int)((a[0]*c+a[2]*s)*radius),cy+(int)((a[1]*.78+(a[2]*c-a[0]*s)*.3)*radius),
                cx+(int)((b[0]*c+b[2]*s)*radius),cy+(int)((b[1]*.78+(b[2]*c-b[0]*s)*.3)*radius),color);}
    }
    private static void line(GuiGraphics g,int x,int y,int tx,int ty,int color){
        int dx=Math.abs(tx-x),sx=x<tx?1:-1,dy=-Math.abs(ty-y),sy=y<ty?1:-1,error=dx+dy;
        for(;;){g.fill(x,y,x+1,y+1,color);if(x==tx&&y==ty)return;int e=2*error;if(e>=dy){error+=dy;x+=sx;}if(e<=dx){error+=dx;y+=sy;}}
    }
}
