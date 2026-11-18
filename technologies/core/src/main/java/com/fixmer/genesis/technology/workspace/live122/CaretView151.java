package com.fixmer.genesis.technology.workspace.live122;

import java.util.*;
import java.util.function.LongSupplier;

/** Small per-actor cursor updates, independently expiring and ordered by sender sequence. */
public final class CaretView151 {
    private record Entry(String row,long sequence,String session,long time){}
    private final Map<String,Entry> entries=new TreeMap<>();
    private final LongSupplier clock;
    public CaretView151(LongSupplier clock){this.clock=Objects.requireNonNull(clock);}
    public void clear(){entries.clear();}
    public void remove(String actor){entries.remove(actor);}
    public void delta(String row,long sequence,String session){
        String[] fields=row.split("\\|",-1);if(fields.length!=1&&fields.length!=6)return;
        try{UUID.fromString(fields[0]);}catch(IllegalArgumentException invalid){return;}
        long now=clock.getAsLong();expire(now);var previous=entries.get(fields[0]);
        if(sequence<=0||session==null||previous!=null&&session.equals(previous.session)&&sequence<=previous.sequence)return;
        if(!entries.containsKey(fields[0])&&entries.size()>=64)return;
        // Keep hidden entries as short-lived sequence tombstones to reject delayed visibility packets.
        entries.put(fields[0],new Entry(fields.length==6?row:"",sequence,session,now));
    }
    public String rows(){expire(clock.getAsLong());return String.join("\n",entries.values().stream().map(Entry::row).filter(row->!row.isEmpty()).toList());}
    private void expire(long now){entries.values().removeIf(entry->now-entry.time>=6_000_000_000L);}
}
