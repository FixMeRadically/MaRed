package com.fixmer.mared.gui2.studio;

import java.util.function.BiConsumer;

import com.fixmer.mared.MaredLang;
import com.fixmer.mared.commands.storage.MaredCommandStorage;
import com.fixmer.mared.gui2.framework.overlay.ConfirmDialogOverlay;
import com.fixmer.mared.gui2.framework.overlay.NameDialogOverlay;
import com.fixmer.mared.gui2.framework.overlay.OverlayManager;
import com.fixmer.mared.gui2.settings.MaredSettingsScreen;
import com.fixmer.mared.technology.editor.GenesisEditorVisuals;

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

    private static String validateAvailableName(String name) {
        try {return MaredCommandStorage.listCommands().contains(name)?MaredLang.format("mared.dialog.error.exists",name):null;}
        catch(Exception error){return "Cannot read commands directory: "+error.getMessage();}
    }

    public static void openNewFileDialog(OverlayManager overlays,
                                         BiConsumer<String, Boolean> onAccept) {
        if (overlays == null) return;
        overlays.push(new NameDialogOverlay(
            MaredLang.get("mared.dialog.new_command_file"),
            onAccept,
            GenesisEditorVisuals.LOGIC,
            true,
            ScreenNavigator::validateAvailableName
        ));
    }

    public static void openSaveAsDialog(OverlayManager overlays, String initial,
                                        java.util.function.Consumer<String> onAccept,
                                        Runnable onCancel) {
        if (overlays == null) return;
        overlays.push(new NameDialogOverlay(MaredLang.get("mared.dialog.save_as"),
            onAccept, GenesisEditorVisuals.LOGIC, false, ScreenNavigator::validateAvailableName)
            .acceptLabel("mared.dialog.save").initialValue(initial).onCancel(onCancel));
    }

    public static void openRenameDialog(OverlayManager overlays,
                                        String oldName,
                                        BiConsumer<String, Boolean> onAccept) {
        if (overlays == null) return;
        overlays.push(new NameDialogOverlay(
            MaredLang.format("mared.dialog.rename_title", oldName),
            onAccept,
            GenesisEditorVisuals.LOGIC,
            false,
            name -> name.equals(oldName) ? null : validateAvailableName(name)
        ).acceptLabel("mared.dialog.rename").initialValue(oldName));
    }

    public static void openDeleteConfirm(OverlayManager overlays,
                                         String fileName,
                                         boolean persistent,
                                         Runnable onConfirm) {
        if (overlays == null) return;
        String title = persistent
            ? MaredLang.format("mared.dialog.delete_persistent_title", fileName)
            : MaredLang.format("mared.dialog.delete_file_title", fileName);
        String message = persistent
            ? MaredLang.get("mared.dialog.delete_persistent_message")
            : MaredLang.format("mared.dialog.delete_message", fileName);

        overlays.push(new ConfirmDialogOverlay(title, message, onConfirm,
            true, GenesisEditorVisuals.LOGIC, true, "mared.dialog.delete"));
    }

    public static void openUnsavedConfirm(OverlayManager overlays,
                                          Runnable onDiscard) {
        if (overlays == null) return;
        overlays.push(new ConfirmDialogOverlay(
            MaredLang.get("mared.dialog.unsaved_title"),
            MaredLang.get("mared.dialog.unsaved_message"),
            onDiscard,
            true, GenesisEditorVisuals.LOGIC, true, "mared.dialog.confirm"
        ));
    }

    // ============================================================
    //  Full-screen navigations
    // ============================================================

    public static void openSettings(Screen parent) {
        if(parent instanceof com.fixmer.mared.gui2.shell.MaredShellScreen shell)shell.suspendForSettings();
        Minecraft.getInstance().setScreen(new MaredSettingsScreen(parent));
    }

    public static void openStudio() {
        com.fixmer.mared.gui2.launcher.MaredScreenManager.openStudio();
    }
}