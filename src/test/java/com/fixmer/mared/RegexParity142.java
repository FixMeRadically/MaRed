package com.fixmer.mared;
import java.util.*;import java.util.regex.*;import com.fixmer.genesis.technology.expression.ValueLimits140;
public class RegexParity142{
 public static void main(String[]args){var random=new Random(142);int checked=0;String[] patterns={"(a)(b)","(?<letter>a)(b?)","(a*)(b?)","(a|b)([ab])","(a)?(b)","()()"};String[] replacements={"$2$1","$0$0","$12","$01","$09","\\$1","\\\\","literal","${letter}","$99","$","\\"};
 for(int n=0;n<3000;n++){StringBuilder text=new StringBuilder();for(int i=random.nextInt(25);i>0;i--)text.append("ab ".charAt(random.nextInt(3)));String p=patterns[random.nextInt(patterns.length)],replacement=replacements[random.nextInt(replacements.length)],expected=null,actual=null;Class<?> error=null,other=null;try{expected=text.toString().replaceAll(p,replacement);}catch(RuntimeException e){error=e.getClass();}try{actual=ValueLimits140.replaceAll(text.toString(),p,replacement);}catch(RuntimeException e){other=e.getClass();}if(!Objects.equals(expected,actual)||(error==null)!=(other==null))throw new AssertionError("Mismatch pattern="+p+", replacement="+replacement+", expected="+expected+", actual="+actual+", error="+error+", other="+other);checked++;}System.out.println("Regex parity passed="+checked);
 }
}
