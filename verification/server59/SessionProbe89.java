import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.genesis.technology.server.firstlaunch.*;import java.nio.file.*;import java.util.*;
/** Current-user and source context are explicit fixture files, never a real Minecraft identity claim. */
public final class SessionProbe89 {
 public static void main(String[] a)throws Exception{Path root=Path.of(a[0]);byte[] key=Files.readAllBytes(root.resolve("channel.key"));var expected=LocalApply67.verify(key,Files.readAllBytes(root.resolve("fixture89.context")));var request=SessionHandoff89.request(root,expected,System.currentTimeMillis());var user=Map.of("account",expected.get("account"),"name","TestPlayer","accessToken",expected.get("mode").equals("ONLINE")?"private-fixture89":"","userType",expected.get("mode").equals("ONLINE")?"msa":"legacy");SessionHandoff89.answer(root,request,user,System.currentTimeMillis());}
}
