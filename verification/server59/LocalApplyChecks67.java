import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Real pure-JDK wire codec/workspace binding checks. No Minecraft or Windows process simulation. */
public final class LocalApplyChecks67 {
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[32];Arrays.fill(key,(byte)'k');
        if(args.length>0&&args[0].equals("verify")){
            var m=LocalApply67.verify(key,Base64.getDecoder().decode(args[1]));if(!"C:/моды/my mod.jar".equals(m.get("jar")))throw new AssertionError("Unicode path changed");System.out.println("Verified Python message in Java");return;
        }
        if(args.length>0&&args[0].equals("sign")){System.out.println(Base64.getEncoder().encodeToString(LocalApply67.sign(key,Map.of("jar","C:/моды/my mod.jar","action","PREPARE","sha",""))));return;}
        if(args.length>0&&args[0].equals("offer")){var session=FirstLaunchSession.open(System.getenv(),"server");var details=session.repeatOffer(UUID.fromString(args[1]));session.repeatConsent("ACCEPTED",7,UUID.fromString(args[2]),details.get("serverPackage"));System.out.println("Verified repeat offer and wrote bound receipt");return;}
        if(args.length>0&&args[0].equals("workspace")){var b=LocalApply67.open(System.getenv());if(b==null||!Files.isDirectory(b.workspace))throw new AssertionError("Workspace missing");System.out.println(b.workspace);return;}
        var input=LocalApply67.sign(key,Map.of("jar","C:/моды/my mod.jar","action","PREPARE","sha",""));if(!LocalApply67.verify(key,input).get("sha").isEmpty())throw new AssertionError();
        input[0]^=1;boolean refused=false;try{LocalApply67.verify(key,input);}catch(Exception expected){refused=true;}if(!refused)throw new AssertionError("Tamper accepted");
        System.out.println("LocalApply67 codec checks passed");
    }
}
