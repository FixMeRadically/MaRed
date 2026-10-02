package com.fixmer.mared.gui2.framework.theme;

import com.fixmer.mared.gui2.framework.render.MaredColor;

public final class MaredTheme {

    public final String id;
    public final String displayName;
    public final boolean light;

    public final int bgScreen;
    public final int bgPanel;
    public final int bgPanelRaised;
    public final int bgSunken;
    public final int bgHover;
    public final int bgSelected;

    public final int text;
    public final int textDim;
    public final int textFaint;
    public final int textInverse;

    public final int accent;
    public final int accentAlt;
    public final int accentDim;

    public final int success;
    public final int warn;
    public final int danger;
    public final int info;

    public final int border;
    public final int borderAccent;
    public final int divider;

    public final int scrollTrack;
    public final int scrollThumb;
    public final int scrollThumbHover;

    public final int overlayBg;
    public final int tooltipBg;
    public final int tooltipBorder;

    public final int tabScripts;
    public final int tabCommands;
    public final int tabNpc;
    public final int tabEvents;
    public final int tabQuests;
    public final boolean monotoneTabs;

    public final int padSmall;
    public final int padMedium;
    public final int padLarge;
    public final int padHuge;

    public final int rowHeight;
    public final int buttonHeight;
    public final int inputHeight;

    public final int radiusSmall;
    public final int radiusMedium;
    public final int radiusLarge;

    private MaredTheme(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.light = b.light;

        this.bgScreen      = b.bgScreen;
        this.bgPanel       = b.bgPanel;
        this.bgPanelRaised = b.bgPanelRaised;
        this.bgSunken      = b.bgSunken;
        this.bgHover       = b.bgHover;
        this.bgSelected    = b.bgSelected;

        this.text          = b.text;
        this.textDim       = b.textDim;
        this.textFaint     = b.textFaint;
        this.textInverse   = b.textInverse;

        this.accent        = b.accent;
        this.accentAlt     = b.accentAlt;
        this.accentDim     = b.accentDim;

        this.success       = b.success;
        this.warn          = b.warn;
        this.danger        = b.danger;
        this.info          = b.info;

        this.border        = b.border;
        this.borderAccent  = b.borderAccent;
        this.divider       = b.divider;

        this.scrollTrack       = b.scrollTrack;
        this.scrollThumb       = b.scrollThumb;
        this.scrollThumbHover  = b.scrollThumbHover;

        this.overlayBg     = b.overlayBg;
        this.tooltipBg     = b.tooltipBg;
        this.tooltipBorder = b.tooltipBorder;

        this.tabScripts    = b.tabScripts;
        this.tabCommands   = b.tabCommands;
        this.tabNpc        = b.tabNpc;
        this.tabEvents     = b.tabEvents;
        this.tabQuests     = b.tabQuests;
        this.monotoneTabs  = b.monotoneTabs;

        this.padSmall  = b.padSmall;
        this.padMedium = b.padMedium;
        this.padLarge  = b.padLarge;
        this.padHuge   = b.padHuge;

        this.rowHeight    = b.rowHeight;
        this.buttonHeight = b.buttonHeight;
        this.inputHeight  = b.inputHeight;

        this.radiusSmall  = b.radiusSmall;
        this.radiusMedium = b.radiusMedium;
        this.radiusLarge  = b.radiusLarge;
    }

    public int colorForTab(String tab) {
        if (monotoneTabs) return accent;
        return switch (tab) {
            case "scripts"  -> tabScripts;
            case "commands" -> tabCommands;
            case "npc"      -> tabNpc;
            case "events"   -> tabEvents;
            case "quests"   -> tabQuests;
            default         -> accent;
        };
    }

    public int colorForTabAlt(String tab) {
        if (monotoneTabs) return accentAlt;
        int base = colorForTab(tab);
        return MaredColor.darken(base, 0.25f);
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static final class Builder {
        final String id;
        final String displayName;
        boolean light = false;

        int bgScreen      = 0xFF0A0A10;
        int bgPanel       = 0xFF14141C;
        int bgPanelRaised = 0xFF1A1A24;
        int bgSunken      = 0xFF0A0A10;
        int bgHover       = 0xFF2A2A38;
        int bgSelected    = 0xFF3A3A4A;

        int text          = 0xFFFFFFFF;
        int textDim       = 0xFFAAAAAA;
        int textFaint     = 0xFF666680;
        int textInverse   = 0xFF000000;

        int accent        = 0xFF55AAFF;
        int accentAlt     = 0xFF3388DD;
        int accentDim     = 0xFF3388AA;

        int success       = 0xFF55FF88;
        int warn          = 0xFFFFAA00;
        int danger        = 0xFFFF4444;
        int info          = 0xFF88DDFF;

        int border        = 0xFF333344;
        int borderAccent  = 0xFF55AAFF;
        int divider       = 0xFF2A2A38;

        int scrollTrack       = 0xFF15151E;
        int scrollThumb       = 0xFF3A3A4A;
        int scrollThumbHover  = 0xFF50505F;

        int overlayBg     = 0xC0000000;
        int tooltipBg     = 0xFF1A1A24;
        int tooltipBorder = 0xFF55AAFF;

        int tabScripts  = 0xFFFF55FF;
        int tabCommands = 0xFFFFAA00;
        int tabNpc      = 0xFF55FF55;
        int tabEvents   = 0xFF55AAFF;
        int tabQuests   = 0xFFFF5555;
        boolean monotoneTabs = false;

        int padSmall  = 4;
        int padMedium = 8;
        int padLarge  = 12;
        int padHuge   = 16;

        int rowHeight    = 16;
        int buttonHeight = 18;
        int inputHeight  = 16;

        int radiusSmall  = 2;
        int radiusMedium = 4;
        int radiusLarge  = 6;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder light(boolean v)                { this.light = v; return this; }
        public Builder bgScreen(int c)                 { this.bgScreen = c; return this; }
        public Builder bgPanel(int c)                  { this.bgPanel = c; return this; }
        public Builder bgPanelRaised(int c)            { this.bgPanelRaised = c; return this; }
        public Builder bgSunken(int c)                 { this.bgSunken = c; return this; }
        public Builder bgHover(int c)                  { this.bgHover = c; return this; }
        public Builder bgSelected(int c)               { this.bgSelected = c; return this; }
        public Builder text(int c)                     { this.text = c; return this; }
        public Builder textDim(int c)                  { this.textDim = c; return this; }
        public Builder textFaint(int c)                { this.textFaint = c; return this; }
        public Builder textInverse(int c)              { this.textInverse = c; return this; }
        public Builder accent(int c)                   { this.accent = c; return this; }
        public Builder accentAlt(int c)                { this.accentAlt = c; return this; }
        public Builder accentDim(int c)                { this.accentDim = c; return this; }
        public Builder success(int c)                  { this.success = c; return this; }
        public Builder warn(int c)                     { this.warn = c; return this; }
        public Builder danger(int c)                   { this.danger = c; return this; }
        public Builder info(int c)                     { this.info = c; return this; }
        public Builder border(int c)                   { this.border = c; return this; }
        public Builder borderAccent(int c)             { this.borderAccent = c; return this; }
        public Builder divider(int c)                  { this.divider = c; return this; }
        public Builder scrollTrack(int c)              { this.scrollTrack = c; return this; }
        public Builder scrollThumb(int c)              { this.scrollThumb = c; return this; }
        public Builder scrollThumbHover(int c)         { this.scrollThumbHover = c; return this; }
        public Builder overlayBg(int c)                { this.overlayBg = c; return this; }
        public Builder tooltipBg(int c)                { this.tooltipBg = c; return this; }
        public Builder tooltipBorder(int c)            { this.tooltipBorder = c; return this; }
        public Builder tabScripts(int c)               { this.tabScripts = c; return this; }
        public Builder tabCommands(int c)              { this.tabCommands = c; return this; }
        public Builder tabNpc(int c)                   { this.tabNpc = c; return this; }
        public Builder tabEvents(int c)                { this.tabEvents = c; return this; }
        public Builder tabQuests(int c)                { this.tabQuests = c; return this; }
        public Builder monotoneTabs(boolean v)         { this.monotoneTabs = v; return this; }
        public Builder padSmall(int v)                 { this.padSmall = v; return this; }
        public Builder padMedium(int v)                { this.padMedium = v; return this; }
        public Builder padLarge(int v)                 { this.padLarge = v; return this; }
        public Builder padHuge(int v)                  { this.padHuge = v; return this; }
        public Builder rowHeight(int v)                { this.rowHeight = v; return this; }
        public Builder buttonHeight(int v)             { this.buttonHeight = v; return this; }
        public Builder inputHeight(int v)              { this.inputHeight = v; return this; }
        public Builder radiusSmall(int v)              { this.radiusSmall = v; return this; }
        public Builder radiusMedium(int v)             { this.radiusMedium = v; return this; }
        public Builder radiusLarge(int v)              { this.radiusLarge = v; return this; }

        public MaredTheme build() {
            return new MaredTheme(this);
        }
    }
}