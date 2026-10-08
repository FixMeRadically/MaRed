package com.fixmer.mared.gui2.workspace;


import com.fixmer.mared.gui2.framework.core.MaredComponent;

import java.util.function.Supplier;


public record WorkspaceDescriptor(

        WorkspaceId id,

        String title,

        Supplier<MaredComponent> factory

) {

}