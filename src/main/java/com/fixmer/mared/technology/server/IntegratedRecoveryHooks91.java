package com.fixmer.mared.technology.server;
import com.fixmer.genesis.supervisor.*;import com.fixmer.mared.Mared;
import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.fml.loading.FMLPaths;import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
/** Before integrated level loading. No client linkage; dedicated servers keep their own protocol. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class IntegratedRecoveryHooks91 {
 private IntegratedRecoveryHooks91(){}
 @SubscribeEvent public static void before(ServerAboutToStartEvent event){if(event.getServer().isDedicatedServer())return;
  try{var d=new ModDeployment(FMLPaths.GAMEDIR.get());var view=d.recovery91();String trial=System.getenv("GENESIS_MOD_TRIAL");
   boolean owned="1".equals(System.getenv("GENESIS_SUPERVISOR"))&&trial!=null&&trial.equals(view.transaction());
   if(view.blocked()&&!(owned&&view.needsChoice()))throw new IllegalStateException("RECOVERY91_REVIEW_REQUIRED");
   if(trial!=null)d.protectTrial91(trial);
  }catch(java.io.IOException failed){throw new IllegalStateException("RECOVERY91_REVIEW_REQUIRED",failed);}
 }
}
