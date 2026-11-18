package com.fixmer.mared.technology.server.remote;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.Mared;
import net.minecraft.commands.Commands;import net.minecraft.network.chat.Component;import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.nio.file.Path;import java.util.UUID;
/** Permission4 console/player entry point. Network identity is always taken from the command source. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class ServerCommands103 {
 private ServerCommands103(){}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("genesisrestart").requires(s->s.hasPermission(4)).executes(ctx->{
  var source=ctx.getSource();String root=System.getenv("GENESIS_DEDICATED74_ROOT");if(root==null){source.sendFailure(Component.translatable("mared.server103.not_managed"));return 0;}
  try{Path path=Path.of(root);var admin=DedicatedAdmin75.open(path);if(!admin.supportsRestart103())throw new IllegalStateException();UUID request=UUID.randomUUID();
   if(source.getEntity() instanceof ServerPlayer p){if(!source.getServer().usesAuthentication()){source.sendFailure(Component.translatable("mared.server103.offline_admin"));return 0;}ServerAccess88.open(path).queue(p.getUUID(),source.hasPermission(4),source.getServer().usesAuthentication(),System.currentTimeMillis(),request,"RESTART","r1");}
   else if(source.getEntity()==null)admin.submit(request,"RESTART","r1");else return 0;
   source.sendSuccess(()->Component.translatable("mared.server103.requested_restart"),false);return 1;
  }catch(Exception refused){source.sendFailure(Component.translatable("mared.server103.command_failed"));return 0;}
 }));}
}
