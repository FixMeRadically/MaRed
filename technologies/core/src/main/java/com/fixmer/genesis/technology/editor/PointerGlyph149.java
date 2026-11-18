package com.fixmer.genesis.technology.editor;
import java.util.*;
/** Familiar diagonal mouse arrow: dark edge, personal-colour rim, white fill. No bitmap dependency. */
public final class PointerGlyph149 {
    public record Run(int x,int y,int length,int ink){}
    private static final double[][] P={{1,1},{7,23},{12,16},{20,24},{24,20},{16,12},{23,7}};
    public static final List<Run> RUNS;
    static {
        var runs=new ArrayList<Run>();
        for(int y=0;y<26;y++){int start=0,last=0;for(int x=0;x<=26;x++){int ink=x==26?0:ink(x+.5,y+.5);if(ink!=last){if(last!=0)runs.add(new Run(start,y,x-start,last));start=x;last=ink;}}}RUNS=List.copyOf(runs);
    }
    private PointerGlyph149(){}
    private static int ink(double x,double y){
        boolean inside=false;double distance=Double.MAX_VALUE;
        for(int i=0,j=P.length-1;i<P.length;j=i++){
            double ax=P[j][0],ay=P[j][1],bx=P[i][0],by=P[i][1];
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
            double vx=bx-ax,vy=by-ay,t=Math.max(0,Math.min(1,((x-ax)*vx+(y-ay)*vy)/(vx*vx+vy*vy)));
            distance=Math.min(distance,Math.hypot(x-ax-t*vx,y-ay-t*vy));
        }
        if(!inside)return distance<=.65?1:0;return distance<.65?1:distance<2?2:3;
    }
}
