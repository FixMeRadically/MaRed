package com.fixmer.mared.technology.server.setup;
import com.google.gson.*;import java.util.*;import java.util.regex.*;
/** Scalar form fields. Preserve comments/order in text formats; complex values remain available as raw text. */
public final class ConfigValues156 {
 public record Value(String key,String value){}
 private ConfigValues156(){}
 public static List<Value> list(String path,String text){
  var out=new ArrayList<Value>();if(path.endsWith(".yaml")||path.endsWith(".yml")||text.contains("\"\"\"")||text.contains("'''"))return List.of();if(path.endsWith(".json")){try{flatten(JsonParser.parseString(text),"",out);}catch(RuntimeException ignored){}return List.copyOf(out);}
  String table="";for(String line:text.split("\\R",-1)){
   String trimmed=line.trim();if(trimmed.startsWith("[")&&trimmed.endsWith("]")){table=trimmed.substring(1,trimmed.length()-1);continue;}
   var m=Pattern.compile("^\\s*([A-Za-z0-9_.-]+)\\s*([=:])\\s*(.*?)\\s*$").matcher(line);if(m.matches()&&!trimmed.startsWith("#")&&!trimmed.startsWith("!")&&!trimmed.startsWith(";"))out.add(new Value(table.isEmpty()?m.group(1):table+"/"+m.group(1),m.group(3)));
  }return List.copyOf(out);
 }
 private static void flatten(JsonElement node,String prefix,List<Value> out){if(node.isJsonObject()){for(var e:node.getAsJsonObject().entrySet())flatten(e.getValue(),prefix.isEmpty()?escape(e.getKey()):prefix+"/"+escape(e.getKey()),out);}else if(node.isJsonPrimitive()||node.isJsonNull())out.add(new Value(prefix,node.toString()));}
 private static String escape(String key){return key.replace("~","~0").replace("/","~1");}
 public static String replace(String path,String text,String key,String value){
  if(value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IllegalArgumentException("SERVER156_VALUE_NEWLINE");
  if(path.endsWith(".json")){var root=JsonParser.parseString(text);String[] parts=key.split("/",-1);JsonObject node=root.getAsJsonObject();for(int i=0;i<parts.length-1;i++)node=node.getAsJsonObject(parts[i].replace("~1","/").replace("~0","~"));String leaf=parts[parts.length-1].replace("~1","/").replace("~0","~");var parsed=JsonParser.parseString(value);if(!parsed.isJsonPrimitive()&&!parsed.isJsonNull())throw new IllegalArgumentException("SERVER156_SCALAR_REQUIRED");node.add(leaf,parsed);return new GsonBuilder().setPrettyPrinting().create().toJson(root)+"\n";}
  String newline=text.contains("\r\n")?"\r\n":"\n";String[] lines=text.split("\\r?\\n",-1);String table="";int found=-1;String replacement="";
  for(int i=0;i<lines.length;i++){
   String trimmed=lines[i].trim();if(trimmed.startsWith("[")&&trimmed.endsWith("]")){table=trimmed.substring(1,trimmed.length()-1);continue;}
   var m=Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+)(\\s*[=:]\\s*)(.*?)\\s*$").matcher(lines[i]);if(!m.matches())continue;String candidate=table.isEmpty()?m.group(2):table+"/"+m.group(2);
   if(candidate.equals(key)){if(found>=0)throw new IllegalArgumentException("SERVER156_DUPLICATE_KEY");found=i;replacement=m.group(1)+m.group(2)+m.group(3)+value;}
  }if(found<0)throw new IllegalArgumentException("SERVER156_KEY_MISSING");lines[found]=replacement;return String.join(newline,lines);
 }
}
