package com.fixmer.genesis.technology.editor;
import java.util.Objects;
import java.util.function.ToIntFunction;
/** Fit both columns without splitting a Unicode code point or painting one column over the other. */
public final class MenuText154 {
 private MenuText154(){}
 public record Columns(String left,String right,int rightOffset){}
 public static String fit(String text,int width,ToIntFunction<String> measure){
  Objects.requireNonNull(text);if(width<=0)return "";if(measure.applyAsInt(text)<=width)return text;
  String dots="...";int available=width-measure.applyAsInt(dots);if(available<0)return "";
  int low=0,high=text.codePointCount(0,text.length());
  while(low<high){int mid=(low+high+1)/2;int end=text.offsetByCodePoints(0,mid);if(measure.applyAsInt(text.substring(0,end))<=available)low=mid;else high=mid-1;}
  return text.substring(0,text.offsetByCodePoints(0,low))+dots;
 }
 public static Columns columns(String label,int width,ToIntFunction<String> measure){
  int tab=label.indexOf('\t');if(tab<0)return new Columns(fit(label,width,measure),"",width);
  String right=fit(label.substring(tab+1),Math.max(0,width/2),measure);int rw=measure.applyAsInt(right);
  return new Columns(fit(label.substring(0,tab),Math.max(0,width-rw-(rw==0?0:12)),measure),right,Math.max(0,width-rw));
 }
}
