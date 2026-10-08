package com.fixmer.mared.gui2.workspace;


import com.fixmer.mared.gui2.workspace.defaults.EmptyWorkspace;



public final class WorkspaceBootstrap {



    private WorkspaceBootstrap(){}




    public static void bootstrap(){


        WorkspaceRegistry.register(

                new WorkspaceDescriptor(
                        WorkspaceId.CONTENT_HOME,
                        "Content",
                        EmptyWorkspace::new
                )

        );



        WorkspaceRegistry.register(

                new WorkspaceDescriptor(
                        WorkspaceId.WORLD_HOME,
                        "World",
                        EmptyWorkspace::new
                )

        );



        WorkspaceRegistry.register(

                new WorkspaceDescriptor(
                        WorkspaceId.LOGIC_HOME,
                        "Logic",
                        EmptyWorkspace::new
                )

        );



        WorkspaceRegistry.register(

                new WorkspaceDescriptor(
                        WorkspaceId.RESOURCE_BROWSER,
                        "Resources",
                        EmptyWorkspace::new
                )

        );



        WorkspaceRegistry.register(

                new WorkspaceDescriptor(
                        WorkspaceId.TOOL_HOME,
                        "Tools",
                        EmptyWorkspace::new
                )

        );



        WorkspaceRegistry.register(

                new WorkspaceDescriptor(
                        WorkspaceId.SCENARIO_HOME,
                        "Scenarios",
                        EmptyWorkspace::new
                )

        );


    }

}