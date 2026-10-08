package com.fixmer.genesis.technology.editor;

/** Pure, bounded editor geometry in logical pixels; independent of Minecraft and rendering. */
public final class EditorLayout {
    private EditorLayout() {}
    public record Rect(int x,int y,int width,int height) {
        public Rect { if(width<0||height<0)throw new IllegalArgumentException("Negative size"); }
        public int right(){return x+width;} public int bottom(){return y+height;}
        public boolean contains(double x,double y){return x>=this.x&&x<right()&&y>=this.y&&y<bottom();}
        public Rect inset(int amount){if(amount<0)throw new IllegalArgumentException("Negative inset");int ax=Math.min(amount,width/2),ay=Math.min(amount,height/2);return new Rect(x+ax,y+ay,width-ax*2,height-ay*2);}
    }
    public record Frame(Rect rail,Rect toolbar,Rect files,Rect editor,Rect logs,Rect help,Rect status) {}
    public static Frame calculate(int width,int height,double filesRatio,double helpRatio,double logsRatio,boolean filesOpen,boolean helpOpen,boolean logsOpen) {
        int w=Math.max(0,width),h=Math.max(0,height),rail=Math.min(30,w),top=Math.min(22,h),footer=Math.min(13,h-top);
        int bodyH=h-top-footer,available=w-rail;
        int sideBudget=Math.max(0,available-Math.min(140,available));
        int fw=filesOpen?bounded(available*finite(filesRatio,.18),90,260):Math.min(14,sideBudget);
        int hw=helpOpen?bounded(available*finite(helpRatio,.29),160,400):0;
        if(fw+hw>sideBudget){int total=fw+hw;fw=(int)((long)fw*sideBudget/Math.max(1,total));hw=sideBudget-fw;}
        int centerW=available-fw-hw;
        int logH=logsOpen?Math.min(Math.max(0,bodyH-55),bounded(bodyH*finite(logsRatio,.24),70,300)):Math.min(15,bodyH);
        return new Frame(new Rect(0,0,rail,h),new Rect(rail,0,available,top),
            new Rect(rail,top,fw,bodyH),new Rect(rail+fw,top,centerW,bodyH-logH),
            new Rect(rail+fw,top+bodyH-logH,centerW,logH),new Rect(w-hw,top,hw,bodyH),
            new Rect(rail,h-footer,available,footer));
    }
    private static double finite(double v,double fallback){return Double.isFinite(v)?Math.max(0,Math.min(.8,v)):fallback;}
    private static int bounded(double v,int min,int max){return Math.max(min,Math.min(max,(int)v));}
}
