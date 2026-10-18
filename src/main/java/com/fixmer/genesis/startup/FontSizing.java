package com.fixmer.genesis.startup;

/** An allocation hint only. The original callback still builds each fresh glyph set. */
public final class FontSizing {
    private FontSizing() {}
    private static final int LIMIT=1<<20;
    private static long calls,largeCalls,expectedSymbols,cappedCalls,maxExpected;
    public static int expected(Object[][] blocks,Object[] empty) {
        int count=0;boolean capped=false;
        if(blocks!=null)outer:for(Object[] block:blocks) {
            if(block==null||block==empty)continue;
            for(Object value:block)if(value!=null&&++count>=LIMIT){capped=true;break outer;}
        }
        synchronized(FontSizing.class){calls++;if(count>24)largeCalls++;expectedSymbols+=count;if(capped)cappedCalls++;maxExpected=Math.max(maxExpected,count);}
        return count;
    }
    public static synchronized long[] counts(){return new long[]{calls,largeCalls,expectedSymbols,cappedCalls,maxExpected};}
}
