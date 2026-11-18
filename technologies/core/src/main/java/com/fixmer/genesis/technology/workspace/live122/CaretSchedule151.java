package com.fixmer.genesis.technology.workspace.live122;

import java.util.Objects;

/** Latest caret only: 20Hz maximum and a heartbeat, without queuing obsolete positions. */
public final class CaretSchedule151 {
    private long sent;
    private String stamp;
    public boolean take(long now,String state){
        Objects.requireNonNull(state);
        if(stamp!=null&&(now-sent<50_000_000L||stamp.equals(state)&&now-sent<500_000_000L))return false;
        stamp=state;sent=now;return true;
    }
    public void retry(){stamp=null;}
    /** Project a code-point boundary across an unprepared local edit. Anchors outside it remain exact. */
    public record Projection(int before,int after,int prefix,int suffix){
        public int map(int offset){
            offset=Math.max(0,Math.min(offset,before));
            if(offset<=prefix)return offset;
            if(offset>=before-suffix)return after-(before-offset);
            return Math.min(after-suffix,offset);
        }
    }
    public static Projection between(String before,String after){
        int n=before.codePointCount(0,before.length());
        if(before.equals(after))return new Projection(n,n,n,0);
        int m=after.codePointCount(0,after.length()),prefix=0,a=0,b=0;
        while(a<before.length()&&b<after.length()){
            int x=before.codePointAt(a),y=after.codePointAt(b);if(x!=y)break;
            prefix++;a+=Character.charCount(x);b+=Character.charCount(y);
        }
        int suffix=0;a=before.length();b=after.length();
        while(suffix<n-prefix&&suffix<m-prefix){
            int x=before.codePointBefore(a),y=after.codePointBefore(b);if(x!=y)break;
            suffix++;a-=Character.charCount(x);b-=Character.charCount(y);
        }
        return new Projection(n,m,prefix,suffix);
    }
    public static int project(String before,String after,int offset){return between(before,after).map(offset);}
}
