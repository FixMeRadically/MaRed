package com.fixmer.genesis.technology.server.remote;
import java.nio.file.*;import java.util.*;
/** Gate output contains public state only, never capabilities or access tokens. */
public final class BootstrapGateCli76 {
 public static void main(String[] a){try{if(a.length!=3&&!(a.length==4&&a[0].equals("poll")))throw new IllegalArgumentException();var state=a.length==4?Bootstrap76.pollCandidate(Path.of(a[1]),a[2],a[3],()->false):Bootstrap76.checkCandidate(Path.of(a[0]),a[1],a[2],()->false);System.out.println(Base64.getEncoder().encodeToString(ControlWire73.encode(state.values())));}catch(Exception bad){System.err.println("BOOTSTRAP76_REJECTED");System.exit(2);}}
}
