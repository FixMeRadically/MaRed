package com.fixmer.mared.technology.catalog;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.ArgumentType;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;

/** Uses the same registered serializers as Minecraft's command synchronization, including mod serializers. */
public final class MinecraftArgumentMetadata {
    private MinecraftArgumentMetadata() {}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static String describe(ArgumentType<?> parser) {
        String name=parser.getClass().getName();
        try {
            if(!ArgumentTypeInfos.isClassRecognized(parser.getClass()))return name+" (unregistered serializer)";
            var template=ArgumentTypeInfos.unpack(parser);ArgumentTypeInfo info=template.type();
            var properties=new JsonObject();info.serializeToJson(template,properties);
            String json=properties.toString();
            if(json.length()>4096)json=json.substring(0,4096)+" …";
            return name+" ["+BuiltInRegistries.COMMAND_ARGUMENT_TYPE.getKey(info)+"]"+(properties.size()==0?"":"\n"+json);
        }catch(RuntimeException|LinkageError error){return name+" (serializer metadata unavailable)";}
    }
}
