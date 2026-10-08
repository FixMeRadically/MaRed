package com.fixmer.mared.technology.links;
import com.google.gson.*;
import com.fixmer.genesis.technology.links.ScriptLinks.*;
import com.fixmer.genesis.technology.links.ScriptExports;
import com.fixmer.genesis.technology.links.ScriptExports.*;
import java.util.*;
/** Versioned JSON adapter. Broken documents are reported, never replaced with an empty file. */
public final class ScriptLinkCodec {
    private ScriptLinkCodec() {
    }
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static Project read(String text) {
        if(text==null)return Project.empty();
        if(text.length()>1_048_576)throw new IllegalArgumentException("Link document exceeds 1 MiB");
        var root=JsonParser.parseString(text).getAsJsonObject();
        if(integer(root,"version")!=1)throw new IllegalArgumentException("Unsupported link document version");
        var bindings=new ArrayList<Binding>();
        var profiles=new ArrayList<AiProfile>();
        var bs=root.getAsJsonArray("bindings");
        var ps=root.getAsJsonArray("profiles");
        if(bs.size()>4096||ps.size()>256)throw new IllegalArgumentException("Project limit");
        for(var value:bs) {
            var b=value.getAsJsonObject();
            var args=new LinkedHashMap<String,String>();
            b.getAsJsonObject("arguments").entrySet().forEach(e-> {
                if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Arguments must be expression strings");
                args.put(e.getKey(),e.getValue().getAsString());
            }
            );
            var ref=new Reference(uuid(b,"module"),uuid(b,"export"),Kind.valueOf(b.get("kind").getAsString()));
            bindings.add(new Binding(uuid(b,"id"),b.get("consumer").getAsString(),b.get("slot").getAsString(),ref,args));
        }
        for(var value:ps) {
            var p=value.getAsJsonObject();
            profiles.add(new AiProfile(uuid(p,"id"),p.get("title").getAsString(),p.get("tag").getAsString(),bool(p,"enabled"),integer(p,"interval"),p.get("range").getAsDouble(),uuid(p,"condition"),uuid(p,"action")));
        }
        return new Project(bindings,profiles);
    }
    private static int integer(JsonObject o,String key) {
        var v=o.get(key);
        if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Expected integer: "+key);
        try {
            return new java.math.BigDecimal(v.getAsString()).intValueExact();
        }
        catch(ArithmeticException error) {
            throw new IllegalArgumentException("Expected integer: "+key,error);
        }
    }
    private static boolean bool(JsonObject o,String key) {
        var v=o.get(key);
        if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Expected boolean: "+key);
        return v.getAsBoolean();
    }
    private static UUID uuid(JsonObject object,String key) {
        return ScriptExports.uuid(object.get(key).getAsString());
    }
    public static String write(Project project) {
        var root=new JsonObject();
        root.addProperty("version",1);
        var bindings=new JsonArray();
        var profiles=new JsonArray();
        for(var b:project.bindings()) {
            var v=new JsonObject();
            v.addProperty("id",b.id().toString());
            v.addProperty("consumer",b.consumer());
            v.addProperty("slot",b.slot());
            v.addProperty("module",b.reference().module().toString());
            v.addProperty("export",b.reference().export().toString());
            v.addProperty("kind",b.reference().kind().name());
            var args=new JsonObject();
            b.arguments().forEach(args::addProperty);
            v.add("arguments",args);
            bindings.add(v);
        }
        for(var p:project.profiles()) {
            var v=new JsonObject();
            v.addProperty("id",p.id().toString());
            v.addProperty("title",p.title());
            v.addProperty("tag",p.tag());
            v.addProperty("enabled",p.enabled());
            v.addProperty("interval",p.interval());
            v.addProperty("range",p.range());
            v.addProperty("condition",p.condition().toString());
            v.addProperty("action",p.action().toString());
            profiles.add(v);
        }
        root.add("bindings",bindings);
        root.add("profiles",profiles);
        return JSON.toJson(root)+"\n";
    }
    public static Map<String,String> arguments(String text) {
        var result=new LinkedHashMap<String,String>();
        var value=JsonParser.parseString(text).getAsJsonObject();
        value.entrySet().forEach(e-> {
            if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString())throw new IllegalArgumentException("Use JSON expression strings");
            result.put(e.getKey(),e.getValue().getAsString());
        }
        );
        return result;
    }
    public static String arguments(Map<String,String> args) {
        return JSON.toJson(args).replace("\n","");
    }
}
