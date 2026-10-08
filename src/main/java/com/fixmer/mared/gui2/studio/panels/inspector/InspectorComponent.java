package com.fixmer.mared.gui2.studio.panels.inspector;

import com.fixmer.mared.commands.registry.MaredCommandRegistry;
import com.fixmer.mared.gui2.framework.core.Disposable;
import com.fixmer.mared.gui2.framework.core.MaredComponent;
import com.fixmer.mared.gui2.framework.core.MaredRenderContext;
import com.fixmer.mared.gui2.framework.render.TextUtils;
import com.fixmer.mared.gui2.framework.theme.ThemeColors;
import com.fixmer.mared.gui2.studio.events.StudioEventBus;
import com.fixmer.mared.gui2.studio.events.StudioEvents;
import com.fixmer.mared.gui2.studio.events.SubscriptionGroup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class InspectorComponent extends MaredComponent implements Disposable {

    private static final int HEADER_H = 16;
    private static final int PAD = 6;
    private static final int LINE_H = 10;
    private static final int SCROLL_STEP = 20;

    private final SubscriptionGroup subs = new SubscriptionGroup();

    private MaredCommandRegistry.CommandInfo current;
    private int scrollOffset = 0;
    private int contentHeight = 0;
    private long catalogRevision=-1;
    private record CopyTarget(int y,int height,String text) {}
    private final java.util.List<CopyTarget> copyTargets=new java.util.ArrayList<>();
    private record LineKey(String text,int width) {}
    private final java.util.Map<LineKey,java.util.List<String>> lineCache=new java.util.HashMap<>();
    private int lastWidth=-1;
    private Font lastFont;
    private boolean heightDirty=true;
    private int copyText(GuiGraphics g,Font font,String text,int y,int maxW,int color) {
        int height=wrappedHeight(font,text,maxW);
        if(y+height>bounds.y()+HEADER_H+2&&y<bounds.bottom()-2)copyTargets.add(new CopyTarget(y,height,text));
        return wrapped(g,font,text,bounds.x()+PAD,y,maxW,color);
    }

    private java.util.List<String> lines(Font font,String text,int width) {
        var key=new LineKey(text,width);
        var cached=lineCache.get(key);if(cached!=null)return cached;
        var result=new java.util.ArrayList<String>();
        for(String paragraph:text.split("\\R",-1))result.addAll(TextUtils.wrapLinesCached(font,paragraph,width));
        var immutable=java.util.List.copyOf(result);if(lineCache.size()<2048)lineCache.put(key,immutable);return immutable;
    }
    private int wrappedHeight(Font font,String text,int width){return lines(font,text,width).size()*LINE_H;}
    private int wrapped(GuiGraphics g,Font font,String text,int x,int y,int width,int color) {
        var lines=lines(font,text,width);
        int first=Math.max(0,(bounds.y()+HEADER_H-y)/LINE_H);
        int end=Math.min(lines.size(),Math.max(0,(bounds.bottom()-y+LINE_H-1)/LINE_H));
        for(int i=first;i<end;i++)g.drawString(font,lines.get(i),x,y+i*LINE_H,color,false);
        return y+lines.size()*LINE_H;
    }

    public InspectorComponent(StudioEventBus bus) {
        subs.add(bus.subscribe(StudioEvents.CommandSelectedEvent.class,
            e -> select(e.info())));
    }

    @Override
    public void dispose() { subs.dispose(); }

    public MaredCommandRegistry.CommandInfo current() { return current; }

    public void select(MaredCommandRegistry.CommandInfo info) {
        copyTargets.clear();lineCache.clear();heightDirty=true;
        this.current = info;
        this.scrollOffset = 0;
        this.contentHeight = 0;
    }

    @Override
    protected void safeRender(MaredRenderContext ctx) {
        if(catalogRevision!=MaredCommandRegistry.revision()) {
            catalogRevision=MaredCommandRegistry.revision();
            if(current!=null)select(MaredCommandRegistry.findByName(current.name,current.source));
        }
        copyTargets.clear();
        GuiGraphics g = ctx.graphics();
        Font font = ctx.font();
        if(lastWidth!=bounds.width()||lastFont!=font){lastWidth=bounds.width();lastFont=font;lineCache.clear();heightDirty=true;}

        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), com.fixmer.mared.technology.editor.GenesisEditorVisuals.panel());
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + HEADER_H, com.fixmer.mared.technology.editor.GenesisEditorVisuals.raised());
        g.drawString(font, com.fixmer.mared.technology.editor.EditorText.translate("DETAILS · click syntax to copy"), bounds.x() + PAD, bounds.y() + 4,
            com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent(), false);
        g.fill(bounds.x(), bounds.y() + HEADER_H - 1,
               bounds.right(), bounds.y() + HEADER_H, com.fixmer.mared.technology.editor.GenesisEditorVisuals.edge());

        if (current == null) {
            g.drawString(font, com.fixmer.mared.technology.editor.EditorText.translate("No command selected"),
                bounds.x() + PAD, bounds.y() + HEADER_H + 8,
                com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
            contentHeight = 0;
            return;
        }

        int bodyY = bounds.y() + HEADER_H + 2;
        int bodyH = bounds.height() - HEADER_H - 4;
        int maxW = Math.max(40, bounds.width() - PAD * 2 - 6);

        if(heightDirty){contentHeight=computeContentHeight(font,maxW);heightDirty=false;}

        int maxScroll = Math.max(0, contentHeight - bodyH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        g.enableScissor(bounds.x(), bodyY, bounds.right(), bodyY + bodyH);

        int y = bodyY - scrollOffset + 2;

        y = sectionHeader(g, font, "COMMAND", y);
        g.drawString(font, current.name, bounds.x() + PAD, y, com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent(), false);
        y += LINE_H + 2;
        y = kv(g, font, "category", current.category, y);
        y = kv(g, font, "source", current.source.name(), y);
        y += 4;

        if (notEmpty(current.description)) {
            y = sectionHeader(g, font, "DESCRIPTION", y);
            y = wrapped(g, font, current.description,
                bounds.x() + PAD, y, maxW, com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
            y += 6;
        }

        if(!current.usages.isEmpty()) {
            y=sectionHeader(g,font,"SYNTAX · click to copy",y);
            for(String usage:current.usages){y=copyText(g,font,usage,y,maxW,com.fixmer.mared.technology.editor.GenesisEditorVisuals.success());y+=4;}
            y+=2;
        }
        if (!current.arguments.isEmpty()) {
            y = sectionHeader(g, font, "ARGUMENTS (" + current.arguments.size() + ")", y);
            for (MaredCommandRegistry.Argument arg : current.arguments) {
                y=copyText(g,font,arg.value,y,maxW,com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
                y+=2;
                if (notEmpty(arg.description)) {
                    y = wrapped(g, font, arg.description,
                        bounds.x() + PAD + 10, y, maxW - 10, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());
                }
                if(!arg.examples.isEmpty())y=copyText(g,font,"Type examples: "+String.join(", ",arg.examples),y,maxW,com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());
                y += 4;
            }
            y += 2;
        }

        if (!current.nbtHints.isEmpty()) {
            y = sectionHeader(g, font, "NBT (" + current.nbtHints.size() + ")", y);
            for (MaredCommandRegistry.NbtHint nbt : current.nbtHints) {
                g.drawString(font, nbt.tag,
                    bounds.x() + PAD + 2, y, com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent(), false);
                y += LINE_H + 1;
                if (notEmpty(nbt.what)) {
                    y = wrapped(g, font, nbt.what,
                        bounds.x() + PAD + 10, y, maxW - 10, com.fixmer.mared.technology.editor.GenesisEditorVisuals.text());
                }
                if (notEmpty(nbt.why)) {
                    y = wrapped(g, font, nbt.why,
                        bounds.x() + PAD + 10, y, maxW - 10, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim());
                }
                if (notEmpty(nbt.example)) {
                    y = wrapped(g, font, nbt.example,
                        bounds.x() + PAD + 10, y, maxW - 10, com.fixmer.mared.technology.editor.GenesisEditorVisuals.success());
                }
                y += 6;
            }
        }

        g.disableScissor();
        drawScrollbar(g, bodyY, bodyH, maxScroll);
    }

    private int sectionHeader(GuiGraphics g, Font font, String title, int y) {
        if(title.startsWith("ARGUMENTS ("))title=com.fixmer.mared.technology.editor.EditorText.translate("ARGUMENTS")+" "+title.substring(9);
        else title=com.fixmer.mared.technology.editor.EditorText.translate(title);
        g.drawString(font, title, bounds.x() + PAD, y, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
        return y + LINE_H + 2;
    }

    private int kv(GuiGraphics g, Font font, String key, String value, int y) {
        g.drawString(font, com.fixmer.mared.technology.editor.EditorText.translate(key) + ": " + value, bounds.x() + PAD, y, com.fixmer.mared.technology.editor.GenesisEditorVisuals.dim(), false);
        return y + LINE_H;
    }

    private void drawScrollbar(GuiGraphics g, int bodyY, int bodyH, int maxScroll) {
        if (maxScroll <= 0) return;
        int trackX = bounds.right() - 4;
        int trackW = 3;
        g.fill(trackX, bodyY, trackX + trackW, bodyY + bodyH, com.fixmer.mared.technology.editor.GenesisEditorVisuals.track());
        int thumbH = Math.max(10, bodyH * bodyH / Math.max(1, contentHeight));
        int thumbY = bodyY + (bodyH - thumbH) * scrollOffset / maxScroll;
        g.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, com.fixmer.mared.technology.editor.GenesisEditorVisuals.accent());
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    private int computeContentHeight(Font font, int maxW) {
        int h = 4;
        h += LINE_H + 2 + LINE_H + 2 + LINE_H + LINE_H + 4;
        if (notEmpty(current.description)) {
            h += LINE_H + 2 + wrappedHeight(font, current.description, maxW) + 6;
        }
        if(!current.usages.isEmpty()) {
            h+=LINE_H+2;
            for(String usage:current.usages)h+=wrappedHeight(font,usage,maxW)+4;
            h+=2;
        }
        if (!current.arguments.isEmpty()) {
            h += LINE_H + 2;
            for (MaredCommandRegistry.Argument a : current.arguments) {
                h += wrappedHeight(font,a.value,maxW)+2;
                if (notEmpty(a.description))
                    h += wrappedHeight(font, a.description, maxW - 10);
                if(!a.examples.isEmpty())h+=wrappedHeight(font,"Type examples: "+String.join(", ",a.examples),maxW);
                h += 4;
            }
            h += 2;
        }
        if (!current.nbtHints.isEmpty()) {
            h += LINE_H + 2;
            for (MaredCommandRegistry.NbtHint n : current.nbtHints) {
                h += LINE_H + 1;
                if (notEmpty(n.what))    h += wrappedHeight(font, n.what, maxW - 10);
                if (notEmpty(n.why))     h += wrappedHeight(font, n.why, maxW - 10);
                if (notEmpty(n.example)) h += wrappedHeight(font, n.example, maxW - 10);
                h += 6;
            }
        }
        return h;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (!bounds.contains(mx, my)) return false;
        int bodyH = bounds.height() - HEADER_H - 4;
        int maxScroll = Math.max(0, contentHeight - bodyH);
        if (scrollY < 0) scrollOffset = Math.min(maxScroll, scrollOffset + SCROLL_STEP);
        else if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if(!bounds.contains(mx,my))return false;
        if(button==0&&my>=bounds.y()+HEADER_H+2&&my<bounds.bottom()-2)
            for(var target:copyTargets)if(my>=target.y&&my<target.y+target.height){net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(target.text);return true;}
        return true;
    }
}