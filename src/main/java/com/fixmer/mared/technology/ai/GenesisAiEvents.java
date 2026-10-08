package com.fixmer.mared.technology.ai;
import com.fixmer.mared.Mared;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
/** Common game bus; the runtime rejects client levels. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class GenesisAiEvents {
    private GenesisAiEvents() {
    }
    @SubscribeEvent public static void entityTick(EntityTickEvent.Post event) {
        if(event.getEntity() instanceof Mob mob)GenesisAiRuntime.entityTick(mob);
    }
}
