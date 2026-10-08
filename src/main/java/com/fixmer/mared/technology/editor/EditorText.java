package com.fixmer.mared.technology.editor;
import com.fixmer.mared.MaredLang;
import java.util.*;
/** UI translation keys only; command names and serialized metadata remain untouched. */
public final class EditorText {
    private EditorText() {}
    private static final Map<String,String> keys=new HashMap<>();
    public static String translate(String text){
        String key=keys.computeIfAbsent(text,value->"mared.editor."+value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$",""));
        String result=MaredLang.get(key);return result.equals(key)?text:result;
    }
}
