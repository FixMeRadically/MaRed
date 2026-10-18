package com.fixmer.genesis.supervisor;

import java.awt.*;
import java.awt.geom.*;
import java.util.Random;

/** Java2D counterpart of GenesisCoreRenderer: same four rings, octahedron and pulse.
 * Minecraft's renderer itself is reused by DevRestartOverlay. No textures or game classes here. */
final class RestartCorePainter {
    static void paint(Graphics2D g,int w,int h,double seconds,String stage,long elapsed,boolean reducedMotion) {
        paint(g,w,h,seconds,stage,elapsed,reducedMotion,new PixelText());
    }
    static void paint(Graphics2D g,int w,int h,double seconds,String stage,long elapsed,boolean reducedMotion,PixelText font) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setPaint(new GradientPaint(0,0,new Color(1,1,4),0,h,new Color(5,5,16)));g.fillRect(0,0,w,h);
        double t=reducedMotion?2:seconds+2,r=RestartSurface.coreRadius(h),cx=w*.5,cy=h*.5;
        double pulse=Math.sin(t*1.8)*.5+.5;
        glow(g,cx,cy,r*3.2,24);glow(g,cx,cy,r*(2.4+.4*pulse),(int)(20+20*pulse));
        ring(g,cx,cy,r*1.05,t*.4,0,0,140);
        ring(g,cx,cy,r*.88,0,t*.55,0,110);
        ring(g,cx,cy,r*1.18,0,0,t*.45,95);
        ring(g,cx,cy,r*1.35,t*.35,t*.22,0,75);
        double[][] v={{0,1,0},{1,0,0},{0,0,1},{-1,0,0},{0,0,-1},{0,-1,0}};
        double[][] xy=new double[6][];
        for(int i=0;i<6;i++)xy[i]=project(v[i][0]*r*.65,v[i][1]*r*.65,v[i][2]*r*.65,0,t*.4,0,cx,cy);
        int[][] edges={{0,1},{0,2},{0,3},{0,4},{5,1},{5,2},{5,3},{5,4},{1,2},{2,3},{3,4},{4,1}};
        g.setStroke(new BasicStroke(1f));g.setColor(new Color(255,255,255,190));
        for(int[] e:edges)g.drawLine((int)Math.round(xy[e[0]][0]),(int)Math.round(xy[e[0]][1]),(int)Math.round(xy[e[1]][0]),(int)Math.round(xy[e[1]][1]));
        double pr=r*(.20+.08*pulse);glow(g,cx,cy,pr*2,150);g.setColor(new Color(255,255,255,(int)(190+65*pulse)));for(int yy=(int)-pr;yy<=pr;yy++){double half=Math.sqrt(Math.max(0,pr*pr-yy*yy));int left=(int)Math.floor((cx-half)/2)*2,right=(int)Math.ceil((cx+half)/2)*2;g.fillRect(left,(int)cy+yy,Math.max(2,right-left),1);}
        g.setColor(Color.WHITE);g.fill(new Rectangle2D.Double(cx-2,cy-2,5,5));
        int y=RestartSurface.textTop(h);
        font.centered(g,"GENESIS",w/2+1,y+1,0x3C3C3F);font.centered(g,"GENESIS",w/2,y,0xF0F1FC);
        font.centered(g,stage,w/2+1,y+16,0x2F3035);font.centered(g,stage,w/2,y+15,0xBEC2D5);
    }
    private static void glow(Graphics2D g,double x,double y,double r,int alpha){g.setPaint(new RadialGradientPaint(new Point2D.Double(x,y),(float)r,new float[]{0,1},new Color[]{new Color(255,255,255,alpha),new Color(255,255,255,0)}));g.fill(new Ellipse2D.Double(x-r,y-r,r*2,r*2));}
    private static void ring(Graphics2D g,double cx,double cy,double r,double rx,double ry,double rz,int alpha){
        Path2D path=new Path2D.Double();
        for(int i=0;i<=48;i++){double a=i*Math.PI*2/48;double[] p=project(Math.cos(a)*r,0,Math.sin(a)*r,rx,ry,rz,cx,cy);if(i==0)path.moveTo(Math.rint(p[0]),Math.rint(p[1]));else path.lineTo(Math.rint(p[0]),Math.rint(p[1]));}
        g.setColor(new Color(255,255,255,alpha));g.setStroke(new BasicStroke(1f));g.draw(path);
    }
    private static double[] project(double x,double y,double z,double rx,double ry,double rz,double cx,double cy){
        double y1=y*Math.cos(rx)-z*Math.sin(rx),z1=y*Math.sin(rx)+z*Math.cos(rx);
        double x2=x*Math.cos(ry)-z1*Math.sin(ry),z2=x*Math.sin(ry)+z1*Math.cos(ry);
        double x3=x2*Math.cos(rz)-y1*Math.sin(rz),y3=x2*Math.sin(rz)+y1*Math.cos(rz);
        double perspective=Math.max(.1,Math.min(4.5,2400/(2400+z2)));
        return new double[]{cx+x3*perspective,cy-y3*perspective};
    }
}
