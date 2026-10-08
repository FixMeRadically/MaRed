package com.fixmer.mared.technology.ai;
import com.fixmer.mared.Mared;
import com.fixmer.mared.commands.engine.*;
import com.fixmer.mared.commands.expr.MaredExpr;
import com.fixmer.mared.mixin.GenesisMobGoals;
import com.fixmer.mared.technology.links.*;
import com.fixmer.mared.technology.runtime.ScriptDispatch;
import com.fixmer.genesis.technology.links.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import com.fixmer.genesis.technology.runtime.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;
import java.lang.ref.WeakReference;
/** Tagged mobs get one owned MOVE/LOOK goal. Vanilla goals remain installed and resume after detach. */
public final class GenesisAiRuntime {
    private GenesisAiRuntime() {
    }
    private static final ExecutionLimits LIMITS=new ExecutionLimits(200,10000,32,2_000_000);
    private static final Map<MinecraftServer,ServerState> SERVERS=new WeakHashMap<>();
    public record Status(int attached,int running,int errors) {
    }
    private record Tracked(WeakReference<Mob> mob,WeakReference<LinkedGoal> goal,long revision,UUID profile) {
    }
    private static final class ServerState {
        final Map<UUID,Tracked> tracked=new LinkedHashMap<>();
        Map<String,AiProfile> tags=Map.of();
        long revision=-1,spent;
        int calls,checks,paths,checkCursor,pathCursor;
        Set<UUID> evaluations=Set.of(),navigation=Set.of();
        volatile Map<UUID,Status> status=Map.of();
        void allocate() {
            var ids=List.copyOf(tracked.keySet());
            if(ids.isEmpty()) {
                evaluations=Set.of();
                navigation=Set.of();
                return;
            }
            var eval=new HashSet<UUID>();
            var nav=new HashSet<UUID>();
            checkCursor=Math.floorMod(checkCursor,ids.size());
            pathCursor=Math.floorMod(pathCursor,ids.size());
            for(int i=0;i<Math.min(8,ids.size());i++)eval.add(ids.get((checkCursor+i)%ids.size()));
            for(int i=0;i<Math.min(16,ids.size());i++)nav.add(ids.get((pathCursor+i)%ids.size()));
            checkCursor=(checkCursor+8)%ids.size();
            pathCursor=(pathCursor+16)%ids.size();
            evaluations=Set.copyOf(eval);
            navigation=Set.copyOf(nav);
        }
        boolean budget(UUID actor) {
            if(!evaluations.contains(actor)||checks>=16||calls>=32||spent>=2_000_000)return false;
            checks++;
            calls++;
            return true;
        }
        boolean pathBudget(UUID actor) {
            if(!navigation.contains(actor)||paths>=16||calls>=32||spent>=2_000_000)return false;
            paths++;
            calls++;
            return true;
        }
    }
    private static synchronized ServerState state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server,s->new ServerState());
    }
    public static Status status(MinecraftServer server,UUID profile) {
        return server==null?new Status(0,0,0):state(server).status.getOrDefault(profile,new Status(0,0,0));
    }
    public static void beginTick(MinecraftServer server) {
        var state=state(server);
        state.spent=0;
        state.calls=0;
        state.checks=0;
        state.paths=0;
        var snapshot=ScriptLinkService.get().snapshot();
        if(state.revision!=snapshot.revision()) {
            var tags=new HashMap<String,AiProfile>();
            for(var p:snapshot.project().profiles())if(p.enabled()&&snapshot.problem(p)==null)tags.put(p.tag(),p);
            state.tags=Map.copyOf(tags);
            state.revision=snapshot.revision();
        }
        var status=new HashMap<UUID,int[]>();
        for(var entry:List.copyOf(state.tracked.entrySet())) {
            var track=entry.getValue();
            var mob=track.mob.get();
            var goal=track.goal.get();
            if(mob==null||goal==null||mob.isRemoved()||!mob.isAlive()||track.revision!=snapshot.revision()) {
                if(goal!=null)goal.stop();
                if(mob!=null&&goal!=null)((GenesisMobGoals)(Object)mob).genesis$goals().removeGoal(goal);
                state.tracked.remove(entry.getKey());
                continue;
            }
            int[] counts=status.computeIfAbsent(track.profile,k->new int[3]);
            counts[0]++;
            if(goal.runner!=null&&goal.runner.state()==LinkedBehavior.State.RUNNING)counts[1]++;
            if(goal.fault!=null)counts[2]++;
        }
        var immutable=new HashMap<UUID,Status>();
        status.forEach((k,v)->immutable.put(k,new Status(v[0],v[1],v[2])));
        state.status=Map.copyOf(immutable);
        state.allocate();
    }
    /** Called on the server from EntityTickEvent.Post; no world scan and no disk IO. */
    public static void entityTick(Mob mob) {
        if(!(mob.level() instanceof ServerLevel level)||mob.isRemoved())return;
        var server=level.getServer();
        var state=state(server);
        var snapshot=ScriptLinkService.get().snapshot();
        if(state.revision!=snapshot.revision())return;
        AiProfile profile=null;
        for(String tag:mob.getTags()) {
            var candidate=state.tags.get(tag);
            if(candidate!=null) {
                if(profile!=null) {
                    profile=null;
                    break;
                }
                profile=candidate;
            }
        }
        var old=state.tracked.get(mob.getUUID());
        if(old!=null&&(profile==null||!old.profile.equals(profile.id())||old.revision!=snapshot.revision())) {
            var goal=old.goal.get();
            if(goal!=null) {
                goal.stop();
                ((GenesisMobGoals)(Object)mob).genesis$goals().removeGoal(goal);
            }
            state.tracked.remove(mob.getUUID());
            old=null;
        }
        if(profile==null||old!=null||state.tracked.size()>=256)return;
        var goal=new LinkedGoal(mob,profile,snapshot,state);
        ((GenesisMobGoals)(Object)mob).genesis$goals().addGoal(-1,goal);
        state.tracked.put(mob.getUUID(),new Tracked(new WeakReference<>(mob),new WeakReference<>(goal),snapshot.revision(),profile.id()));
    }
    public static void clear(MinecraftServer server) {
        ServerState state;
        synchronized(GenesisAiRuntime.class) {
            state=SERVERS.remove(server);
        }
        if(state!=null)for(var track:state.tracked.values()) {
            var goal=track.goal.get();
            var mob=track.mob.get();
            if(goal!=null)goal.stop();
            if(goal!=null&&mob!=null)((GenesisMobGoals)(Object)mob).genesis$goals().removeGoal(goal);
        }
    }
    private static final class LinkedGoal extends Goal {
        final Mob mob;
        final AiProfile profile;
        final ScriptLinkService.Snapshot snapshot;
        final ServerState budget;
        final NavigationHost navigation=new NavigationHost();
        LinkedBehavior<MaredScriptContext> runner;
        UUID targetId;
        ServerPlayer chosen;
        long targetDue=Long.MIN_VALUE;
        Throwable fault;
        LinkedGoal(Mob mob,AiProfile profile,ScriptLinkService.Snapshot snapshot,ServerState budget) {
            this.mob=mob;
            this.profile=profile;
            this.snapshot=snapshot;
            this.budget=budget;
            setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));
        }
        private void newRunner() {
            runner=new LinkedBehavior<>(new LinkedBehavior.Host<>() {
                public boolean condition(MaredScriptContext ctx) {
                    navigation.condition=true;
                    try {
                        return ScriptLibrary.condition(snapshot.catalog(),snapshot.project().binding(profile.condition()),ctx,LIMITS);
                    }
                    finally {
                        navigation.condition=false;
                    }
                }
                public LinkedBehavior.Action action(MaredScriptContext ctx) {
                    return ScriptLibrary.action(snapshot.catalog(),snapshot.project().binding(profile.action()),ctx,LIMITS);
                }
            }
            ,profile.interval());
        }
        @Override public boolean canUse() {
            return !mob.isRemoved()&&mob.isAlive()&&!mob.isNoAi()&&fault==null;
        }
        @Override public boolean canContinueToUse() {
            return canUse();
        }
        @Override public boolean requiresUpdateEveryTick() {
            return true;
        }
        @Override public void start() {
            if(runner==null||runner.state()==LinkedBehavior.State.CLOSED)newRunner();
        }
        @Override public void stop() {
            if(runner!=null)runner.close();
            navigation.stop();
            targetId=null;
            chosen=null;
        }
        @Override public void tick() {
            long started=System.nanoTime();
            try {
                tickOwned();
            }
            catch(RuntimeException error) {
                fault=error;
                if(runner!=null)runner.close();
                navigation.stop();
                Mared.LOGGER.warn("[genesis-ai] profile {} failed for {}",profile.title(),mob.getUUID(),error);
            }
            finally {
                budget.spent+=System.nanoTime()-started;
            }
        }
        private void tickOwned() {
            var level=(ServerLevel)mob.level();
            long time=level.getGameTime();
            navigation.update(time);
            if(runner!=null&&runner.hasActiveFailure()) {
                runner.tick(null,time);
                fault=runner.failure();
                navigation.stop();
                Mared.LOGGER.warn("[genesis-ai] profile {} failed for {}",profile.title(),mob.getUUID(),fault);
                return;
            }
            if(time>=targetDue) {
                if(!budget.budget(mob.getUUID()))return;
                targetDue=time+profile.interval();
                var nearest=level.getNearestPlayer(mob,profile.range());
                chosen=nearest instanceof ServerPlayer player&&!player.isSpectator()?player:null;
            }
            var player=chosen;
            if(player==null||player.isRemoved()||player.level()!=mob.level()) {
                if(targetId!=null) {
                    if(runner!=null)runner.close();
                    navigation.stop();
                    newRunner();
                }
                targetId=null;
                return;
            }
            if(targetId!=null&&!targetId.equals(player.getUUID())) {
                if(runner!=null)runner.close();
                navigation.stop();
                newRunner();
            }
            targetId=player.getUUID();
            if(runner==null)newRunner();
            if(time<runner.dueTick()&&!runner.hasActiveFailure())return;
            if(!budget.budget(mob.getUUID()))return;
            var ctx=new MaredScriptContext(player,level.getServer(),line->Mared.LOGGER.debug("[genesis-ai] {}",line));
            ctx.setCapabilityHost(navigation);
            ctx.setVariable("target",player);
            ctx.setVariable("actor",mob);
            ctx.setVariable("distance",Math.sqrt(mob.distanceToSqr(player)));
            runner.tick(ctx,time);
            if(runner.state()==LinkedBehavior.State.ERROR) {
                fault=runner.failure();
                navigation.stop();
                Mared.LOGGER.warn("[genesis-ai] profile {} failed for {}",profile.title(),mob.getUUID(),fault);
            }
        }
        private final class NavigationHost implements InvocationCapabilities<MaredScriptContext> {
            boolean condition;
            ExecutionScope owner;
            ExecutionScope.Lease lease;
            Entity target;
            double speed;
            long due;
            public boolean supports(String name) {
                return name.equals("ai_move_to")||name.equals("ai_stop");
            }
            public Object call(String name,List<Object> args,MaredScriptContext ctx) {
                var server=((ServerLevel)mob.level()).getServer();
                if(ctx.getServer()!=server||!server.isSameThread())throw new IllegalStateException("AI capability used outside its server owner");
                if(condition)throw new IllegalStateException("AI conditions cannot call movement capabilities");
                if(name.equals("ai_stop")) {
                    stop();
                    return true;
                }
                if(args.size()!=2||!(args.get(0) instanceof Entity entity)||entity.level()!=mob.level())throw new IllegalArgumentException("ai_move_to needs target entity in same dimension and speed");
                double requested=MaredExpr.toNumber(args.get(1));
                if(!Double.isFinite(requested)||requested<.05||requested>4)throw new IllegalArgumentException("AI speed must be finite in 0.05..4");
                var scope=ctx.executionScope();
                if(scope==null||scope.cancelled())throw new IllegalStateException("AI movement needs live invocation owner");
                if(owner!=null&&owner!=scope)throw new IllegalStateException("Navigation already owned");
                target=entity;
                speed=requested;
                if(owner==null) {
                    owner=scope;
                    lease=scope.own(ExecutionScope.Kind.ACTION,()-> {
                        var cleanupServer=server;
                        Runnable cleanup=()-> {
                            if(owner==scope)stop();
                        }
                        ;
                        if(cleanupServer.isSameThread())cleanup.run();
                        else if(!ScriptDispatch.submit(cleanupServer,cleanup))throw new IllegalStateException("AI cleanup dispatch full");
                    }
                    );
                }
                if(scope.cancelled()||owner!=scope) {
                    stop();
                    return false;
                }
                // Accept an owned intent even when this tick's path budget is exhausted.
                // The mob's goal drains it fairly; closing the scope cancels it before execution.
                due=((ServerLevel)mob.level()).getGameTime()+1;
                return true;
            }
            void update(long time) {
                if(owner==null||target==null)return;
                if(owner.cancelled()||target.isRemoved()||target.level()!=mob.level()) {
                    stop();
                    return;
                }
                if(time>=due&&budget.pathBudget(mob.getUUID())) {
                    due=time+10;
                    try {
                        mob.getNavigation().moveTo(target,speed);
                    }
                    catch(RuntimeException error) {
                        owner.fail(error);
                    }
                }
            }
            void stop() {
                if(owner==null&&target==null)return;
                var previousOwner=owner;
                var previousLease=lease;
                target=null;
                owner=null;
                lease=null;
                try {
                    mob.getNavigation().stop();
                }
                catch(RuntimeException error) {
                    if(fault==null) {
                        fault=error;
                        Mared.LOGGER.warn("[genesis-ai] navigation cleanup failed for {}",mob.getUUID(),error);
                    }
                    if(previousOwner!=null)previousOwner.fail(error);
                }
                finally {
                    if(previousLease!=null)previousLease.close();
                }
            }
        }
    }
}
