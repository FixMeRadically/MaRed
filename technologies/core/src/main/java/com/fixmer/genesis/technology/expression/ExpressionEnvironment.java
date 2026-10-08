package com.fixmer.genesis.technology.expression;
import java.util.List;
/** Capabilities provided by the host; no Minecraft dependency. */
public interface ExpressionEnvironment {
    Object getVariable(String name);
    boolean hasVariable(String name);
    boolean hasFunction(String name);
    Object callFunction(String name, List<Object> args);
    Object callBuiltin(String name, List<Object> args);
    Object callMethod(Object receiver, String name, List<Object> args);
}
