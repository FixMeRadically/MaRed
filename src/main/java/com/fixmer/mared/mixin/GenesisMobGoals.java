package com.fixmer.mared.mixin;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Common-side access; client-only mouse mixins are kept in the client list. */
@Mixin(Mob.class)
public interface GenesisMobGoals {
    @Accessor("goalSelector") GoalSelector genesis$goals();
}
