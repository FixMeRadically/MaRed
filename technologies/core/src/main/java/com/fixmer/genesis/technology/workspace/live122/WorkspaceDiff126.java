package com.fixmer.genesis.technology.workspace.live122;

import java.util.*;

/** Bounded line diff. Long common edges are trimmed before allocating the LCS matrix. */
public final class WorkspaceDiff126 {
    public record Line(char kind,int before,int after,String text){}
    public record Result(List<Line> lines,boolean truncated){public Result{lines=List.copyOf(lines);}}
    private WorkspaceDiff126(){}
    private static String[] lines(String text){return text.isEmpty()?new String[0]:text.split("\n",-1);}
    public static Result compare(String before,String after){
        if(before.length()>32768||after.length()>32768)throw new IllegalArgumentException("LIMIT");
        String[] a=lines(before),b=lines(after);var result=new ArrayList<Line>();
        if(a.length<=400&&b.length<=400){middle(a,b,0,a.length,b.length,result);return new Result(result,false);}
        int prefix=0,suffix=0;
        while(prefix<a.length&&prefix<b.length&&a[prefix].equals(b[prefix]))prefix++;
        while(suffix<a.length-prefix&&suffix<b.length-prefix&&a[a.length-1-suffix].equals(b[b.length-1-suffix]))suffix++;
        int endA=a.length-suffix,endB=b.length-suffix;
        if(endA-prefix>400||endB-prefix>400){
            for(int i=prefix;i<Math.min(prefix+250,endA);i++)result.add(new Line('-',i+1,0,a[i]));
            for(int j=prefix;j<Math.min(prefix+250,endB);j++)result.add(new Line('+',0,j+1,b[j]));
            return new Result(result,true);
        }
        for(int i=Math.max(0,prefix-3);i<prefix;i++)result.add(new Line(' ',i+1,i+1,a[i]));
        middle(a,b,prefix,endA,endB,result);
        for(int k=0;k<Math.min(3,suffix);k++)result.add(new Line(' ',endA+k+1,endB+k+1,a[endA+k]));
        return new Result(result,prefix>3||suffix>3);
    }
    private static void middle(String[] a,String[] b,int start,int endA,int endB,List<Line> result){
        int n=endA-start,m=endB-start;int[][] same=new int[n+1][m+1];
        for(int i=n-1;i>=0;i--)for(int j=m-1;j>=0;j--)same[i][j]=a[start+i].equals(b[start+j])?same[i+1][j+1]+1:Math.max(same[i+1][j],same[i][j+1]);
        int i=0,j=0;
        while(i<n||j<m){
            if(i<n&&j<m&&a[start+i].equals(b[start+j])){result.add(new Line(' ',start+i+1,start+j+1,a[start+i]));i++;j++;}
            else if(i<n&&(j==m||same[i+1][j]>=same[i][j+1])){result.add(new Line('-',start+i+1,0,a[start+i]));i++;}
            else{result.add(new Line('+',0,start+j+1,b[start+j]));j++;}
        }
    }
}
