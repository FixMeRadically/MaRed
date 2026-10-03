package com.fixmer.mared.gui2.studio;

import java.util.function.BiConsumer;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.gui2.framework.overlay.ConfirmDialogOverlay;
import com.fixmer.mared.gui2.framework.overlay.NameDialogOverlay;
import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.settings.MaredSettingsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Хелперы для диалогов и Screen-переходов.
 *
 * 0.3.2:
 *   - Диалоги теперь Overlay'и, а не Screen'ы. Методы принимают
 *     OverlayManager.
 *   - openSettings / openStudio остаются на setScreen — это полноценные
 *     экраны, а не overlay.
 */
public final class ScreenNavigator {

    private ScreenNavigator() {}

    // ============================================================
    //  Dialogs (overlays)
    // ============================================================

    public static void openNewFileDialog(OverlayManager overlays,
                                         BiConsumer<String, Boolean> onAccept) {
        if (overlays == null) return;
        overlays.push(new NameDialogOverlay(
            MaredLang.get("mared.dialog.new_command_file"),
            onAccept,
            0xFFFF55FF,
            true,
            name -> MaredCommandStorage.listCommands().contains(name)
                ? MaredLang.format("mared.dialog.error.exists", name) : null
        ));
    }

    public static void openRenameDialog(OverlayManager overlays,
                                        String oldName,
                                        BiConsumer<String, Boolean> onAccept) {
        if (overlays == null) return;
        overlays.push(new NameDialogOverlay(
            MaredLang.format("mared.dialog.rename_title", oldName),
            onAccept,
            0xFFAA00,
            true,
            name -> MaredCommandStorage.listCommands().contains(name)
                ? MaredLang.format("mared.dialog.error.exists", name) : null
        ));
    }

    public static void openDeleteConfirm(OverlayManager overlays,
                                         String fileName,
                                         boolean persistent,
                                         Runnable onConfirm) {
        if (overlays == null) return;
        String title = persistent
            ? MaredLang.format("mared.dialog.delete_persistent_title", fileName)
            : MaredLang.format("mared.dialog.delete_title", "file", fileName);
        String message = persistent
            ? MaredLang.get("mared.dialog.delete_persistent_message")
            : MaredLang.format("mared.dialog.delete_message", fileName);

        overlays.push(new ConfirmDialogOverlay(title, message, onConfirm,
            persistent));
    }

    public static void openUnsavedConfirm(OverlayManager overlays,
                                          Runnable onDiscard) {
        if (overlays == null) return;
        overlays.push(new ConfirmDialogOverlay(
            MaredLang.get("mared.dialog.unsaved_title"),
            MaredLang.get("mared.dialog.unsaved_message"),
            onDiscard,
            true
        ));
    }

    // ============================================================
    //  Full-screen navigations
    // ============================================================

    public static void openSettings(Screen parent) {
        Minecraft.getInstance().setScreen(new MaredSettingsScreen(parent));
    }

    public static void openStudio() {
        Minecraft.getInstance().setScreen(new MaredStudioScreen());
    }
}