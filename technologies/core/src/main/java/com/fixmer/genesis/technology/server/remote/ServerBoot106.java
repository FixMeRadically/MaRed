package com.fixmer.genesis.technology.server.remote;

import com.fixmer.genesis.technology.server.firstlaunch.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.*;
import java.nio.channels.*;
import java.nio.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Foreground console for the existing run.bat. The actual server remains a child of its Job owner. */
public final class ServerBoot106 {
    private ServerBoot106() {}
    public static Path currentApp(Path server)throws Exception{
        var jars=new ArrayList<Path>();Path mods=FirstLaunchFiles.safe(server.resolve("mods"));
        try(var files=Files.list(mods)){var entries=files.filter(p->p.getFileName().toString().endsWith(".jar")).limit(257).toList();if(entries.size()>256)throw new IOException("SERVER106_MOD_LIMIT");for(Path p:entries){
            FirstLaunchFiles.safe(p);if(Files.size(p)>64L*1024*1024)throw new IOException("SERVER106_MOD_LIMIT");
            try(var jar=new ZipFile(p.toFile())){if(jar.getEntry("com/fixmer/mared/Mared.class")!=null)jars.add(p);}
        }}
        if(jars.isEmpty())return null;if(jars.size()!=1)throw new IOException("SERVER104_DUPLICATE_GENESIS");
        byte[] snapshot=FirstLaunchFiles.read(jars.get(0),64*1024*1024);Path temporary=Files.createTempFile("genesis-boot106-",".jar");
        try{FirstLaunchFiles.privateMode(temporary);Files.write(temporary,snapshot);try(var jar=new ZipFile(temporary.toFile())){
            var resource=jar.getEntry("genesis/server59/controller.zip");if(resource==null||resource.getSize()>100L*1024*1024)throw new IOException("SERVER106_MOD_BOOTSTRAP_MISSING");
            byte[] bundle;try(var in=jar.getInputStream(resource)){bundle=in.readNBytes(100*1024*1024+1);}if(bundle.length>100*1024*1024)throw new IOException("SERVER106_MOD_LIMIT");
            return ServerApp104.deploy(server,new ByteArrayInputStream(bundle),new ByteArrayInputStream(snapshot));
        }}finally{Files.deleteIfExists(temporary);}
    }
    private static String phaseText(String phase){return switch(phase){case "STARTING","RESTARTING"->"Запускаем сервер…";case "RUNNING"->"Сервер работает.";case "CHECKING"->"Проверяем настройки перезапуска…";case "STOPPING"->"Сохраняем мир и останавливаем сервер…";case "STOPPED"->"Сервер остановлен.";case "WAITING_FOR_STOP"->"Ожидаем завершения сервера.";case "RECOVERY_REQUIRED"->"Нужна проверка восстановления.";default->"Не удалось продолжить запуск. Проверь журнал.";};}
    public static int run(Path server)throws Exception{
        server=FirstLaunchFiles.safe(server);Path app=currentApp(server);
        if(app==null){ServerAgent106.removeOwnOption(server);System.out.println("[Genesis] MaRed is absent. Its startup option was removed; continuing the normal Minecraft launcher.");return -1;}
        Path log=ServerApp104.root(server).resolve("logs/console-launch106.log");Files.createDirectories(log.getParent());
        Path java=Path.of(System.getProperty("java.home"),"bin","java.exe");Process helper=ServerApp104.helper(app,"start",server,java,log);
        if(!helper.waitFor(60,TimeUnit.SECONDS)){helper.destroy();throw new IOException("SERVER104_HELPER_TIMEOUT");}
        if(helper.exitValue()!=0){System.err.println(Files.readString(log));throw new IOException("SERVER106_CONTROLLER_START_FAILED");}
        final Path profile=server;Thread hook=new Thread(()->{try{var s=ServerApp104.state(profile);if(Set.of("RUNNING","STARTING","WAITING_FOR_STOP").contains(s.getOrDefault("phase","")))ServerConsole106.submit(profile,"stop");}catch(Exception ignored){}},"genesis-console106-close");Runtime.getRuntime().addShutdownHook(hook);
        var input=new LinkedBlockingQueue<String>(64);Thread reader=new Thread(()->{try{Charset charset=System.console()==null?Charset.defaultCharset():System.console().charset();var lines=new BufferedReader(new InputStreamReader(System.in,charset));for(String line;(line=lines.readLine())!=null;)if(!line.isBlank())input.offer(line);}catch(IOException ignored){}},"genesis-console106-input");reader.setDaemon(true);reader.start();
        String previous="",launch="";long position=0,spawn=0;Object fileKey=null;boolean seen=false;Path latest=FirstLaunchFiles.safe(server.resolve("logs/latest.log"));
        System.out.println("[Genesis] Контроллер запущен. Здесь доступны команды Minecraft, stop и genesisrestart.");
        try{while(true){
            Map<String,String> state=ServerApp104.state(server);String phase=state.getOrDefault("phase","STOPPED");
            if(!phase.equals(previous)){System.out.println("[Genesis] "+phaseText(phase));if(phase.equals("RUNNING"))try{var target128=ServerJoin128.target(server,state);for(String lan128:target128.lan())System.out.println("[Genesis] Адрес для игроков в локальной сети: "+lan128);System.out.println("[Genesis] Адрес для входа из Minecraft: "+target128.address()+" — Сетевая игра → Прямое подключение.");}catch(Exception ignored){}previous=phase;}if(Set.of("STARTING","RUNNING","STOPPING","RESTARTING","CHECKING","WAITING_FOR_STOP").contains(phase))seen=true;
            String current=state.getOrDefault("launch","");if(!current.equals(launch)){launch=current;position=0;fileKey=null;spawn=System.currentTimeMillis();if(phase.equals("RUNNING")&&Files.isRegularFile(latest))position=Files.size(latest);}
            try{if(Files.isRegularFile(latest)&&Files.getLastModifiedTime(latest).toMillis()>=spawn){
                var attrs=Files.readAttributes(FirstLaunchFiles.safe(latest),BasicFileAttributes.class);if(fileKey!=null&&!Objects.equals(fileKey,attrs.fileKey())||attrs.size()<position)position=0;fileKey=attrs.fileKey();
                try(var channel=FileChannel.open(latest,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){channel.position(position);ByteBuffer chunk=ByteBuffer.allocate(65536);int n=channel.read(chunk);if(n>0){chunk.flip();var characters=CharBuffer.allocate(n);var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE);decoder.decode(chunk,characters,false);characters.flip();System.out.print(characters);position+=chunk.position();}}
            }}catch(IOException logBusy){}
            String command;while((command=input.poll())!=null)try{ServerConsole106.submit(server,command);}catch(Exception refused){System.err.println("[Genesis] Command was not accepted: "+refused.getMessage());}
            if(Set.of("FAILED","BOOTSTRAP_FAILED_RESTORED","RECOVERY_REQUIRED","OWNER_UNAVAILABLE").contains(phase)){System.err.println("[Genesis] Подробности в .genesis/server104/logs. Из панели можно сохранить отчёт ZIP.");return 2;}
            if(phase.equals("STOPPED")&&seen)return 0;Thread.sleep(150);
        }}finally{try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException shuttingDown){}}
    }
}
