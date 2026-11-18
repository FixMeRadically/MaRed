package com.fixmer.mared.technology.server.consent;

import com.fixmer.genesis.technology.server.waiting.GameConsentBridge.View;
import com.fixmer.mared.Mared;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import java.util.function.Consumer;

/** Optional play channels: no client classes reachable from common registration. */
@EventBusSubscriber(modid=Mared.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class GenesisConsentPayloads {
    private GenesisConsentPayloads(){}
    public static volatile Consumer<Offer> clientSink=offer->{};
    public record Offer(View view) implements CustomPacketPayload {
        public static final Type<Offer> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"consent_offer57"));
        public static final StreamCodec<FriendlyByteBuf,Offer> CODEC=new StreamCodec<>() {
            public Offer decode(FriendlyByteBuf b){return new Offer(new View(b.readUUID(),b.readUUID(),b.readUtf(64),b.readUUID(),b.readLong(),b.readLong(),b.readUtf(8),b.readUtf(9),b.readBoolean(),b.readBoolean()));}
            public void encode(FriendlyByteBuf b,Offer p){View v=p.view();b.writeUUID(v.instance());b.writeUUID(v.update());b.writeUtf(v.packageSha256(),64);b.writeUUID(v.offer());b.writeLong(v.revision());b.writeLong(v.remainingMillis());b.writeUtf(v.decision(),8);b.writeUtf(v.phase(),9);b.writeBoolean(v.paused());b.writeBoolean(v.disconnectRequested());}
        };
        public Offer {Objects.requireNonNull(view);}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Choice(UUID update,String packageSha256,UUID offer,UUID request,boolean accept) implements CustomPacketPayload {
        public static final Type<Choice> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Mared.MOD_ID,"consent_choice57"));
        public static final StreamCodec<FriendlyByteBuf,Choice> CODEC=new StreamCodec<>() {
            public Choice decode(FriendlyByteBuf b){return new Choice(b.readUUID(),b.readUtf(64),b.readUUID(),b.readUUID(),b.readBoolean());}
            public void encode(FriendlyByteBuf b,Choice p){b.writeUUID(p.update());b.writeUtf(p.packageSha256(),64);b.writeUUID(p.offer());b.writeUUID(p.request());b.writeBoolean(p.accept());}
        };
        public Choice {Objects.requireNonNull(update);Objects.requireNonNull(offer);Objects.requireNonNull(request);if(!packageSha256.matches("[a-f0-9]{64}")||request.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid choice");}
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("57").optional();
        registrar.playToClient(Offer.TYPE,Offer.CODEC,(payload,context)->clientSink.accept(payload));
        // NeoForge default handler runs on the server main thread. Identity comes ONLY from its context.
        registrar.playToServer(Choice.TYPE,Choice.CODEC,(payload,context)->{
            if(context.player() instanceof ServerPlayer player&&!com.fixmer.mared.technology.server.firstlaunch.FirstLaunchServer58.choose(player,payload))GenesisConsentServer.choose(player,payload);
        });
    }
}
