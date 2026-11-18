package com.fixmer.genesis.technology.server.process;
import java.util.*;
final class ServerJson {
    private ServerJson(){}
    static String quote(String value){var out=new StringBuilder("\"");for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(c<32)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}}}return out.append('"').toString();}
    static String object(Map<String,?> values){var out=new StringBuilder("{");boolean first=true;for(var item:values.entrySet()){if(!first)out.append(',');first=false;Object v=item.getValue();out.append(quote(item.getKey())).append(':').append(v instanceof Boolean||v instanceof Number?v.toString():quote(String.valueOf(v)));}return out.append('}').toString();}
}
