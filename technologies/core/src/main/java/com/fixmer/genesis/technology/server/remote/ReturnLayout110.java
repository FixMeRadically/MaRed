package com.fixmer.genesis.technology.server.remote;
/** Integer layout keeps core, wrapped status and controls apart at high GUI scales. */
public record ReturnLayout110(int coreX,int coreY,int radius,int titleY,int statusY,int rows,
                              int attemptY,int buttonY,int buttonHeight,int buttonWidth,int padding) {
    public static ReturnLayout110 of(int width,int height,int requestedRows,int lineHeight){
        int w=Math.max(1,width),h=Math.max(1,height),padding=Math.min(8,Math.max(1,w/20));
        int bh=Math.min(20,Math.max(8,h/7)),by=Math.max(0,h-Math.min(8,Math.max(1,h/20))-bh);
        int ay=Math.max(1,by-Math.max(14,lineHeight+5)),step=Math.max(2,lineHeight+2);
        int rows=Math.max(1,Math.min(Math.max(1,requestedRows),Math.max(1,(ay-32)/step)));
        int sy=Math.max(12,ay-4-rows*step),ty=Math.max(2,sy-Math.max(16,lineHeight+7)),bottom=Math.max(0,ty-10);
        int radius=bottom<36?0:Math.min(48,(bottom-12)/3);
        return new ReturnLayout110(w/2,bottom/2,radius,ty,sy,rows,ay,by,bh,Math.max(1,Math.min(210,w-2*padding)),padding);
    }
}
