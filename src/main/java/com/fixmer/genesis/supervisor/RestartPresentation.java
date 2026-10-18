package com.fixmer.genesis.supervisor;

/** UI is optional: a headless or stripped JRE must retain the complete lifecycle. */
final class RestartPresentation implements AutoCloseable {
    interface Display extends AutoCloseable {
        void begin(String phase);
        void phase(String phase);
        void ready();
        @Override void close();
    }
    private final Display display;
    private final java.nio.file.Path game;
    private volatile long epoch;
    long epochMs(){return epoch;}
    RestartPresentation(SupervisorConfig config,SupervisorLog log) {
        game=config.gameDir();
        Display found=null;
        if(config.restartUi())try {
            found=(Display)Class.forName("com.fixmer.genesis.supervisor.SwingRestartDisplay")
                .getDeclaredConstructor(java.nio.file.Path.class,boolean.class).newInstance(config.gameDir(),config.reducedMotion());
        }catch(ReflectiveOperationException|LinkageError|RuntimeException e){log.info("[restart-ui] Desktop unavailable; restart continues without window");}
        display=found;
    }
    void begin(String phase){RestartSurface surface=RestartSurface.load(RestartSurface.path(game));epoch=surface==null?0:surface.epoch();if(display!=null)display.begin(phase);}
    void phase(String phase){if(display!=null)display.phase(phase);}
    void ready(){if(display!=null)display.ready();}
    @Override public void close(){if(display!=null)display.close();}
}
