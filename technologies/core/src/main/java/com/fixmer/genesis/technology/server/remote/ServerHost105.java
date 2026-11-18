package com.fixmer.genesis.technology.server.remote;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

/** Standalone host: no disk/process reads on the Swing event thread, no Minecraft classes. */
public final class ServerHost105 {
    @FunctionalInterface public interface Installer { Path deploy(Path server) throws Exception; }
    private ServerHost105() {}
    public static void show(Path initial,Path java,Installer installer) { new Window(initial,java,installer).show(); }
    private static final class Window {
        final JFrame frame=new JFrame("Genesis — сервер");
        final JTextField folder;final JButton main=new JButton("Запустить сервер"),browse=new JButton("Выбрать"),advanced=new JButton("Продвинутые настройки");
        final JTextArea status=new JTextArea(3,40);
        final ExecutorService io=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"genesis-host105-io");t.setDaemon(true);return t;});
        final AtomicBoolean reading=new AtomicBoolean();final Installer installer;final Path java;
        volatile Map<String,String> state=Map.of("phase","READING");volatile String scope="",error="";volatile boolean working;
        volatile Path report;volatile UUID pending;volatile String pendingRun="",pendingGeneration="";javax.swing.Timer timer;
        Window(Path initial,Path java,Installer installer){folder=new JTextField(initial.toString());this.java=java;this.installer=installer;}
        String text(){return folder.getText().trim();}
        ServerView105.View view(){return ServerView105.of(state,!scope.isBlank(),working||pending!=null);}
        void show(){
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);var body=new JPanel(new BorderLayout(8,12));body.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
            var location=new JPanel(new BorderLayout(8,6));location.add(new JLabel("Папка сервера"),BorderLayout.NORTH);location.add(folder);location.add(browse,BorderLayout.EAST);body.add(location,BorderLayout.NORTH);
            var center=new JPanel(new BorderLayout(8,10));center.add(main,BorderLayout.NORTH);status.setLineWrap(true);status.setWrapStyleWord(true);status.setEditable(false);status.setOpaque(false);status.setFocusable(false);center.add(status);body.add(center);body.add(advanced,BorderLayout.SOUTH);
            browse.addActionListener(e->{var chooser=new JFileChooser(text());chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(chooser.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)folder.setText(chooser.getSelectedFile().toString());});
            main.addActionListener(e->{if(view().canStart())action(view().operation());});advanced.addActionListener(e->advanced());
            frame.setContentPane(body);frame.setSize(650,290);frame.setMinimumSize(new Dimension(420,260));frame.setLocationRelativeTo(null);frame.setVisible(true);
            timer=new javax.swing.Timer(1000,e->refresh());timer.start();frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(working){status.setText("Дождись завершения текущего действия.");return;}timer.stop();io.shutdown();frame.dispose();}});refresh();
        }
        void refresh(){
            String selected=text();if(!selected.equals(scope)){scope=selected;state=Map.of("phase",selected.isBlank()?"UNSELECTED":"READING");error="";report=null;pending=null;}
            if(!selected.isBlank()&&reading.compareAndSet(false,true))io.execute(()->{
                try{var latest=ServerLocal105.observe(Path.of(selected));if(selected.equals(scope)){
                    state=latest;if(pending!=null&&(!latest.getOrDefault("run","").equals(pendingRun)||!latest.getOrDefault("generation","").equals(pendingGeneration)||!latest.getOrDefault("phase","").equals("RUNNING")||pending.toString().equals(latest.get("request"))&&Set.of("COMPLETED","REJECTED","FAILED").contains(latest.getOrDefault("requestResult",""))))pending=null;
                }}catch(Exception failed){if(selected.equals(scope)){state=Map.of("phase","OWNER_UNAVAILABLE");error=code(failed);pending=null;}}finally{reading.set(false);}
            });
            var v=view();main.setText(v.operation().equals("restart")?"Перезапустить сервер":"Запустить сервер");main.setEnabled(v.canStart());folder.setEditable(!working);browse.setEnabled(!working);advanced.setEnabled(!working);
            String message=working?"Выполняем…":pending!=null?"Команда отправлена. Ждём сервер…":phase(v.phase());String detail=!error.isBlank()?error:v.error();if(!detail.isBlank())message+="\n"+hint(detail);else if(v.phase().equals("RUNNING"))message+="\nМожно закрыть это окно: сервер продолжит работать.";if(report!=null&&detail.isBlank())message+="\nОтчёт: "+report.getFileName();status.setText(message);
        }
        void action(String operation){
            if(working)return;String selected=text();Path server;
            try{if(selected.isBlank())throw new IOException("SERVER105_FOLDER_REQUIRED");server=Path.of(selected).toAbsolutePath();}catch(Exception bad){error="SERVER105_FOLDER_REQUIRED";refresh();return;}
            working=true;error="";main.setEnabled(false);folder.setEditable(false);browse.setEnabled(false);advanced.setEnabled(false);
            io.execute(()->{try{
                if(operation.equals("report")){Path saved=ServerDiagnostics105.export(server);if(selected.equals(scope))report=saved;}
                else if(operation.equals("restart")||operation.equals("stop")){var before=ServerLocal105.observe(server);UUID id=ServerLocal105.command(server,operation);if(selected.equals(scope)){pendingRun=before.getOrDefault("run","");pendingGeneration=before.getOrDefault("generation","");pending=id;}}
                else if(operation.equals("acknowledge"))ServerLocal105.acknowledge(server);
                else {
                    Path app=installer.deploy(server),log=ServerApp104.root(server).resolve("logs/launcher.log");Files.createDirectories(log.getParent());Process helper=ServerApp104.helper(app,"start",server,java,log);
                    if(!helper.waitFor(60,TimeUnit.SECONDS)){helper.destroy();throw new IOException("SERVER104_HELPER_TIMEOUT");}
                    if(helper.exitValue()!=0){String code="SERVER104_START_FAILED";if(Files.size(log)<32768)for(String line:Files.readString(log).split("\\R"))if(line.matches("[A-Z][A-Z0-9_]{1,100}"))code=line;throw new IOException(code);}
                }
            }catch(Exception failed){ServerApp104.diagnostic(server,failed);if(selected.equals(scope))error=code(failed);}
            finally{working=false;SwingUtilities.invokeLater(()->{if(frame.isDisplayable()){refresh();if(operation.equals("report")&&report!=null&&error.isBlank())status.setText("Отчёт сохранён: "+report.getFileName()+"\nОткрыть папку можно в продвинутых настройках.");}});}});
        }
        void advanced(){
            var v=view();var choices=new ArrayList<String>();choices.add("Сохранить отчёт ZIP");if(report!=null)choices.add("Открыть папку отчёта");if(v.canStop())choices.add("Остановить сервер");if(v.needsReview())choices.add(state.getOrDefault("repairPending","false").equals("true")?"Повторить восстановление":"Разрешить запуск после проверки");choices.add("Закрыть");
            int choice=JOptionPane.showOptionDialog(frame,"Все файлы и логи хранятся в выбранной папке сервера.","Продвинутые настройки",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,choices.toArray(),choices.get(0));if(choice<0)return;String action=choices.get(choice);
            if(action.equals("Сохранить отчёт ZIP"))action("report");
            else if(action.equals("Открыть папку отчёта")){try{Desktop.getDesktop().open(report.getParent().toFile());}catch(Exception failed){error="SERVER105_OPEN_REPORT_FAILED";}}
            else if(action.equals("Остановить сервер")&&JOptionPane.showConfirmDialog(frame,"Сохранить мир и выключить сервер?","Остановка",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)action("stop");
            else if(action.equals("Повторить восстановление"))action("repair");
            else if(action.equals("Разрешить запуск после проверки")&&JOptionPane.showConfirmDialog(frame,"Мир не откатывается. Ты проверил логи и его совместимость с возвращёнными модами?","Проверка восстановления",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)action("acknowledge");
        }
        String code(Exception e){String s=e.getMessage();return s!=null&&s.matches("[A-Z][A-Z0-9_]{1,100}")?s:"SERVER104_OPERATION_FAILED";}
        String phase(String p){return switch(p){case "UNSELECTED"->"Выбери папку сервера.";case "READING"->"Проверяем состояние…";case "RUNNING"->"Сервер работает.";case "CHECKING"->"Проверяем настройки перед перезапуском…";case "STARTING","RESTARTING"->"Сервер запускается…";case "STOPPING"->"Сохраняем мир и останавливаем сервер…";case "RECOVERY_REQUIRED"->"Нужна проверка восстановления. Открой продвинутые настройки.";case "WAITING_FOR_STOP"->"Ожидаем завершения сервера. Он остаётся под управлением.";case "FAILED","BOOTSTRAP_FAILED_RESTORED"->"Не удалось запустить сервер. Сохрани отчёт в продвинутых настройках.";case "OWNER_UNAVAILABLE"->"Контроллер не отвечает. Сохрани отчёт.";case "INVALID_FOLDER"->"Папка не найдена. Проверь выбранный путь.";default->"Готов к запуску.";};}
        String hint(String code){return switch(ServerView105.hint(code)){case "eula"->"Прими EULA в eula.txt, если согласен с её условиями.";case "port"->"Порт занят. Останови ранее запущенный сервер.";case "runtime"->"Проверь, что выбрана папка установленного NeoForge 1.21.1 сервера.";case "java"->"Нужен JDK 21.";case "python"->"Не удалось запустить Python 3. Проверь его установку.";case "pending"->"Восстановление файлов ещё не завершено.";case "folder"->"Выбери существующую папку сервера.";default->"Причина записана в логах. Сохрани отчёт ZIP.";};}
    }
}
