package com.fixmer.mared.gui2.workspace;


import com.fixmer.mared.gui2.modules.ModuleId;



import java.util.EnumMap;
import java.util.Map;



public final class ModuleWorkspaceMap {


    private static final Map<ModuleId, WorkspaceId> DEFAULTS =
            new EnumMap<>(ModuleId.class);



    static {

        DEFAULTS.put(
                ModuleId.CONTENT,
                WorkspaceId.CONTENT_HOME
        );


        DEFAULTS.put(
                ModuleId.WORLD,
                WorkspaceId.WORLD_HOME
        );


        DEFAULTS.put(
                ModuleId.LOGIC,
                WorkspaceId.LOGIC_HOME
        );


        DEFAULTS.put(
                ModuleId.RESOURCES,
                WorkspaceId.RESOURCE_BROWSER
        );


        DEFAULTS.put(
                ModuleId.TOOLS,
                WorkspaceId.TOOL_HOME
        );


        DEFAULTS.put(
                ModuleId.SCENARIOS,
                WorkspaceId.SCENARIO_HOME
        );

    }



    private ModuleWorkspaceMap(){}



    public static WorkspaceId get(
            ModuleId module
    ){

        return DEFAULTS.get(module);

    }

}