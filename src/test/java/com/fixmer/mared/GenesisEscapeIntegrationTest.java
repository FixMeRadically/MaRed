package com.fixmer.mared;

import com.fixmer.mared.gui2.genesis.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Controller/space input regression without a graphics context or rendering. */
public class GenesisEscapeIntegrationTest {
    @Test void overviewPassesEscapeToShellInsteadOfSwallowingIt() {
        assertFalse(new com.fixmer.mared.gui2.spaces.GenesisSpace(new com.fixmer.mared.gui2.navigation.MaredNavigation(new com.fixmer.mared.gui2.navigation.SpaceGraph())).keyPressed(256,0,0));
    }
    @Test void focusedOrEnteringEscapeCancelsThenNextEscapePassesThrough() throws Exception {
        var world=new GenesisWorld();var controller=world.controller();
        var flight=GenesisController.class.getDeclaredMethod("startFlight",GenesisWorld.class,com.fixmer.mared.gui2.genesis.node.GenesisNode.class);flight.setAccessible(true);
        flight.invoke(controller,world,world.nodes().get(1));assertEquals(GenesisController.State.FLYING,controller.state());
        assertTrue(controller.tryEscape(world));assertEquals(GenesisController.State.IDLE,controller.state());assertNull(controller.selected());assertFalse(controller.tryEscape(world));
        flight.invoke(controller,world,world.nodes().get(1));controller.tick(world,2f);
        assertEquals(GenesisController.State.FOCUSED,controller.state());world.camera().setZoomTarget(3.4f);controller.onMouseScrolled(world,1);
        assertEquals(GenesisController.State.ENTERING,controller.state());assertTrue(controller.tryEscape(world));assertEquals(GenesisController.State.IDLE,controller.state());assertFalse(controller.tryEscape(world));
    }
}
