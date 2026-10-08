package com.fixmer.genesis.technology.links;
/** Portable condition -> action scenario. No overlapping action and no hidden code copy. */
public final class LinkedBehavior<C> implements AutoCloseable {
    public enum State {
        IDLE, RUNNING, ERROR, CLOSED
    }
    public interface Action extends AutoCloseable {
        boolean done();
        Throwable failure();
        void close();
    }
    public interface Host<C> {
        boolean condition(C context);
        Action action(C context);
    }
    private final Host<C> host;
    private final int interval;
    private Action active;
    private long due=Long.MIN_VALUE;
    private State state=State.IDLE;
    private Throwable failure;
    public LinkedBehavior(Host<C> host,int interval) {
        if(interval<1)throw new IllegalArgumentException("Interval");
        this.host=java.util.Objects.requireNonNull(host);
        this.interval=interval;
    }
    public long dueTick() {
        return due;
    }
    public boolean hasActiveFailure() {
        try {
            return active!=null&&active.failure()!=null;
        }
        catch(RuntimeException error) {
            return true;
        }
    }
    public State state() {
        return state;
    }
    public Throwable failure() {
        return failure;
    }
    public void tick(C context,long tick) {
        if(state==State.CLOSED||state==State.ERROR)return;
        try {
            if(active!=null) {
                if(active.failure()!=null)throw new IllegalStateException("Action failed",active.failure());
                if(active.done()) {
                    active.close();
                    active=null;
                    state=State.IDLE;
                }
            }
            if(tick<due)return;
            due=tick>Long.MAX_VALUE-interval?Long.MAX_VALUE:tick+interval;
            if(!host.condition(context)) {
                if(active!=null) {
                    active.close();
                    active=null;
                }
                state=State.IDLE;
                return;
            }
            if(active==null) {
                active=java.util.Objects.requireNonNull(host.action(context));
                state=State.RUNNING;
            }
        }
        catch(RuntimeException error) {
            failure=error;
            try {
                if(active!=null)active.close();
            }
            catch(RuntimeException cleanup) {
                if(cleanup!=error)error.addSuppressed(cleanup);
            }
            finally {
                active=null;
                state=State.ERROR;
            }
        }
    }
    @Override public void close() {
        try {
            if(active!=null)active.close();
        }
        finally {
            active=null;
            state=State.CLOSED;
        }
    }
}
