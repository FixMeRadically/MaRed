package com.fixmer.genesis.technology.workspace.live122;
import java.util.*;
/** Stable participant order, one rotating color per file, shared by every file-tree renderer. */
public final class WorkspaceActivity152 {
    private WorkspaceActivity152(){}
    public record Person(String actor,String label,int rgb,String tool){}
    public static List<Person> people(String path,String carets,String models){var people=new TreeMap<String,Person>();
        for(String row:carets.lines().toList()){String[] f=row.split("\\|",-1);if(f.length!=6||!path.equals(f[1]))continue;try{int rgb=Integer.parseInt(f[3]);if(rgb>=0&&rgb<=0xffffff)people.put(f[0],new Person(f[0],f[2],rgb,"Code"));}catch(NumberFormatException ignored){}}
        for(String row:models.lines().toList()){String[] f=row.split("\\|",-1);if(f.length!=4||!path.equals(f[2]))continue;try{int rgb=Integer.parseInt(f[3]);if(rgb>=0&&rgb<=0xffffff)people.put(f[0],new Person(f[0],f[1],rgb,"Blockbench"));}catch(NumberFormatException ignored){}}
        return List.copyOf(people.values());
    }
    public static int color(List<Person> people,long nanos){if(people.isEmpty())return 0;return people.get((int)Math.floorMod(Math.floorDiv(nanos,1_500_000_000L),(long)people.size())).rgb;}
}
