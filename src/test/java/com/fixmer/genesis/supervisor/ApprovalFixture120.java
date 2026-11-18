package com.fixmer.genesis.supervisor;
import java.nio.file.Path;import java.io.IOException;import java.util.Map;
/** Explicit test-fixture approval; not shipped in the runtime jar. */
public final class ApprovalFixture120 {
 public static String stage(ModDeployment d,Path source,String hash,Map<String,String> loaded)throws IOException{return stage(d,source,hash,loaded,"");}
 public static String stage(ModDeployment d,Path source,String hash,Map<String,String> loaded,String project)throws IOException{return d.stageReviewed98(source,d.review98(source,hash,loaded,project),loaded);}
}
