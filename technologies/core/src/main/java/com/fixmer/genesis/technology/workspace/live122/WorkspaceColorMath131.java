package com.fixmer.genesis.technology.workspace.live122;
public final class WorkspaceColorMath131 {
    private WorkspaceColorMath131(){}
    public static int rgb(float h,float s,float v){h=(h%1+1)%1;s=Math.max(0,Math.min(1,s));v=Math.max(0,Math.min(1,v));float q=h*6,f=q-(int)q,p=v*(1-s),a=v*(1-s*f),b=v*(1-s*(1-f));float r,g,blue;switch((int)q){case 0:r=v;g=b;blue=p;break;case 1:r=a;g=v;blue=p;break;case 2:r=p;g=v;blue=b;break;case 3:r=p;g=a;blue=v;break;case 4:r=b;g=p;blue=v;break;default:r=v;g=p;blue=a;}return Math.round(r*255)<<16|Math.round(g*255)<<8|Math.round(blue*255);}
    public static float[] hsv(int rgb){float r=(rgb>>16&255)/255f,g=(rgb>>8&255)/255f,b=(rgb&255)/255f,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;float h=d==0?0:max==r?((g-b)/d%6)/6:max==g?((b-r)/d+2)/6:((r-g)/d+4)/6;if(h<0)h+=1;return new float[]{h,max==0?0:d/max,max};}
    public static boolean insertionOnly(String before,String after){int[] a=before.codePoints().toArray(),b=after.codePoints().toArray();int i=0;for(int cp:b)if(i<a.length&&cp==a[i])i++;return i==a.length;}
}
