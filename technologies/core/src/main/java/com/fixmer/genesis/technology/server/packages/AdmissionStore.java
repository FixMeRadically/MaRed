package com.fixmer.genesis.technology.server.packages;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local forced snapshots; a missing/corrupt saved state never proves a stopped process. */
final class AdmissionStore implements AutoCloseable {
    record Binding(String root,UUID profile,UUID distribution,PackageModel.Side side,ProfileCoordinator.Scope scope){}
    record State(long epoch,long revision,ProfileCoordinator.Phase phase,UUID run,UUID install,String hash,UUID nonce,String reason){}
    final Path directory;final InstallIo.Lease lease;final Binding binding;final boolean previousBinding;final InstallIo.Fault fault;
    AdmissionStore(Path root,UUID expectedProfile,UUID distribution,PackageModel.Side side,ProfileCoordinator.Scope scope,InstallIo.Fault fault)throws IOException{
        root=root.toAbsolutePath().normalize();PackageFiles.safe(root);if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("PROFILE_ROOT_REQUIRED");root=root.toRealPath();if(root.toString().getBytes(StandardCharsets.UTF_8).length>4096)throw new IOException("PROFILE_PATH_LIMIT");this.fault=fault;directory=root.resolve(".genesis-admission");InstallIo.directory(directory);lease=new InstallIo.Lease(directory.resolve("owner.lock"));
        try{
            Path file=directory.resolve("binding");previousBinding=Files.exists(file,LinkOption.NOFOLLOW_LINKS);
            if(previousBinding){String[] f=InstallIo.unframe(PackageFiles.read(file,16384),6).split("\n");if(!f[0].equals("GENESIS-ADMISSION-BINDING-1"))throw new IOException("ADMISSION_BINDING_SCHEMA");binding=new Binding(unb64(f[1]),uuid(f[2]),optional(f[3]),PackageModel.Side.valueOf(f[4]),ProfileCoordinator.Scope.valueOf(f[5]));
                if(!binding.root().equals(root.toString())||binding.scope()!=scope||binding.side()!=side||!Objects.equals(distribution,binding.distribution())||expectedProfile!=null&&!expectedProfile.equals(binding.profile()))throw new IOException("ADMISSION_PROFILE_OR_SCOPE_MISMATCH");
            }else{
                if(Files.exists(directory.resolve("state"),LinkOption.NOFOLLOW_LINKS))throw new IOException("ADMISSION_BINDING_MISSING");UUID profile=expectedProfile==null?UUID.randomUUID():expectedProfile;PackageModel.nonzero(profile);binding=new Binding(root.toString(),profile,distribution,side,scope);
                InstallIo.atomic(file,InstallIo.framed("GENESIS-ADMISSION-BINDING-1\n"+b64(root.toString())+"\n"+profile+"\n"+nullable(distribution)+"\n"+side+"\n"+scope+"\n"),fault,"admission.binding");
            }
        }catch(IOException|RuntimeException bad){lease.close();throw bad;}
    }
    State read()throws IOException{Path file=directory.resolve("state");return Files.exists(file,LinkOption.NOFOLLOW_LINKS)?decodeState(PackageFiles.read(file,4096),PackageFiles.digest(bindingBytes())):null;}
    static ProfileCoordinator.Snapshot inspect(Path root)throws IOException{
        root=root.toAbsolutePath().normalize();PackageFiles.safe(root);root=root.toRealPath();Path directory=root.resolve(".genesis-admission");byte[] raw=PackageFiles.read(directory.resolve("binding"),16384);String[] f=InstallIo.unframe(raw,6).split("\n");
        try{if(!f[0].equals("GENESIS-ADMISSION-BINDING-1")||!unb64(f[1]).equals(root.toString()))throw new IOException("ADMISSION_PROFILE_OR_SCOPE_MISMATCH");UUID profile=uuid(f[2]);optional(f[3]);PackageModel.Side side=PackageModel.Side.valueOf(f[4]);if(side==PackageModel.Side.BOTH)throw new IOException("TARGET_SIDE_REQUIRED");var scope=ProfileCoordinator.Scope.valueOf(f[5]);State state=decodeState(PackageFiles.read(directory.resolve("state"),4096),PackageFiles.digest(raw));return new ProfileCoordinator.Snapshot(profile,scope,state.epoch(),state.revision(),state.phase(),state.run(),state.install(),state.hash(),state.reason(),false);}catch(IllegalArgumentException bad){throw new IOException("ADMISSION_STATE_CORRUPT",bad);}
    }
    private static State decodeState(byte[] raw,String bindingHash)throws IOException{
        String[] f=InstallIo.unframe(raw,10).split("\n");try{if(!f[0].equals("GENESIS-ADMISSION-STATE-1"))throw new IOException("ADMISSION_STATE_SCHEMA");var state=new State(Long.parseLong(f[1]),Long.parseLong(f[2]),ProfileCoordinator.Phase.valueOf(f[3]),optional(f[4]),optional(f[5]),f[6].equals("-")?null:PackageModel.hash(f[6]),optional(f[7]),f[8]);if(!f[9].equals(bindingHash))throw new IOException("ADMISSION_BINDING_HASH");validate(state);return state;}catch(IllegalArgumentException bad){throw new IOException("ADMISSION_STATE_CORRUPT",bad);}
    }
    void write(State state)throws IOException{
        validate(state);String body="GENESIS-ADMISSION-STATE-1\n"+state.epoch()+"\n"+state.revision()+"\n"+state.phase()+"\n"+nullable(state.run())+"\n"+nullable(state.install())+"\n"+(state.hash()==null?"-":state.hash())+"\n"+nullable(state.nonce())+"\n"+state.reason()+"\n"+PackageFiles.digest(bindingBytes())+"\n";InstallIo.atomic(directory.resolve("state"),InstallIo.framed(body),fault,"admission.state");
    }
    private byte[] bindingBytes()throws IOException{return PackageFiles.read(directory.resolve("binding"),16384);}
    private static void validate(State s)throws IOException{
        if(s.epoch()<1||s.revision()<1||s.reason()==null||!s.reason().matches("[A-Z_]{1,96}")||s.run()!=null&&s.install()!=null)throw new IOException("ADMISSION_STATE_SHAPE");
        boolean valid=switch(s.phase()){
            case IDLE->s.run()==null&&s.install()==null&&s.hash()==null&&s.nonce()==null;
            case PROCESS_RESERVED,PROCESS_RUNNING->s.run()!=null&&s.install()==null&&s.hash()==null&&s.nonce()!=null;
            case INSTALL_RESERVED,INSTALLING->s.run()==null&&s.install()!=null&&s.hash()!=null&&s.nonce()!=null;
            case RECOVERY_REQUIRED->true;
            case RECOVERING->s.run()==null&&s.nonce()!=null;
        };if(!valid)throw new IOException("ADMISSION_STATE_SHAPE");
    }
    static UUID uuid(String value){UUID id=UUID.fromString(value);PackageModel.nonzero(id);if(!id.toString().equals(value))throw new IllegalArgumentException("NONCANONICAL_UUID");return id;}
    private static UUID optional(String value){return value.equals("-")?null:uuid(value);}
    private static String nullable(UUID value){return value==null?"-":value.toString();}
    private static String b64(String value){return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private static String unb64(String value)throws IOException{try{String result=new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8);if(!b64(result).equals(value))throw new IOException("ADMISSION_ROOT_ENCODING");return result;}catch(IllegalArgumentException bad){throw new IOException("ADMISSION_ROOT_ENCODING",bad);}}
    public void close()throws IOException{lease.close();}
}
