package com.fixmer.genesis.technology.workspace.live122;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121;
import com.fixmer.genesis.technology.workspace.collaboration121.WorkspacePolicy121.*;
import java.util.*;
/** The consent screen evaluates the same role, deny rules and hidden panels as the server. */
public final class WorkspaceRights131 {
    private final WorkspacePolicy121 policy;private final UUID target=new UUID(131,2);private final String path;
    public WorkspaceRights131(Map<String,String> fields){UUID owner=new UUID(131,1);policy=new WorkspacePolicy121(owner);Member rights=WorkspaceInvitations131.rights(fields);policy.grant(owner,0,target,rights);path=fields.getOrDefault("prefix","").isEmpty()?"src/Main.java":fields.get("prefix");}
    public boolean allows(Capability capability){return policy.allows(target,capability,path);}
    public boolean panel(Panel panel){return policy.panel(target,panel,path);}
}
