package com.fixmer.mared.technology.server.remote;

import com.fixmer.genesis.technology.server.remote.RemoteConsent72.*;
import com.fixmer.mared.Mared;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.Consumer;

/** Separate optional channels:57/58 local consent contracts remain unchanged. */
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class RemoteConsentPayloads72 {
    private RemoteConsentPayloads72(){}public static volatile Consumer<Offer> clientSink=offer->{};
    public record Offer(Frame frame) implements CustomPacketPayload {
        public static final Type<Offer> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"remote_offer72"));
        public static final StreamCodec<FriendlyByteBuf,Offer> CODEC=new StreamCodec<>(){
            public Offer decode(FriendlyByteBuf b){return new Offer(new Frame(b.readUUID(),b.readUUID(),b.readUUID(),b.readUUID(),b.readUUID(),b.readUtf(64),b.readUtf(255),b.readUtf(2048),b.readUtf(64),b.readLong(),b.readLong(),b.readLong(),b.readUtf(9),b.readUtf(8)));}
            public void encode(FriendlyByteBuf b,Offer o){var f=o.frame();b.writeUUID(f.instance());b.writeUUID(f.round());b.writeUUID(f.ticket());b.writeUUID(f.release());b.writeUUID(f.distribution());b.writeUtf(f.sha(),64);b.writeUtf(f.game(),255);b.writeUtf(f.endpoint(),2048);b.writeUtf(f.signer(),64);b.writeLong(f.revision());b.writeLong(f.serial());b.writeLong(f.remaining());b.writeUtf(f.phase(),9);b.writeUtf(f.decision(),8);}
        };
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Answer(Choice choice) implements CustomPacketPayload {
        public static final Type<Answer> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"remote_answer72"));
        public static final StreamCodec<FriendlyByteBuf,Answer> CODEC=new StreamCodec<>(){
            public Answer decode(FriendlyByteBuf b){return new Answer(new Choice(b.readUUID(),b.readUUID(),b.readUtf(64),b.readUUID(),b.readUUID(),b.readBoolean()));}
            public void encode(FriendlyByteBuf b,Answer a){var c=a.choice();b.writeUUID(c.round());b.writeUUID(c.release());b.writeUtf(c.sha(),64);b.writeUUID(c.ticket());b.writeUUID(c.request());b.writeBoolean(c.accept());}
        };
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("72").optional();r.playToClient(Offer.TYPE,Offer.CODEC,(p,c)->clientSink.accept(p));
        r.playToServer(Answer.TYPE,Answer.CODEC,(p,c)->{if(c.player() instanceof ServerPlayer player)RemoteConsentServer72.choose(player,p.choice());});
    }
}
