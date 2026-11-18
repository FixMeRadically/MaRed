package com.fixmer.mared.technology.workspace;
import com.fixmer.mared.Mared;import com.fixmer.genesis.technology.workspace.WorkspaceWire112;
import net.minecraft.network.FriendlyByteBuf;import net.minecraft.network.codec.StreamCodec;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;import net.minecraft.resources.ResourceLocation;import net.minecraft.server.level.ServerPlayer;import net.neoforged.bus.api.SubscribeEvent;import net.neoforged.fml.common.EventBusSubscriber;import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;import java.util.function.Consumer;
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class WorkspacePayloads112 {
 private WorkspacePayloads112(){}public static volatile Consumer<Response> clientSink=p->{};
 public record Request(byte[] bytes)implements CustomPacketPayload{
  public Request{if(bytes.length>WorkspaceWire112.MAX_PACKET)throw new IllegalArgumentException();bytes=bytes.clone();}public byte[] bytes(){return bytes.clone();}
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"workspace112_request"));
  public static final StreamCodec<FriendlyByteBuf,Request> CODEC=new StreamCodec<>(){public Request decode(FriendlyByteBuf b){return new Request(b.readByteArray(WorkspaceWire112.MAX_PACKET));}public void encode(FriendlyByteBuf b,Request p){b.writeByteArray(p.bytes());}};public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public record Response(byte[] bytes)implements CustomPacketPayload{
  public Response{if(bytes.length>WorkspaceWire112.MAX_PACKET)throw new IllegalArgumentException();bytes=bytes.clone();}public byte[] bytes(){return bytes.clone();}
  public static final Type<Response> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"workspace112_response"));
  public static final StreamCodec<FriendlyByteBuf,Response> CODEC=new StreamCodec<>(){public Response decode(FriendlyByteBuf b){return new Response(b.readByteArray(WorkspaceWire112.MAX_PACKET));}public void encode(FriendlyByteBuf b,Response p){b.writeByteArray(p.bytes());}};public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){var r=e.registrar("112").optional();r.playToClient(Response.TYPE,Response.CODEC,(p,c)->clientSink.accept(p));r.playToServer(Request.TYPE,Request.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)WorkspaceServer112.receive(player,p.bytes());});}
}
