package com.fixmer.mared.dev.client;

import java.lang.reflect.*;
import java.util.Locale;

/** Optional Windows foreground check. No new native dependencies, global hooks or synthetic input. */
public final class DevNativeFocus {
    private DevNativeFocus() {}
    private static final boolean WINDOWS=System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    private static boolean initialized,unavailable;
    private static Object user32;
    private static Class<?> pointer;
    private static Method getFunction,invoke,nativeValue,nativeWindow;
    private static void initialize() throws ReflectiveOperationException {
        if(unavailable)throw new ClassNotFoundException("Windows focus API unavailable");
        if(initialized)return;
        try{
            Class<?> library=Class.forName("com.sun.jna.NativeLibrary"),function=Class.forName("com.sun.jna.Function");
            pointer=Class.forName("com.sun.jna.Pointer");
            user32=library.getMethod("getInstance",String.class).invoke(null,"user32");
            getFunction=library.getMethod("getFunction",String.class,int.class);
            invoke=function.getMethod("invoke",Class.class,Object[].class);
            nativeValue=pointer.getMethod("nativeValue",pointer);
            nativeWindow=Class.forName("org.lwjgl.glfw.GLFWNativeWin32").getMethod("glfwGetWin32Window",long.class);
            initialized=true;
        }catch(ReflectiveOperationException|LinkageError e){unavailable=true;throw e;}
    }
    private static Object call(String name,Class<?> type,Object... arguments)throws ReflectiveOperationException {
        // ALT_CONVENTION (stdcall) matches WinAPI on both supported Windows architectures.
        Object function=getFunction.invoke(user32,name,63);
        return invoke.invoke(function,type,arguments);
    }
    public static long foreground(){
        if(!WINDOWS)return -1;
        try{initialize();Object handle=call("GetForegroundWindow",pointer);return handle==null?0:((Number)nativeValue.invoke(null,handle)).longValue();}
        catch(ReflectiveOperationException|RuntimeException|LinkageError e){unavailable=true;return -1;}
    }
    /** true/false on Windows; null means unsupported and GLFW remains the fallback. */
    public static Boolean ownsForeground(long window){
        long foreground=foreground();if(foreground<0)return null;
        try{return foreground==((Number)nativeWindow.invoke(null,window)).longValue();}
        catch(ReflectiveOperationException|RuntimeException|LinkageError e){return null;}
    }
    public static boolean request(long window){
        if(!WINDOWS)return false;
        try{
            initialize();long handle=((Number)nativeWindow.invoke(null,window)).longValue();if(handle==0)return false;
            Object hwnd=pointer.getConstructor(long.class).newInstance(handle);
            boolean accepted=((Number)call("SetForegroundWindow",Integer.class,hwnd)).intValue()!=0;
            // These calls run on the GLFW owner thread; they affect only this game's window.
            if(Boolean.TRUE.equals(ownsForeground(window))){call("SetActiveWindow",pointer,hwnd);call("SetFocus",pointer,hwnd);}
            return accepted;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){unavailable=true;return false;}
    }
}
