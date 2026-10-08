package com.fixmer.mared.gui2.workspace;


import com.fixmer.mared.gui2.framework.core.MaredComponent;



public final class WorkspaceFactory {


    private WorkspaceFactory(){}



    public static MaredComponent create(
            WorkspaceId id
    ){

        WorkspaceDescriptor descriptor =
                WorkspaceRegistry.get(id);


        if(descriptor == null)
            return null;


        return descriptor.factory().get();

    }

}