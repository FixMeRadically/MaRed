package com.fixmer.mared;

import com.fixmer.mared.gui2.framework.theme.MaredTheme;
import com.fixmer.mared.gui2.framework.theme.MaredThemeRegistry;
import com.fixmer.mared.gui2.framework.components.editor.SpanResolver;
import com.fixmer.mared.technology.editor.GenesisEditorVisuals;
import com.fixmer.mared.technology.editor.WorkbenchPreferences;
import net.neoforged.fml.loading.FMLPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EditorThemePreferencesIntegrationTest {
    @TempDir Path directory;

    @Test void versionOnePreferencesMigrateWithoutDiscardingPanelSizes() throws Exception {
        FMLPaths.loadAbsolutePaths(directory);
        Path path=directory.resolve("config/mared/editor_workbench.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path,"{\"version\":1,\"selectedTab\":1,\"filesRatio\":0.25,\"helpRatio\":0.35,\"logsRatio\":0.30,\"helpOpen\":true}");
        var prefs=WorkbenchPreferences.load();
        assertEquals(2,prefs.version);assertEquals(1,prefs.selectedTab);
        assertEquals(.25,prefs.filesRatio);assertEquals(.35,prefs.helpRatio);assertEquals(.30,prefs.logsRatio);
        assertFalse(prefs.helpOpen);assertFalse(prefs.helpDocked);
        prefs.helpOpen=true;prefs.helpDocked=true;prefs.helpWindowX=.12;prefs.helpWindowW=.65;prefs.save();
        var restored=WorkbenchPreferences.load();
        assertTrue(restored.helpOpen);assertTrue(restored.helpDocked);
        assertEquals(.12,restored.helpWindowX);assertEquals(.65,restored.helpWindowW);
    }

    @Test void livePaletteCacheUsesThemeIdentityAndExplicitTextColorRemainsSupported() {
        FMLPaths.loadAbsolutePaths(directory);
        MaredTheme previous=MaredThemeRegistry.active();
        try {
            var first=MaredTheme.builder("same-id","First").bgPanel(0xFF101010).text(0xFFEFEFEF).build();
            var replacement=MaredTheme.builder("same-id","Replacement").light(true).bgPanel(0xFFFFFFFF).text(0xFF222222).build();
            MaredThemeRegistry.setActive(first);assertEquals(first.bgPanel,GenesisEditorVisuals.panel());
            MaredThemeRegistry.setActive(replacement);
            assertEquals(replacement.bgPanel,GenesisEditorVisuals.panel());assertEquals(replacement.text,GenesisEditorVisuals.text());
            assertEquals(replacement.text,SpanResolver.plain().baseColor());
            assertEquals(0xFF123456,SpanResolver.plain(0xFF123456).baseColor());
        } finally { MaredThemeRegistry.setActive(previous); }
    }
}
