package com.fixmer.genesis.technology.scripts;

import java.util.Set;

/** Conservative conventional-source-root inference; custom Gradle sourceSets are not guessed. */
public final class JavaFileTemplate {
    private JavaFileTemplate() {}
    private static final Set<String> RESERVED=Set.of(
        "abstract","assert","boolean","break","byte","case","catch","char","class","const","continue",
        "default","do","double","else","enum","extends","final","finally","float","for","goto","if",
        "implements","import","instanceof","int","interface","long","native","new","package","private",
        "protected","public","return","short","static","strictfp","super","switch","synchronized","this",
        "throw","throws","transient","try","void","volatile","while","true","false","null","_",
        "var","yield","record","sealed","permits");
    private static boolean identifier(String value) {
        if(value.isEmpty()||RESERVED.contains(value))return false;
        int first=value.codePointAt(0);if(!Character.isJavaIdentifierStart(first))return false;
        for(int i=Character.charCount(first);i<value.length();) {
            int code=value.codePointAt(i);if(!Character.isJavaIdentifierPart(code))return false;i+=Character.charCount(code);
        }return true;
    }
    public enum Kind { CLASS, INTERFACE, ENUM, RECORD }
    public static boolean validTypePath(String path){
        if(path==null||!path.endsWith(".java")||path.startsWith("/")||path.contains("\\"))return false;
        String relative=path;for(String root:new String[]{"src/main/java/","src/test/java/","src/","tests/"})if(relative.startsWith(root)){relative=relative.substring(root.length());break;}
        String[] parts=relative.substring(0,relative.length()-5).split("/",-1);
        for(String part:parts)if(!identifier(part))return false;return parts.length>0;
    }
    public static String source(String path){return source(path,Kind.CLASS);}
    public static String source(String path,Kind kind) {
        if(path==null||!path.endsWith(".java"))return "";
        int slash=path.lastIndexOf('/');String name=path.substring(slash+1,path.length()-5);
        if(name.equals("module-info")||name.equals("package-info")||!identifier(name))return "";
        String prefix="";
        for(String root:new String[]{"src/main/java/","src/test/java/","src/","tests/"}) {
            if(path.startsWith(root)) {
                String folder=slash>=root.length()?path.substring(root.length(),slash):"";
                if(!folder.isEmpty()) {
                    String[] parts=folder.split("/",-1);boolean valid=true;
                    for(String part:parts)valid&=identifier(part);
                    if(valid)prefix="package "+String.join(".",parts)+";\n\n";
                }break;
            }
        }
        if(kind==null)throw new IllegalArgumentException("Java type required");
        String declaration=switch(kind){case CLASS->"class "+name;case INTERFACE->"interface "+name;case ENUM->"enum "+name;case RECORD->"record "+name+"()";};
        return prefix+"public "+declaration+" {\n\n}\n";
    }
}
