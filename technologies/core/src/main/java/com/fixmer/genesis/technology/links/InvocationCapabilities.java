package com.fixmer.genesis.technology.links;
import java.util.List;
/** Explicit host capabilities for a function invocation; no reflective game access. */
public interface InvocationCapabilities<C> {
    boolean supports(String name);
    Object call(String name,List<Object> args,C context);
}
