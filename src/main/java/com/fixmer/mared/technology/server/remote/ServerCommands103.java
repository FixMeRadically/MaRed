package com.fixmer.mared.technology.server.remote;
import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.Mared;
import net.minecraft.commands.Commands;import net.minecraft.network.chat.Component;import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.nio.file.Path;import java.util.UUID;
/** Explicit Maop or genuine console. Vanilla OP and execute-as impersonation do not authorize restart. */
@EventBusSubscriber(modid=Mared.MOD_ID)
public final class ServerCommands103 {
 private ServerCommands103(){}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("genesisrestart").requires(s->com.fixmer.mared.technology.permissions.MaopServer113.source(s,"RESTART")).executes(ctx->{
  var source=ctx.getSource();if(!com.fixmer.mared.technology.permissions.MaopServer113.source(source,"RESTART"))return 0;String root=System.getenv("GENESIS_DEDICATED74_ROOT");if(root==null){source.sendFailure(Component.translatable("mared.server103.not_managed"));return 0;}
  try{Path path=Path.of(root);var admin=DedicatedAdmin75.open(path);if(!admin.supportsRestart103())throw new IllegalStateException();UUID request=UUID.randomUUID();
   if(source.getEntity() instanceof ServerPlayer p){if(!com.fixmer.mared.technology.permissions.MaopServer113.remoteReady(source.getServer())||!source.getServer().usesAuthentication()){source.sendFailure(Component.translatable("mared.server103.offline_admin"));return 0;}ServerAccess88.open(path).queue(p.getUUID(),true,source.getServer().usesAuthentication(),System.currentTimeMillis(),request,"RESTART","r1");}
   else if(com.fixmer.mared.technology.permissions.MaopServer113.console(source))admin.submit(request,"RESTART","r1");else return 0;
   source.sendSuccess(()->Component.translatable("mared.server103.requested_restart"),false);return 1;
  }catch(Exception refused){source.sendFailure(Component.translatable("mared.server103.command_failed"));return 0;}
 }));}
}
