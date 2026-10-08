package com.fixmer.mared.technology.links;
import java.util.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import com.fixmer.genesis.technology.links.ScriptExports.*;
/** IDs are permanent; installing the example never silently overwrites a user's script. */
public final class GenesisAiDemo {
    private GenesisAiDemo() {
    }
    public static final UUID MODULE=UUID.fromString("c817e3e5-9ad0-4e80-b655-21c771b88701"),CONDITION=UUID.fromString("58e67a0c-2a83-43ed-8759-0d1f48f57002"),ACTION=UUID.fromString("aa678e20-5297-4d58-8b0c-5b6d20f13003"),PROFILE=UUID.fromString("62e9c9a8-4e26-4b29-966e-a85154f23004"),C_BIND=UUID.fromString("f1e723a5-4fa9-4d30-b5c7-dc3657a9e005"),A_BIND=UUID.fromString("ba137c6b-980e-437b-b185-8c0278be5006");
    public static AiProfile profile() {
        return new AiProfile(PROFILE,"Follow nearest player","genesis.ai.follow",false,10,32,C_BIND,A_BIND);
    }
    public static Binding condition() {
        return new Binding(C_BIND,"ai:"+PROFILE,"condition",new Reference(MODULE,CONDITION,Kind.CONDITION),Map.of("distance","$distance","stopDistance","3"));
    }
    public static Binding action() {
        return new Binding(A_BIND,"ai:"+PROFILE,"action",new Reference(MODULE,ACTION,Kind.ACTION),Map.of("target","$target","speed","1.0"));
    }
}
