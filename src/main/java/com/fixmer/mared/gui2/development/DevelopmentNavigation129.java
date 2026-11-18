package com.fixmer.mared.gui2.development;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.fixmer.mared.gui2.shell.MaredShellScreen;

/** Keep the editor runtime and its unsaved documents alive while using development tools. */
public final class DevelopmentNavigation129 {
    private DevelopmentNavigation129() {}
    public static void open(DevelopmentHub129.Section section) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen;
        if (parent instanceof MaredShellScreen shell) shell.suspendForSettings();
        if(section==DevelopmentHub129.Section.TEAM)mc.setScreen(new com.fixmer.mared.technology.workspace.live122.WorkspaceHome131(parent));else mc.setScreen(new DevelopmentHub129(parent, section));
    }
}
