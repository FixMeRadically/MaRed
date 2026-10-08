package com.fixmer.mared.commands.input;

import com.fixmer.genesis.technology.runtime.ExecutionScope;
import com.fixmer.genesis.technology.runtime.OwnedActions;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import java.util.EnumMap;
import java.util.Set;

/** Producers never read the client player. Minecraft effects run only in applyTick on the client. */
public final class MaredActionRegistry {
    private MaredActionRegistry() {}
    public enum Action { FORWARD, BACK, LEFT, RIGHT, SNEAK, SPRINT, JUMP }
    private static final OwnedActions<Action,Runnable> actions=new OwnedActions<>(1024);
    // Client-thread state only; other threads change claims and enqueue actions.
    private static final EnumMap<Action,KeyMapping> applied=new EnumMap<>(Action.class);
    private static final EnumMap<Action,Boolean> previous=new EnumMap<>(Action.class);
    private static boolean jumpPulse;
    public static void observeKey(int key,int scan,int action){
        if(action!=0&&action!=1)return;
        var mc=Minecraft.getInstance();if(mc.options==null)return;
        for(var item:applied.entrySet())if(item.getValue().matches(key,scan))previous.put(item.getKey(),action==1);
    }
    public static void observeMouse(int button,int action){
        if(action!=0&&action!=1)return;
        for(var item:applied.entrySet())if(item.getValue().matchesMouse(button))previous.put(item.getKey(),action==1);
    }
    public static void press(Action a){press(null,a);}public static void press(ExecutionScope scope,Action a){actions.press(scope,a);}
    public static void release(Action a){release(null,a);}public static void release(ExecutionScope scope,Action a){actions.release(scope,a);}
    public static void toggle(Action a,boolean on){toggle(null,a,on);}public static void toggle(ExecutionScope scope,Action a,boolean on){if(on)press(scope,a);else release(scope,a);}
    public static boolean isPressed(Action a){return actions.activeKeys().contains(a);}public static boolean isPressed(ExecutionScope scope,Action a){return actions.pressed(scope,a);}
    public static void stopAll(){actions.stopAll();}public static void stop(ExecutionScope scope){actions.stop(scope);}
    private static void queue(ExecutionScope scope,Runnable effect){actions.enqueue(scope,effect);}
    public static void lookAt(double x,double y,double z){lookAt(null,x,y,z);}
    public static void lookAt(ExecutionScope scope,double x,double y,double z){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Look position must be finite");queue(scope,()->{var p=Minecraft.getInstance().player;if(p==null)return;double dx=x-p.getX(),dy=y-p.getEyeY(),dz=z-p.getZ();p.setYRot((float)Math.toDegrees(Math.atan2(-dx,dz)));p.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));});}
    public static void setLook(float yaw,float pitch){setLook(null,yaw,pitch);}
    public static void setLook(ExecutionScope scope,float yaw,float pitch){if(!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Look angles must be finite");queue(scope,()->{var p=Minecraft.getInstance().player;if(p!=null){p.setYRot(yaw);p.setXRot(Math.max(-90,Math.min(90,pitch)));}});}
    public static void queueJump(){queueJump(null);}public static void queueJump(ExecutionScope scope){queue(scope,()->jumpPulse=true);}
    public static void queueAttack(){queueAttack(null);}public static void queueAttack(ExecutionScope scope){queue(scope,()->{var mc=Minecraft.getInstance();if(mc.gameMode==null||mc.player==null)return;if(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit)mc.gameMode.attack(mc.player,hit.getEntity());else if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)mc.gameMode.startDestroyBlock(hit.getBlockPos(),hit.getDirection());});}
    public static void queueUse(){queueUse(null);}public static void queueUse(ExecutionScope scope){queue(scope,()->{var mc=Minecraft.getInstance();if(mc.gameMode!=null&&mc.player!=null)mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND);});}
    public static void queueDrop(){queueDrop(null);}public static void queueDrop(ExecutionScope scope){queue(scope,()->{var p=Minecraft.getInstance().player;if(p!=null)p.drop(false);});}
    public static void queueSwap(){queueSwap(null);}public static void queueSwap(ExecutionScope scope){queue(scope,()->{var p=Minecraft.getInstance().player;if(p!=null)p.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,net.minecraft.core.BlockPos.ZERO,net.minecraft.core.Direction.DOWN));});}
    public static void selectSlot(int slot){selectSlot(null,slot);}public static void selectSlot(ExecutionScope scope,int slot){if(slot<0||slot>8)throw new IllegalArgumentException("Hotbar slot must be 0..8");queue(scope,()->{var p=Minecraft.getInstance().player;if(p!=null)p.getInventory().selected=slot;});}
    private static KeyMapping mapping(Minecraft mc,Action action){return switch(action){case FORWARD->mc.options.keyUp;case BACK->mc.options.keyDown;case LEFT->mc.options.keyLeft;case RIGHT->mc.options.keyRight;case SNEAK->mc.options.keyShift;case SPRINT->mc.options.keySprint;case JUMP->mc.options.keyJump;};}
    public static void applyTick(){
        var mc=Minecraft.getInstance();if(mc.options==null)return;
        if(mc.player==null){actions.stopAll();restoreAll();jumpPulse=false;return;}
        jumpPulse=false;actions.drain(64,Runnable::run);
        Set<Action> held=actions.activeKeys();
        for(Action action:Action.values()){
            boolean down=held.contains(action)||(action==Action.JUMP&&jumpPulse);
            KeyMapping key=mapping(mc,action);if(key==null)continue;
            KeyMapping old=applied.get(action);
            if(old!=null&&old!=key)restore(action);
            if(down){if(!applied.containsKey(action)){applied.put(action,key);previous.put(action,key.isDown());}key.setDown(true);}
            else if(applied.containsKey(action))restore(action);
        }
    }
    private static void restore(Action action){KeyMapping key=applied.remove(action);Boolean wasDown=previous.remove(action);if(key!=null)key.setDown(Boolean.TRUE.equals(wasDown));}
    private static void restoreAll(){for(Action action:Action.values())restore(action);}
    public static int pressedCount(){return actions.activeKeys().size();}
    public static String describeState(){var keys=actions.activeKeys();return keys.isEmpty()?"idle":keys.toString();}
}
