package com.fixmer.mared.commands.input;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Реестр действий игрока.
 *
 * Не трогает клавиши, которые не нажимал скрипт. Если скрипт не вызывал
 * move forward on — keyUp игрока не трогается, игрок ходит свободно.
 */
public final class MaredActionRegistry {

    private MaredActionRegistry() {}

    public enum Action {
        FORWARD, BACK, LEFT, RIGHT,
        SNEAK, SPRINT, JUMP
    }

    private static final Set<Action> PRESSED = EnumSet.noneOf(Action.class);
    private static final Set<Action> OURS    = EnumSet.noneOf(Action.class);

    private static Float pendingYaw = null;
    private static Float pendingPitch = null;

    private static boolean pendingJump   = false;
    private static boolean pendingAttack = false;
    private static boolean pendingUse    = false;
    private static boolean pendingDrop   = false;
    private static boolean pendingSwap   = false;

    /** Флаг: на следующем applyTick() отпустить все OURS и очистить. */
    private static boolean releaseOurKeysPending = false;

    // ============================================================
    //  Публичный API
    // ============================================================

    public static void press(Action a) {
        PRESSED.add(a);
        OURS.add(a);
    }

    public static void release(Action a) {
        PRESSED.remove(a);
        OURS.add(a);
    }

    public static void toggle(Action a, boolean on) {
        if (on) PRESSED.add(a); else PRESSED.remove(a);
        OURS.add(a);
    }

    public static boolean isPressed(Action a) { return PRESSED.contains(a); }

    public static void stopAll() {
        PRESSED.clear();
        pendingYaw = null;
        pendingPitch = null;
        pendingJump = false;
        pendingAttack = false;
        pendingUse = false;
        pendingDrop = false;
        pendingSwap = false;
        releaseOurKeysPending = true;
    }

    public static void lookAt(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;

        double dx = x - p.getX();
        double dy = y - (p.getY() + p.getEyeHeight());
        double dz = z - p.getZ();

        double horiz = Math.sqrt(dx * dx + dz * dz);
        pendingYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pendingPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
    }

    public static void setLook(float yaw, float pitch) {
        pendingYaw = yaw;
        pendingPitch = pitch;
    }

    public static void queueJump()   { pendingJump = true; }
    public static void queueAttack() { pendingAttack = true; }
    public static void queueUse()    { pendingUse = true; }
    public static void queueDrop()   { pendingDrop = true; }
    public static void queueSwap()   { pendingSwap = true; }

    public static void selectSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (slot < 0 || slot > 8) return;
        p.getInventory().selected = slot;
    }

    // ============================================================
    //  Tick
    // ============================================================

    public static void applyTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        LocalPlayer p = mc.player;
        if (p == null) return;

        if (releaseOurKeysPending) {
            releaseOurKeysPending = false;
            setKey(mc.options.keyUp,     false);
            setKey(mc.options.keyDown,   false);
            setKey(mc.options.keyLeft,   false);
            setKey(mc.options.keyRight,  false);
            setKey(mc.options.keyShift,  false);
            setKey(mc.options.keySprint, false);
            setKey(mc.options.keyJump,   false);
            OURS.clear();
            return;
        }

        if (pendingYaw != null) { p.setYRot(pendingYaw); pendingYaw = null; }
        if (pendingPitch != null) { p.setXRot(pendingPitch); pendingPitch = null; }

        if (OURS.contains(Action.FORWARD)) setKey(mc.options.keyUp,     PRESSED.contains(Action.FORWARD));
        if (OURS.contains(Action.BACK))    setKey(mc.options.keyDown,   PRESSED.contains(Action.BACK));
        if (OURS.contains(Action.LEFT))    setKey(mc.options.keyLeft,   PRESSED.contains(Action.LEFT));
        if (OURS.contains(Action.RIGHT))   setKey(mc.options.keyRight,  PRESSED.contains(Action.RIGHT));
        if (OURS.contains(Action.SNEAK))   setKey(mc.options.keyShift,  PRESSED.contains(Action.SNEAK));
        if (OURS.contains(Action.SPRINT))  setKey(mc.options.keySprint, PRESSED.contains(Action.SPRINT));
        if (OURS.contains(Action.JUMP) || pendingJump)
            setKey(mc.options.keyJump, PRESSED.contains(Action.JUMP) || pendingJump);

        if (pendingJump) pendingJump = false;

        if (pendingAttack) {
            pendingAttack = false;
            try {
                if (mc.gameMode != null) {
                    if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                        mc.gameMode.attack(p, ehr.getEntity());
                    } else {
                        mc.gameMode.attack(p, null);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (pendingUse) {
            pendingUse = false;
            try {
                if (mc.gameMode != null) {
                    mc.gameMode.useItem(p, net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            } catch (Throwable ignored) {}
        }

        if (pendingDrop) {
            pendingDrop = false;
            try { p.drop(false); } catch (Throwable ignored) {}
        }

        if (pendingSwap) {
            pendingSwap = false;
            try {
                if (p.connection != null) p.connection.sendCommand("minecraft:swap_offhand");
            } catch (Throwable ignored) {}
        }
    }

    private static void setKey(KeyMapping mapping, boolean down) {
        if (mapping == null) return;
        if (mapping.isDown() == down) return;
        mapping.setDown(down);
    }

    public static int pressedCount() { return PRESSED.size(); }

    public static String describeState() {
        if (PRESSED.isEmpty()) return "idle";
        StringBuilder sb = new StringBuilder();
        for (Action a : PRESSED) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(a.name().toLowerCase());
        }
        return sb.toString();
    }
}