import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Isolated acceptance agent; never included in the mod or connected to a server. */
public final class UiAcceptance129 {
    private static Path output;
    private static Object mc, shell, runtime;
    private static ClassLoader loader;
    private static int stage;
    private static final List<String> evidence = new ArrayList<>();
    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        output = Path.of(args); Files.createDirectories(output);
        Thread worker = new Thread(() -> {
            try {
                for (int attempt = 0; attempt < 240; attempt++) {
                    for (Class<?> type : instrumentation.getAllLoadedClasses()) if (type.getName().equals("net.minecraft.client.Minecraft")) {
                        loader = type.getClassLoader(); mc = type.getMethod("getInstance").invoke(null); break;
                    }
                    if (mc != null) break;
                    Thread.sleep(500);
                }
                if (mc == null) throw new AssertionError("No Minecraft instance");
                for (int attempt = 0; attempt < 360 && stage < 15; attempt++) {
                    CompletableFuture<Void> frame = new CompletableFuture<>();
                    mc.getClass().getMethod("execute", Runnable.class).invoke(mc, (Runnable)() -> {
                        try { step(); frame.complete(null); } catch (Throwable error) { frame.completeExceptionally(error); }
                    });
                    frame.get(30, TimeUnit.SECONDS); Thread.sleep(900);
                }
                if (stage < 15) throw new AssertionError("Acceptance timed out at stage " + stage);
                Files.writeString(output.resolve("passed.txt"), String.join("\n", evidence));
            } catch (Throwable error) {
                try { Files.writeString(output.resolve("failed.txt"), stack(error)); } catch(Exception ignored) {}
            } finally {
                if(mc != null) try { mc.getClass().getMethod("execute", Runnable.class).invoke(mc, (Runnable)() -> {
                    try { mc.getClass().getMethod("stop").invoke(mc); } catch(Exception ignored) {}
                }); } catch(Exception ignored) {}
            }
        }, "ui-acceptance129");
        worker.setDaemon(true); worker.start();
    }
    private static String stack(Throwable error) { var s = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(s)); return s.toString(); }
    private static Class<?> type(String name) throws Exception { return Class.forName(name, true, loader); }
    private static Object call(Object target, String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    private static void screen(Object screen) throws Exception { mc.getClass().getMethod("setScreen", type("net.minecraft.client.gui.screens.Screen")).invoke(mc, screen); }
    private static Object current() throws Exception { return mc.getClass().getField("screen").get(mc); }
    private static void hub(String section) throws Exception {
        var s = type("com.fixmer.mared.gui2.development.DevelopmentHub129$Section");
        Object value = s.getField(section).get(null);
        type("com.fixmer.mared.gui2.development.DevelopmentNavigation129").getMethod("open", s).invoke(null, value);
    }
    private static void screenshot(String name) throws Exception {
        Object target = call(mc, "getMainRenderTarget");
        var method = Arrays.stream(type("net.minecraft.client.Screenshot").getMethods()).filter(m -> m.getName().equals("takeScreenshot") && m.getParameterCount() == 1).findFirst().orElseThrow();
        Object image = method.invoke(null, target);
        try { image.getClass().getMethod("writeToFile", Path.class).invoke(image, output.resolve(name + ".png")); }
        finally { call(image, "close"); }
        evidence.add("Rendered " + name);
    }
    private static void key(int key) throws Exception { current().getClass().getMethod("keyPressed", int.class, int.class, int.class).invoke(current(), key, 0, 0); }
    private static void step() throws Exception {
        if (stage == 0) {
            Object screen = current();
            if (screen == null || !screen.getClass().getSimpleName().equals("TitleScreen") || call(mc,"getOverlay") != null) return;
            shell = type("com.fixmer.mared.gui2.shell.MaredShellScreen").getConstructor().newInstance();
            var id = type("com.fixmer.mared.gui2.navigation.SpaceId");
            shell.getClass().getMethod("setInitialSpace", id).invoke(shell, id.getField("STUDIO").get(null));
            screen(shell);
        } else if (stage == 1) {
            runtime = type("com.fixmer.mared.gui2.runtime.RuntimeProvider").getMethod("get").invoke(null);
            screenshot("editor-sidebar"); hub("SERVER");
        } else if (stage == 2) { screenshot("server"); key(267); }
        else if (stage == 3) { screenshot("server-scrolled"); call(current(), "onClose"); }
        else if (stage == 4) {
            if (current() != shell || runtime != type("com.fixmer.mared.gui2.runtime.RuntimeProvider").getMethod("get").invoke(null)) throw new AssertionError("Editor runtime lost after navigation");
            evidence.add("Editor runtime identity preserved after return"); hub("TEAM");
        } else if (stage == 5) {
            screenshot("team-disconnected");
            Object workspace = ((List<?>)call(current(), "children")).get(5);
            if (workspace.getClass().getField("active").getBoolean(workspace)) throw new AssertionError("Disconnected workspace action enabled");
            evidence.add("Workspace action disabled without a game server connection");
            hubFromExisting("SUPERVISOR");
        } else if (stage == 6) { screenshot("supervisor"); hubFromExisting("UPDATES"); }
        else if (stage == 7) { screenshot("updates"); hubFromExisting("DIAGNOSTICS"); }
        else if (stage == 8) {
            screenshot("diagnostics");
            Object window = call(mc, "getWindow");
            long handle = (Long)call(window, "getWindow");
            type("org.lwjgl.glfw.GLFW").getMethod("glfwSetWindowSize", long.class, int.class, int.class).invoke(null, handle, 720, 480);
        } else if (stage == 9) { screenshot("compact"); key(267); }
        else if (stage == 10) { screenshot("compact-scrolled"); hubFromExisting("SERVER"); }
        else if (stage == 11) {
            screenshot("compact-server");
            Object previous = current();
            screen(type("com.fixmer.mared.technology.server.setup.InPlaceServerScreen104").getConstructor(type("net.minecraft.client.gui.screens.Screen")).newInstance(previous));
        } else if (stage == 12) { screenshot("server-controls-themed"); call(current(), "onClose"); }
        else if (stage == 13) {
            var theme = type("com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry");
            for (Object t : (List<?>)theme.getMethod("all").invoke(null)) if (t.getClass().getField("light").getBoolean(t)) {
                theme.getMethod("setActive", t.getClass()).invoke(null, t); break;
            }
        } else if (stage == 14) screenshot("light-theme");
        stage++;
    }
    private static void hubFromExisting(String name) throws Exception {
        // Navigate via the actual sidebar button, preserving its parent and rebuilding widgets.
        var section = type("com.fixmer.mared.gui2.development.DevelopmentHub129$Section").getField(name).get(null);
        int index = ((Enum<?>)section).ordinal();
        Object button = ((List<?>)call(current(), "children")).get(index);
        call(button, "onPress");
    }
}
