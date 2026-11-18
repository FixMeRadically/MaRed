package com.fixmer.genesis.technology.workspace.live122;

import java.util.*;
import java.util.function.LongSupplier;

/** Bounded transient pointers, with modest extrapolation-free smoothing in the receiver's layout. */
public final class PointerView149 {
    public record Row(String actor,String path,String label,int rgb,String anchor,int dx,int dy){}
    public static final class Motion {
        private long lastFrame;
        private String surface;
        private double x,y;

        public int[] position(String surface,double targetX,double targetY,long now,double cadence) {
            double dt=(now-lastFrame)/1e9;
            if(!surface.equals(this.surface)||dt>.25||Math.hypot(targetX-x,targetY-y)>120){
                x=targetX;y=targetY;this.surface=surface;
            }else{
                double weight=-Math.expm1(-Math.max(0,dt)/Math.clamp(cadence*.65,.025,.08));
                x+=(targetX-x)*weight;y+=(targetY-y)*weight;
            }
            lastFrame=now;
            return new int[]{(int)Math.round(x),(int)Math.round(y)};
        }
    }
    private static final class Entry{
        Row row;long at;double cadence=.05;
        final Motion[] panes={new Motion(),new Motion()};
        Entry(Row row,long at){this.row=row;this.at=at;}
    }
    private final Map<String,Entry> peers=new HashMap<>();private final LongSupplier clock;
    public PointerView149(LongSupplier clock){this.clock=Objects.requireNonNull(clock);}
    private void prune(){long now=clock.getAsLong();peers.values().removeIf(e->now-e.at>2_000_000_000L);}
    public void snapshot(String rows){var visible=new HashSet<String>();for(String row:rows.lines().toList()){String actor=accept(row);if(actor!=null)visible.add(actor);}peers.keySet().retainAll(visible);}
    public void delta(String rows){for(String row:rows.lines().toList()){if(row.indexOf('|')<0){peers.remove(row);continue;}accept(row);}prune();}
    private String accept(String wire){try{
        String[] p=wire.split("\\|",-1);if(p.length!=8)return null;UUID.fromString(p[0]);int color=Integer.parseInt(p[3]),dx=Integer.parseInt(p[5]),dy=Integer.parseInt(p[6]);
        com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.path(p[1]);WorkspaceCodec122.id(p[4]);
        if(color<0||color>0xffffff||dx< -64||dx>64||dy<0||dy>16||p[2].length()>48||p[2].chars().anyMatch(Character::isISOControl))return null;
        Row row=new Row(p[0],p[1],p[2],color,p[4],dx,dy);Entry old=peers.get(p[0]);long now=clock.getAsLong();
        if(old==null){prune();if(peers.size()>=64)return null;peers.put(row.actor,new Entry(row,now));}else{if(!old.row.equals(row)){double gap=(now-old.at)/1e9;if(gap>0&&gap<.5)old.cadence=Math.max(.04,Math.min(.12,old.cadence*.5+gap*.5));}old.row=row;old.at=now;}return row.actor;
    }catch(RuntimeException ignored){return null;}}
    public List<Row> visible(){prune();return peers.values().stream().map(e->e.row).sorted(Comparator.comparing(Row::actor)).toList();}
    public int[] position(String actor,String surface,double targetX,double targetY){
        return position(actor,0,surface,targetX,targetY);
    }
    public int[] position(String actor,int pane,String surface,double x,double y){
        Entry entry=peers.get(actor);
        if(entry==null)return new int[]{(int)Math.round(x),(int)Math.round(y)};
        return entry.panes[pane].position(entry.row.path+"|"+surface,x,y,clock.getAsLong(),entry.cadence);
    }
}
