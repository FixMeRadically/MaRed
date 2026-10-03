package com.fixmer.mared.gui2.studio;

import java.util.function.BiConsumer;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.gui2.framework.components.overlay.MaredConfirmDialog;
import com.fixmer.mared.gui2.framework.components.overlay.MaredNameDialog;
import com.fixmer.mared.gui2.settings.MaredSettingsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenNavigator {

    private ScreenNavigator() {}

    public static void openNewFileDialog(Screen parent,
                                         BiConsumer<String, Boolean> onAccept) {
        Minecraft.getInstance().setScreen(new MaredNameDialog(
            parent,
            MaredLang.get("mared.dialog.new_command_file"),
            onAccept,
            0xFFFF55FF, true,
            name -> MaredCommandStorage.listCommands().contains(name)
                ? MaredLang.format("mared.dialog.error.exists", name) : null
        ));
    }

    public static void openRenameDialog(Screen parent,
                                        String oldName,
                                        BiConsumer<String, Boolean> onAccept) {
        Minecraft.getInstance().setScreen(new MaredNameDialog(
            parent,
            MaredLang.format("mared.dialog.rename_title", oldName),
            onAccept,
            0xFFAA00, true,
            name -> MaredCommandStorage.listCommands().contains(name)
                ? MaredLang.format("mared.dialog.error.exists", name) : null
        ));
    }

    public static void openDeleteConfirm(Screen parent,
                                         String fileName,
                                         boolean persistent,
                                         Runnable onConfirm) {
        String title = persistent
            ? MaredLang.format("mared.dialog.delete_persistent_title", fileName)
            : MaredLang.format("mared.dialog.delete_title", "file", fileName);
        String message = persistent
            ? MaredLang.get("mared.dialog.delete_persistent_message")
            : MaredLang.format("mared.dialog.delete_message", fileName);

        Minecraft.getInstance().setScreen(new MaredConfirmDialog(
            parent, title, message, onConfirm, persistent
        ));
    }

    public static void openUnsavedConfirm(Screen parent, Runnable onDiscard) {
        Minecraft.getInstance().setScreen(new MaredConfirmDialog(
            parent,
            MaredLang.get("mared.dialog.unsaved_title"),
            MaredLang.get("mared.dialog.unsaved_message"),
            onDiscard,
            true
        ));
    }

    public static void openSettings(Screen parent) {
        Minecraft.getInstance().setScreen(new MaredSettingsScreen(parent));
    }

    public static void openStudio() {
        Minecraft.getInstance().setScreen(new MaredStudioScreen());
    }
}