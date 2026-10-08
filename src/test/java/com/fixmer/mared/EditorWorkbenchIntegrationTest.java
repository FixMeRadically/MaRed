package com.fixmer.mared;

import com.fixmer.genesis.technology.editor.EditorLayout;
import com.fixmer.genesis.technology.editor.EditorLayout.Rect;
import com.fixmer.mared.gui2.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorWorkbenchIntegrationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private List<Rect> rectangles(EditorLayout.Frame f){return List.of(f.rail(),f.toolbar(),f.files(),f.editor(),f.logs(),f.help(),f.status());}
    @Test void panelsTileScreenWithoutOverlapsAtEveryStateAndSize(){
        for(int w:new int[]{0,1,29,120,260,320,640,960,1280,1920})for(int h:new int[]{0,1,22,100,180,360,720,1080})
            for(int state=0;state<8;state++)for(double ratio:new double[]{Double.NaN,-1,.18,.6,Double.POSITIVE_INFINITY}){
                var frame=EditorLayout.calculate(w,h,ratio,ratio,ratio,(state&1)!=0,(state&2)!=0,(state&4)!=0);
                var rects=rectangles(frame);long area=0;
                for(Rect r:rects){assertTrue(r.x()>=0&&r.y()>=0&&r.right()<=w&&r.bottom()<=h,r.toString());area+=(long)r.width()*r.height();}
                assertEquals((long)w*h,area);
                for(int i=0;i<rects.size();i++)for(int j=i+1;j<rects.size();j++){
                    Rect a=rects.get(i),b=rects.get(j);assertFalse(Math.min(a.right(),b.right())>Math.max(a.x(),b.x())&&Math.min(a.bottom(),b.bottom())>Math.max(a.y(),b.y()));
                }
            }
    }
    @Test void collapsingReturnsEditorSpaceAndRestoresRequestedRatios(){
        var open=EditorLayout.calculate(640,360,.18,.29,.24,true,true,true);
        var closed=EditorLayout.calculate(640,360,.18,.29,.24,false,false,false);
        assertTrue(closed.editor().width()>open.editor().width());assertTrue(closed.editor().height()>open.editor().height());
        assertEquals(15,closed.logs().height());assertEquals(14,closed.files().width());assertEquals(0,closed.help().width());
        assertEquals(open,EditorLayout.calculate(640,360,.18,.29,.24,true,true,true));
    }
    private static class Dummy implements MaredSpace {
        final SpaceId id;int entries,exits;Dummy(SpaceId id){this.id=id;}
        public SpaceId id(){return id;}public String titleKey(){return id.id();}public int accentColor(){return 0;}
        public void onEnter(SpaceCameraState c){entries++;}public void onExit(){exits++;}
    }
    @Test void returnToGenesisReusesRootAndReleasesEveryNestedSpace(){
        var root=new Dummy(SpaceId.GENESIS);var logic=new Dummy(SpaceId.LOGIC);var studio=new Dummy(SpaceId.STUDIO);
        var graph=new SpaceGraph();graph.register(SpaceId.GENESIS,()->root);graph.register(SpaceId.LOGIC,()->logic);graph.register(SpaceId.STUDIO,()->studio);
        var nav=new MaredNavigation(graph);nav.boot(SpaceId.GENESIS);nav.push(SpaceId.LOGIC);nav.tick(1);nav.push(SpaceId.STUDIO);nav.tick(1);
        assertEquals(3,nav.depth());nav.returnToGenesis();assertSame(root,nav.current());assertEquals(1,nav.depth());assertEquals(1,logic.exits);assertEquals(1,studio.exits);assertEquals(0,root.exits);
        nav.returnToGenesis();assertEquals(1,nav.depth());
    }
    @Test void directStudioBootCanReturnToGenesisAndReturnDuringTransitionIsIgnored(){
        var root=new Dummy(SpaceId.GENESIS);var studio=new Dummy(SpaceId.STUDIO);var graph=new SpaceGraph();
        graph.register(SpaceId.GENESIS,()->root);graph.register(SpaceId.STUDIO,()->studio);var nav=new MaredNavigation(graph);
        nav.boot(SpaceId.STUDIO);nav.returnToGenesis();assertSame(root,nav.current());assertEquals(1,studio.exits);
        nav.push(SpaceId.STUDIO);nav.returnToGenesis();assertTrue(nav.isTransitioning());nav.tick(1);assertSame(studio,nav.current());
    }
    @Test void escapeClosesModalBeforeNavigationAndHiddenOwnersCannotReceiveInput() {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        var session=new com.fixmer.mared.gui2.studio.StudioSession();
        try {
            session.initialize(null,null);var editor=session.workbench();
            session.overlayManager().push(new com.fixmer.mared.gui2.framework.overlay.ConfirmDialogOverlay("Test","Modal",()->{},false));
            assertTrue(editor.key(256,0,0));assertFalse(editor.key(256,0,0));
            int[] calls={0};var invisible=new com.fixmer.mared.gui2.framework.core.MaredComponent(){
                @Override protected void safeRender(com.fixmer.mared.gui2.framework.core.MaredRenderContext ctx){}
                @Override public boolean keyPressed(int key,int scan,int mods){calls[0]++;return true;}
                @Override public boolean charTyped(char value,int mods){calls[0]++;return true;}
            };
            session.focusManager().request(invisible);session.pointerManager().capture(invisible,0);
            assertFalse(editor.key(65,0,0));assertFalse(editor.character('a',0));assertEquals(0,calls[0]);
            editor.suspended();assertNull(session.focusManager().owner());assertFalse(session.pointerManager().isCaptured());
        } finally { session.shutdown(); }
    }
    @Test void workbenchPreferencesPersistAndInvalidRatiosAreBounded() throws Exception {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        var settings=new com.fixmer.mared.technology.editor.WorkbenchPreferences();settings.background=false;settings.logsOpen=true;settings.selectedTab=1;settings.save();
        var loaded=com.fixmer.mared.technology.editor.WorkbenchPreferences.load();assertFalse(loaded.background);assertTrue(loaded.logsOpen);assertEquals(1,loaded.selectedTab);
        var file=directory.resolve("config/mared/editor_workbench.json");
        java.nio.file.Files.writeString(file,"{\"version\":1,\"filesRatio\":1000,\"helpRatio\":-10,\"selectedTab\":500}");
        loaded=com.fixmer.mared.technology.editor.WorkbenchPreferences.load();assertEquals(.6,loaded.filesRatio);assertEquals(.08,loaded.helpRatio);assertEquals(7,loaded.selectedTab);
        try(var files=java.nio.file.Files.list(file.getParent())){assertFalse(files.anyMatch(path->path.getFileName().toString().endsWith(".tmp")));}
    }
    @Test void syntaxHighlightingSurvivesDocumentAttachmentAndSwitching() {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        var workspace=new com.fixmer.mared.gui2.studio.panels.workspace.WorkspaceComponent(new com.fixmer.mared.gui2.studio.events.StudioEventBus());
        try {
            workspace.openUntitled();assertSame(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.INSTANCE,workspace.editor().spanResolver());
            workspace.setSpanResolver(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.DARK_EDITOR);
            workspace.openUntitled();assertSame(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.DARK_EDITOR,workspace.editor().spanResolver());
            workspace.keyPressed(258,0,2);assertSame(com.fixmer.mared.gui2.framework.components.editor.MaredScriptSpanResolver.DARK_EDITOR,workspace.editor().spanResolver());
        } finally { workspace.dispose(); }
    }
    @Test void hiddenLegacyDocksStillProvideCoreEditorServices() {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(directory);
        boolean files=MaredSettings.isLayoutShowSidebar(),help=MaredSettings.isLayoutShowRightPanel(),logs=MaredSettings.isLayoutShowConsole();
        var controller=new com.fixmer.mared.gui2.studio.MaredStudioController();
        try {
            MaredSettings.setLayoutShowSidebar(false);MaredSettings.setLayoutShowRightPanel(false);MaredSettings.setLayoutShowConsole(false);
            controller.initialize(new com.fixmer.mared.gui2.framework.core.FocusManager(),new com.fixmer.mared.gui2.framework.core.PointerCaptureManager(),new com.fixmer.mared.gui2.framework.core.FocusTraversal());
            assertNotNull(controller.workspacePanel());assertNotNull(controller.explorerPanel());assertNotNull(controller.inspectorPanel());assertNotNull(controller.consolePanel());
            assertFalse(controller.dockManager().contains("explorer"));assertFalse(controller.dockManager().contains("console"));
            controller.bus().publish(com.fixmer.mared.gui2.studio.events.StudioEvents.LogEvent.legacy("hidden console still receives events"));
            assertTrue(controller.consolePanel().consoleComponent().logPanel().snapshot().stream().anyMatch(row->row.text.contains("hidden console still receives events")));
        } finally {
            controller.shutdown();MaredSettings.setLayoutShowSidebar(files);MaredSettings.setLayoutShowRightPanel(help);MaredSettings.setLayoutShowConsole(logs);
        }
    }
    @Test void editorTranslationsAreStrictJsonAndCompleteInBothLanguages() throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("genesis.packagedMod"));
        try(var jar=new java.util.zip.ZipFile(root.toFile())){
            var mapper=new com.google.gson.Gson();
            java.util.Set<String> keys=null;
            for(String code:List.of("en_us","ru_ru")){
                String json=new String(jar.getInputStream(jar.getEntry("assets/mared/lang/"+code+".json")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                var reader=new com.google.gson.stream.JsonReader(new java.io.StringReader(json));reader.setLenient(false);
                var object=mapper.getAdapter(com.google.gson.JsonObject.class).read(reader);
                var editorKeys=object.keySet().stream().filter(k->k.startsWith("mared.editor.")).collect(java.util.stream.Collectors.toSet());
                assertTrue(editorKeys.size()>45);if(keys==null)keys=editorKeys;else assertEquals(keys,editorKeys);
                for(String key:editorKeys)assertFalse(object.get(key).getAsString().isBlank());
            }
        }
    }
}
