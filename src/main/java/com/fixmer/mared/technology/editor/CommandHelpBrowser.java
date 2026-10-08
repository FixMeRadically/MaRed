package com.fixmer.mared.technology.editor;

import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.gui2.framework.core.*;
import com.fixmer.mared.gui2.studio.events.*;
import com.fixmer.mared.gui2.studio.panels.inspector.InspectorComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.fixmer.mared.technology.editor.GenesisEditorVisuals.*;

/** Right-hand browser. Selecting a command keeps its list, search and source controls available. */
public final class CommandHelpBrowser extends MaredComponent implements NarratableComponent {
    private record Choice(String value, String label) {}
    private final StudioEventBus bus;
    private final InspectorComponent inspector;
    private MaredCommandRegistry.Source source=MaredCommandRegistry.Source.MC;
    private List<MaredCommandRegistry.CommandInfo> rows=List.of();
    private List<String> categories=List.of();
    private List<Choice> choices=List.of();
    private String category, selected, commandQuery="";
    private EditBox filter;
    private long revision=-1;
    private int scroll, listBottom, listRight;
    private double split=.40,wideSplit=.36;
    private boolean wide;
    public void setWide(boolean value){wide=value;}
    private boolean wideDetails(){return wide&&bounds.width()>=360&&selected!=null&&!categoriesOpen;}
    private boolean resizing, categoriesOpen;

    public CommandHelpBrowser(StudioEventBus bus, InspectorComponent inspector) {
        this.bus=bus;
        this.inspector=inspector;
    }

    public void setSource(MaredCommandRegistry.Source source) {
        closeCategories();
        this.source=source;
        selected=null;
        category=null;
        scroll=0;
        reload();
        bus.publish(new StudioEvents.CommandSelectedEvent(null));
    }

    public void cancelInteraction(){resizing=false;if(filter!=null)filter.setFocused(false);}

    @Override public boolean focusable() { return true; }
    @Override public void onFocusGained() { if(filter!=null)filter.setFocused(true); }
    @Override public void onFocusLost() { if(filter!=null)filter.setFocused(false); }
    @Override public Component narrationText() { return Component.literal(EditorText.translate("Help")+", "+source+", "+rowCount()); }

    private void reload() {
        categories=MaredCommandRegistry.categories(source);
        if(category!=null&&!categories.contains(category))category=null;
        String query=filter==null?"":filter.getValue();
        rows=MaredCommandRegistry.search(categoriesOpen?commandQuery:query,source,category);
        var filtered=new ArrayList<Choice>();
        filtered.add(new Choice(null,EditorText.translate("All namespaces")));
        String lower=query.toLowerCase(Locale.ROOT);
        for(String value:categories)if(!categoriesOpen||value.toLowerCase(Locale.ROOT).contains(lower))filtered.add(new Choice(value,value));
        choices=List.copyOf(filtered);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,rowCount()-visibleRows())));
        long nextRevision=MaredCommandRegistry.revision();
        if(selected!=null) {
            var updated=MaredCommandRegistry.findByName(selected,source);
            if(updated==null){selected=null;bus.publish(new StudioEvents.CommandSelectedEvent(null));}
            else if(nextRevision!=revision)bus.publish(new StudioEvents.CommandSelectedEvent(updated));
        }
        revision=nextRevision;
    }

    private int visibleRows() { return Math.max(1,(listBottom-bounds.y()-46)/13); }
    private int rowCount() { return categoriesOpen?choices.size():rows.size(); }
    private void closeCategories() {
        if(!categoriesOpen)return;
        categoriesOpen=false;
        scroll=0;
        if(filter!=null)filter.setValue(commandQuery);
        reload();
    }

    @Override protected void safeRender(MaredRenderContext ctx) {
        com.fixmer.mared.technology.catalog.MinecraftCommandCatalog.poll(net.minecraft.client.Minecraft.getInstance().getConnection());
        var g=ctx.graphics();
        var font=ctx.font();
        if(bounds.width()<15||bounds.height()<50)return;
        if(filter==null) {
            filter=new EditBox(font,0,0,80,12,Component.literal("Search commands"));
            filter.setMaxLength(128);
            filter.setBordered(false);
            filter.setHint(Component.literal(EditorText.translate("Search commands…")));
            filter.setResponder(q->{scroll=0;reload();});
        }
        filter.setTextColor(text());
        filter.setTextColorUneditable(dim());
        listRight=wideDetails()?bounds.x()+Math.max(140,Math.min(bounds.width()-160,(int)(bounds.width()*wideSplit))):bounds.right();
        listBottom=wideDetails()||selected==null||categoriesOpen?bounds.bottom():Math.min(bounds.bottom(),bounds.y()+Math.max(72,(int)(bounds.height()*split)));
        if(revision!=MaredCommandRegistry.revision())reload();
        scroll=Math.max(0,Math.min(scroll,Math.max(0,rowCount()-visibleRows())));
        g.fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),panel());
        String title=EditorText.translate(selected==null?"HELP":"< HELP");
        g.drawString(font,font.plainSubstrByWidth(title,Math.max(0,listRight-bounds.x()-78)),bounds.x()+6,bounds.y()+5,dim(),false);
        g.drawString(font,"R",listRight-66,bounds.y()+5,dim(),false);
        g.drawString(font,"MC",listRight-46,bounds.y()+5,source==MaredCommandRegistry.Source.MC?accent():dim(),false);
        g.drawString(font,"MR",listRight-23,bounds.y()+5,source==MaredCommandRegistry.Source.MR?accent():dim(),false);
        filter.setX(bounds.x()+6);
        filter.setY(bounds.y()+21);
        filter.setWidth(Math.max(1,listRight-bounds.x()-12));
        filter.render(g,ctx.mouseX(),ctx.mouseY(),0f);
        String label=(category==null?EditorText.translate("All namespaces"):category)+(categoriesOpen?"  ^":"  v");
        g.drawString(font,font.plainSubstrByWidth(label,Math.max(0,listRight-bounds.x()-12)),bounds.x()+6,bounds.y()+35,dim(),false);
        g.enableScissor(bounds.x(),bounds.y()+46,listRight,listBottom);
        int end=Math.min(rowCount(),scroll+visibleRows());
        for(int i=scroll;i<end;i++) {
            int y=bounds.y()+46+(i-scroll)*13;
            String name=categoriesOpen?choices.get(i).label:rows.get(i).name;
            boolean active=categoriesOpen?Objects.equals(choices.get(i).value,category):name.equals(selected);
            boolean hover=ctx.mouseX()>=bounds.x()&&ctx.mouseX()<listRight&&ctx.mouseY()>=y&&ctx.mouseY()<y+13;
            if(active||hover)g.fill(bounds.x()+2,y,listRight-2,y+12,raised());
            if(active)g.fill(bounds.x()+2,y+3,bounds.x()+4,y+9,accent());
            g.drawString(font,font.plainSubstrByWidth(name,Math.max(0,listRight-bounds.x()-16)),bounds.x()+8,y+2,active?text():dim(),false);
        }
        if(rowCount()==0)g.drawString(font,EditorText.translate(emptyMessage()),bounds.x()+6,bounds.y()+49,dim(),false);
        g.disableScissor();
        if(selected!=null&&!categoriesOpen&&(wideDetails()||listBottom<bounds.bottom())) {
            if(wideDetails()) {
                g.fill(listRight,bounds.y(),listRight+2,bounds.bottom(),edge());
                inspector.layout(new MaredBounds(listRight+2,bounds.y(),Math.max(0,bounds.right()-listRight-2),bounds.height()));
                g.enableScissor(listRight+2,bounds.y(),bounds.right(),bounds.bottom());
            } else {
                g.fill(bounds.x(),listBottom,bounds.right(),listBottom+2,edge());
                inspector.layout(new MaredBounds(bounds.x(),listBottom+2,bounds.width(),Math.max(0,bounds.bottom()-listBottom-2)));
                g.enableScissor(bounds.x(),listBottom+2,bounds.right(),bounds.bottom());
            }
            inspector.render(ctx);
            g.disableScissor();
        }
    }

    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(!bounds.contains(mx,my))return false;
        if(button!=0)return true;
        requestFocus();
        if(wideDetails()) {
            if(Math.abs(mx-listRight)<=3){resizing=true;return true;}
            if(mx>=listRight+2){if(filter!=null)filter.setFocused(false);return inspector.mouseClicked(mx,my,button);}
        }
        if(my<bounds.y()+17&&mx>=listRight-72&&mx<listRight-48){refreshCatalog();return true;}
        if(selected!=null&&my<bounds.y()+17&&mx<listRight-72) {
            selected=null;
            bus.publish(new StudioEvents.CommandSelectedEvent(null));
            return true;
        }
        if(my<bounds.y()+17&&mx>=listRight-48) {
            setSource(mx<listRight-24?MaredCommandRegistry.Source.MC:MaredCommandRegistry.Source.MR);
            return true;
        }
        if(filter!=null) {
            filter.setFocused(filter.isMouseOver(mx,my));
            if(filter.isFocused())return filter.mouseClicked(mx,my,button);
        }
        if(my>=bounds.y()+33&&my<bounds.y()+46) {
            if(categoriesOpen)closeCategories();
            else {
                commandQuery=filter==null?"":filter.getValue();
                categoriesOpen=true;
                scroll=0;
                if(filter!=null)filter.setValue("");
                reload();
            }
            return true;
        }
        if(!wideDetails()&&selected!=null&&!categoriesOpen&&Math.abs(my-listBottom)<=3) { resizing=true;return true; }
        if(my>=bounds.y()+46&&my<listBottom) {
            int index=scroll+(int)((my-bounds.y()-46)/13);
            if(index<rowCount()) {
                if(categoriesOpen) { category=choices.get(index).value;closeCategories(); }
                else { selected=rows.get(index).name;bus.publish(new StudioEvents.CommandSelectedEvent(MaredCommandRegistry.findByName(selected,source))); }
            }
            return true;
        }
        return selected!=null&&!categoriesOpen&&inspector.mouseClicked(mx,my,button);
    }

    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(!resizing)return false;
        if(wideDetails())wideSplit=Math.max(.25,Math.min(.65,(mx-bounds.x())/Math.max(1,bounds.width())));
        else split=Math.max(.25,Math.min(.75,(my-bounds.y())/Math.max(1,bounds.height())));
        return true;
    }
    @Override public boolean mouseReleased(double mx,double my,int button) { boolean handled=resizing;resizing=false;return handled; }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy) {
        if(!bounds.contains(mx,my))return false;
        if(wideDetails()&&mx>=listRight)return inspector.mouseScrolled(mx,my,dx,dy);
        if(!wideDetails()&&my>=listBottom&&selected!=null&&!categoriesOpen)return inspector.mouseScrolled(mx,my,dx,dy);
        scroll=Math.max(0,Math.min(Math.max(0,rowCount()-visibleRows()),scroll+(dy<0?3:dy>0?-3:0)));
        return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==294){refreshCatalog();return true;}
        if(key==256&&categoriesOpen) { closeCategories();return true; }
        return filter!=null&&filter.isFocused()&&filter.keyPressed(key,scan,mods);
    }
    private void refreshCatalog(){com.fixmer.mared.technology.catalog.MinecraftCommandCatalog.refresh(net.minecraft.client.Minecraft.getInstance().getConnection());reload();}
    private String emptyMessage(){
        if(source!=MaredCommandRegistry.Source.MC||!MaredCommandRegistry.categories(source).isEmpty())return "No matches";
        return switch(com.fixmer.mared.technology.catalog.MinecraftCommandCatalog.state()){
            case OFFLINE -> "Connect to a world to load commands";
            case WAITING -> "Waiting for server commands · F5";
            case ERROR -> "Command catalog unavailable · F5";
            case READY -> "No matches";
        };
    }
    @Override public boolean charTyped(char c,int mods) { return filter!=null&&filter.isFocused()&&filter.charTyped(c,mods); }
}
