package com.fixmer.genesis.supervisor;

/** UTF-16 size bounds without cutting a valid supplementary character in half. */
final class DiagnosticText {
    private DiagnosticText() {}
    static String prefix(String text,int maxUnits) {
        if(maxUnits<0)throw new IllegalArgumentException("Negative text bound");
        if(text.length()<=maxUnits)return text;
        int end=maxUnits;
        if(end>0&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))end--;
        return text.substring(0,end);
    }
}
