package com.fixmer.genesis.technology.editor;

/** Screen-space scissors need the same translation as the embedded screen's pose. */
public final class EmbeddedClip155 implements AutoCloseable {
    private static final ThreadLocal<EmbeddedClip155> CURRENT=new ThreadLocal<>();
    private final EmbeddedClip155 previous;
    private final int x,y;
    private boolean closed;
    private EmbeddedClip155(int dx,int dy){previous=CURRENT.get();x=dx+(previous==null?0:previous.x);y=dy+(previous==null?0:previous.y);CURRENT.set(this);}
    public static EmbeddedClip155 enter(int dx,int dy){return new EmbeddedClip155(dx,dy);}
    public static int x(int local){var scope=CURRENT.get();return local+(scope==null?0:scope.x);}
    public static int y(int local){var scope=CURRENT.get();return local+(scope==null?0:scope.y);}
    @Override public void close(){if(closed)return;if(CURRENT.get()!=this)throw new IllegalStateException("CLIP155_ORDER");closed=true;if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
