package com.fixmer.genesis.technology.server;

/** Deliberately visible changes: applying the template sets overworld noon and broadcasts once. */
public final class ServerModuleTemplate {
    private ServerModuleTemplate() {}
    public static String source(){return """
        import com.fixmer.genesis.technology.server.ServerModule;
        import com.fixmer.genesis.technology.server.ServerContext;

        public final class WorldModule implements ServerModule {
            private ServerContext.Subscription ticks;

            @Override public void onEnable(ServerContext context) {
                String previous = context.state("applies");
                long count = previous == null ? 1 : Long.parseLong(previous) + 1;
                context.state("applies", Long.toString(count));
                context.setDayTime("minecraft:overworld", 6000);
                context.broadcast("Genesis: module applied " + count + " time(s)");
                context.log("Ready, generation " + context.generation());
                ticks = context.onTick(tick -> {
                    if (tick % 200 == 0) context.log("Module alive: tick " + tick);
                });
            }

            @Override public void onDisable() {
                if (ticks != null) ticks.close();
            }
        }
        """;}
}
