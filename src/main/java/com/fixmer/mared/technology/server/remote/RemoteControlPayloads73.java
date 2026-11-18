package com.fixmer.mared.technology.server.remote;
import com.fixmer.mared.Mared;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class RemoteControlPayloads73 {
 private RemoteControlPayloads73(){}public static volatile Consumer<byte[]> clientSink=b->{};
 public record Config(byte[] bytes) implements CustomPacketPayload {
  public Config{bytes=bytes.clone();if(bytes.length>16384)throw new IllegalArgumentException();}@Override public byte[] bytes(){return bytes.clone();}
  public static final Type<Config> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"remote_control73"));
  public static final StreamCodec<FriendlyByteBuf,Config> CODEC=new StreamCodec<>(){public Config decode(FriendlyByteBuf b){return new Config(b.readByteArray(16384));}public void encode(FriendlyByteBuf b,Config p){b.writeByteArray(p.bytes());}};
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("73").optional().playToClient(Config.TYPE,Config.CODEC,(p,c)->clientSink.accept(p.bytes()));}
}
