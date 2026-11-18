import com.fixmer.genesis.technology.server.remote.*;import com.fixmer.mared.ServerAccessChecks88;import java.nio.file.*;import java.util.*;
/** Cross-language fixture; operator flags are explicit test inputs, not ServerPlayer proof. */
public final class AccessProbe88 {
 public static void main(String[] a){try{Path r=Path.of(a[1]);if(a[0].equals("init")){ServerAccessChecks88.fixture(r);System.out.println("FIXTURE");return;}var s=ServerAccess88.open(r);UUID actor=UUID.fromString(a[2]);switch(a[0]){
 case "hold"->{try(var c=java.nio.channels.FileChannel.open(r.resolve("access88.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var held=c.lock(0,1,false)){System.out.println("LOCKED");System.out.flush();System.in.read();}}
 case "change"->s.change(actor,true,true,UUID.fromString(a[3]),a[4],Long.parseLong(a[5]),UUID.randomUUID());
 case "queue"->s.queue(actor,Boolean.parseBoolean(a[3]),true,System.currentTimeMillis(),UUID.fromString(a[4]),a[5],a[6]);
 case "view"->System.out.println(Base64.getEncoder().encodeToString(ControlWire73.encode(s.view(actor,Boolean.parseBoolean(a[3]),true))));
 default->throw new IllegalArgumentException();}}catch(Exception e){String c=e.getMessage();System.err.println(c!=null&&c.matches("(?:ACCESS88|DEDICATED75)_[A-Z_]{1,60}")?c:"FIXTURE_REFUSED");System.exit(2);}}
}
