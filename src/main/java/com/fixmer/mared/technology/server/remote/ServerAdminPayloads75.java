package com.fixmer.mared.technology.server.remote;
import com.fixmer.mared.Mared;
import net.minecraft.network.FriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;import java.util.*;import java.util.function.Consumer;
/** Optional admin75 channels; no key/path/argv payload. */
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class ServerAdminPayloads75 {
 public static volatile Consumer<View> clientSink=v->{};private ServerAdminPayloads75(){}
 public record Request(UUID request,String action,String label)implements CustomPacketPayload{
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"server_admin75_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=new StreamCodec<>(){public Request decode(FriendlyByteBuf b){return new Request(b.readUUID(),b.readUtf(24),b.readUtf(40));}public void encode(FriendlyByteBuf b,Request p){b.writeUUID(p.request());b.writeUtf(p.action(),24);b.writeUtf(p.label(),40);}};
  public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public record View(UUID request,byte[] bytes)implements CustomPacketPayload{
  public View{if(bytes.length>32768)throw new IllegalArgumentException();bytes=bytes.clone();}public byte[] bytes(){return bytes.clone();}
  public static final Type<View> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"server_admin75_view"));
  public static final StreamCodec<FriendlyByteBuf,View> CODEC=new StreamCodec<>(){public View decode(FriendlyByteBuf b){return new View(b.readUUID(),b.readByteArray(32768));}public void encode(FriendlyByteBuf b,View p){b.writeUUID(p.request());b.writeByteArray(p.bytes());}};
  public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("75").optional();r.playToClient(View.TYPE,View.CODEC,(p,c)->clientSink.accept(p));r.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)ServerAdmin75.receive(player,p);});}
}
