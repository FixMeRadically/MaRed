package com.fixmer.mared.technology.server.remote;
import com.fixmer.mared.Mared;import net.minecraft.network.FriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;import java.util.*;import java.util.function.Consumer;
/** No client-supplied actor, paths, tokens or owner keys. */
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class ServerAccessPayloads88 {
 public static volatile Consumer<View> clientSink=v->{};private ServerAccessPayloads88(){}
 public record Request(UUID request,String operation,UUID target,String role,long revision)implements CustomPacketPayload{
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"server_access88_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=new StreamCodec<>(){public Request decode(FriendlyByteBuf b){return new Request(b.readUUID(),b.readUtf(12),b.readUUID(),b.readUtf(16),b.readLong());}public void encode(FriendlyByteBuf b,Request p){b.writeUUID(p.request());b.writeUtf(p.operation(),12);b.writeUUID(p.target());b.writeUtf(p.role(),16);b.writeLong(p.revision());}};public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public record View(UUID request,byte[] bytes)implements CustomPacketPayload{
  public View{if(bytes.length>32768)throw new IllegalArgumentException();bytes=bytes.clone();}public byte[] bytes(){return bytes.clone();}
  public static final Type<View> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"server_access88_view"));
  public static final StreamCodec<FriendlyByteBuf,View> CODEC=new StreamCodec<>(){public View decode(FriendlyByteBuf b){return new View(b.readUUID(),b.readByteArray(32768));}public void encode(FriendlyByteBuf b,View p){b.writeUUID(p.request());b.writeByteArray(p.bytes());}};public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("88").optional();r.playToClient(View.TYPE,View.CODEC,(p,c)->clientSink.accept(p));r.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)ServerAdmin75.access88(player,p);});}
}
