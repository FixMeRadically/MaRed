package com.fixmer.genesis.technology.server.waiting;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.fixmer.genesis.technology.server.preparation.ServerUpdatePreparation;

final class WaitingPreparation {
    private final Path preparation;private final String intent,update,sha;
    private WaitingPreparation(Path preparation,String intent,String update,String sha){this.preparation=preparation;this.intent=intent;this.update=update;this.sha=sha;}
    static WaitingPreparation load(Path root)throws IOException{
        Path binding=root.resolve("prepared.v57");if(!Files.exists(binding,LinkOption.NOFOLLOW_LINKS))return null;
        var m=WaitingFiles.read(binding);WaitingFiles.keys(m,"schema","preparationBase64","intentSha256","update","packageSha256");
        if(!m.get("schema").equals("57"))throw new IOException("PREPARATION_SCHEMA");
        String encoded=m.get("preparationBase64");byte[] bytes;try{bytes=Base64.getUrlDecoder().decode(encoded);}catch(IllegalArgumentException bad){throw new IOException("PREPARATION_PATH",bad);}
        String name=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).equals(encoded))throw new IOException("NONCANONICAL_PATH");
        Path path=WaitingFiles.safe(Path.of(name));if(!path.toString().equals(name)||!m.get("intentSha256").matches("[a-f0-9]{64}"))throw new IOException("PREPARATION_PATH");
        var result=new WaitingPreparation(path,m.get("intentSha256"),m.get("update"),m.get("packageSha256"));result.check();return result;
    }
    void check()throws IOException{
        var verified=ServerUpdatePreparation.recheck(preparation,intent);
        if(!Boolean.TRUE.equals(verified.get("readyForReview"))||!Boolean.TRUE.equals(verified.get("inputsChecked"))||!update.equals(String.valueOf(verified.get("update")))||!sha.equals(verified.get("packageSha256")))throw new IOException("PREPARED_PACKAGE_NOT_CURRENT");
    }
    void matches(String update,String sha)throws IOException{if(!this.update.equals(update)||!this.sha.equals(sha))throw new IOException("WAITING_PREPARATION_MISMATCH");}
    static void init(Path root,Path preparation,String intent,long barrier,String audience)throws Exception{
        if(audience.equals("-")||audience.isBlank())throw new IOException("EXPLICIT_PLAYER_AUDIENCE_REQUIRED");
        preparation=WaitingFiles.safe(preparation);var verified=ServerUpdatePreparation.recheck(preparation,intent);
        if(!Boolean.TRUE.equals(verified.get("readyForReview"))||!Boolean.TRUE.equals(verified.get("inputsChecked")))throw new IOException("PREPARED_PACKAGE_REQUIRED");
        root=WaitingFiles.safe(root);Path profile=WaitingFiles.safe(Path.of(String.valueOf(verified.get("profile"))));
        for(Path reserved:List.of(preparation,profile))if(root.startsWith(reserved)||reserved.startsWith(root))throw new IOException("WAITING_STORAGE_MUST_BE_DISJOINT");
        String update=String.valueOf(verified.get("update")),sha=String.valueOf(verified.get("packageSha256"));
        WaitingCli.run(new String[]{"init",root.toString(),update,sha,Long.toString(barrier),audience});
        WaitingFiles.write(root.resolve("prepared.v57"),Map.of("schema","57","preparationBase64",Base64.getUrlEncoder().withoutPadding().encodeToString(preparation.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),"intentSha256",intent,"update",update,"packageSha256",sha),false);
        System.out.println("Verified server package bound for consent. No client install or restart permission granted.");
    }
}
