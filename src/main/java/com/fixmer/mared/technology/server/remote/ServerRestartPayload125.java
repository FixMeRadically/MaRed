package com.fixmer.mared.technology.server.remote;

import com.fixmer.mared.Mared;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;
import java.util.function.Consumer;

/** Same-server return notification only; cannot download mods, exit a client, or change its address. */
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class ServerRestartPayload125 {
    private ServerRestartPayload125(){}
    public static volatile Consumer<UUID> clientSink=id->{};
    public record Notice(UUID request) implements CustomPacketPayload {
        public Notice{if(request==null||request.equals(new UUID(0,0)))throw new IllegalArgumentException("REQUEST");}
        public static final Type<Notice> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"server_restart125"));
        public static final StreamCodec<FriendlyByteBuf,Notice> CODEC=new StreamCodec<>(){
            public Notice decode(FriendlyByteBuf b){return new Notice(b.readUUID());}
            public void encode(FriendlyByteBuf b,Notice p){b.writeUUID(p.request());}
        };
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("125").optional().playToClient(Notice.TYPE,Notice.CODEC,(p,c)->c.enqueueWork(()->clientSink.accept(p.request())));}
}
