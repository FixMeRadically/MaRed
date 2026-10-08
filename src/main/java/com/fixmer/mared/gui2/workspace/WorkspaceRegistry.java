package com.fixmer.mared.gui2.workspace;


import java.util.*;


public final class WorkspaceRegistry {


    private static final Map<WorkspaceId, WorkspaceDescriptor> WORKSPACES =
            new LinkedHashMap<>();


    private WorkspaceRegistry(){}



    public static void register(
            WorkspaceDescriptor descriptor
    ){

        if(descriptor == null)
            return;


        WORKSPACES.put(
                descriptor.id(),
                descriptor
        );

    }



    public static WorkspaceDescriptor get(
            WorkspaceId id
    ){

        return WORKSPACES.get(id);

    }



    public static List<WorkspaceDescriptor> all(){

        return List.copyOf(
                WORKSPACES.values()
        );

    }



    public static void clear(){

        WORKSPACES.clear();

    }

}