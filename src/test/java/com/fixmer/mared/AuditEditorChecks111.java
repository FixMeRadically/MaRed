package com.fixmer.mared;
import com.fixmer.mared.gui2.framework.components.editor.*;
import com.fixmer.mared.gui2.studio.action.*;
import com.fixmer.mared.services.threading.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Actual document/history/actions/scheduler. Does not exercise render or Minecraft input dispatch. */
public final class AuditEditorChecks111 {
    private static int checks;
    static void ok(boolean value){checks++;if(!value)throw new AssertionError("editor111 check "+checks);}
    static void await(Callable<Boolean> condition)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.call()){if(System.nanoTime()>end)throw new AssertionError("editor111 timeout");Thread.sleep(2);}
    }
    public static void main(String[] args)throws Exception{
        for(String symbol:new String[]{"😀","𝄞","𐐷","a","я"}){
            EditorDocument d=new EditorDocument();String original="x"+symbol+"y";d.setValue(original);d.setCursor(0,1+symbol.length());
            d.deleteBeforeCursor();ok(d.getValue().equals("xy"));ok(d.cursorCol()==1);
            d.setValue(original);d.setCursor(0,1);d.deleteAfterCursor();ok(d.getValue().equals("xy"));
            d.setValue(original);d.setCursor(0,1);d.moveRight();ok(d.cursorCol()==1+symbol.length());d.moveLeft();ok(d.cursorCol()==1);
            d.startSelection(0,1);d.extendSelection(0,1+symbol.length());ok(d.getSelectedText().equals(symbol));
            EditorHistory h=new EditorHistory();Edit e=new Edit(0,1,symbol,"",0,1+symbol.length(),0,1);
            d.clearSelection();d.applyEdit(e);h.record(e);ok(d.getValue().equals("xy"));ok(h.undo(d));ok(d.getValue().equals(original));ok(h.redo(d));ok(d.getValue().equals("xy"));
        }
        EditorDocument d=new EditorDocument();d.setValue("x😀y");d.setCursor(0,2);ok(d.cursorCol()==1);d.startSelection(0,2);d.extendSelection(0,3);ok(d.getSelectedText().equals("😀"));
        d.setCursor(0,3);d.deleteNBeforeCursor(1);ok(d.getValue().equals("xy"));
        for(String clip:new String[]{"a\r\nb", "a\rb", "a\nb", "a\r\n", "😀\r\nb"}){
            d.setValue("xy");d.setCursor(0,1);int[] after=Edit.advancePosition(0,1,clip);
            Edit edit=new Edit(0,1,"",clip,0,1,after[0],after[1]);EditorHistory history=new EditorHistory();
            d.insertString(clip);history.record(edit);String inserted=d.getValue();ok(!inserted.contains("\r"));
            ok(history.undo(d));ok(d.getValue().equals("xy"));ok(history.redo(d));ok(d.getValue().equals(inserted));
        }
        d.setValue("a\rb\r\nc");ok(d.lineCount()==3);ok(d.getValue().equals("a\nb\nc"));
        Shortcut shortcut=Shortcut.parse("Ctrl+S");ok(shortcut!=null);ok(shortcut.matches(83,2|16|32));ok(!shortcut.matches(83,2|1));ok(Shortcut.parse("Ctrl+S+")==null);
        AtomicInteger command=new AtomicInteger(),script=new AtomicInteger();
        EditorActionRegistry registry=new EditorActionRegistry();
        registry.register(EditorAction.of("command","command","Ctrl+S",ctx->command.incrementAndGet()).inScope(EditorAction.Scope.COMMANDS));
        registry.register(EditorAction.of("script","script","Ctrl+S",ctx->script.incrementAndGet()).inScope(EditorAction.Scope.SCRIPTS));
        ok(registry.findConflicts().isEmpty());EditorActionContext ctx=new EditorActionContext(null,null,null,null);
        ok(registry.executeByKey(83,2|16,ctx));ok(command.get()==1&&script.get()==0);ctx.setCommandsActive(false);
        ok(registry.executeByKey(83,2|32,ctx));ok(command.get()==1&&script.get()==1);ok(!registry.execute("command",ctx));ok(registry.execute("script",ctx));
        ok(new EditorActionRegistry().findConflicts().isEmpty());
        ConcurrentLinkedQueue<Runnable> callbacks=new ConcurrentLinkedQueue<>();
        MainThreadDispatcher main=new MainThreadDispatcher(){
            public void execute(Runnable work){callbacks.add(work);}
            public <T> CompletableFuture<T> submit(Callable<T> work){throw new UnsupportedOperationException();}
            public boolean isMainThread(){return true;}
        };
        AsyncTaskScheduler scheduler=new AsyncTaskScheduler(1,main);
        try{
            AtomicInteger success=new AtomicInteger(),errors=new AtomicInteger();
            TaskHandle finished=scheduler.submit(()->42,v->success.set(v),e->errors.incrementAndGet());finished.future().get(5,TimeUnit.SECONDS);await(()->!callbacks.isEmpty());
            ok(success.get()==0);callbacks.remove().run();ok(success.get()==42);ok(!finished.cancel());ok(!finished.isCancelled());
            TaskHandle failed=scheduler.submit(()->{throw new IllegalStateException("expected");},v->success.incrementAndGet(),e->errors.incrementAndGet());
            try{failed.future().get(5,TimeUnit.SECONDS);throw new AssertionError("failure swallowed");}catch(ExecutionException expected){checks++;}
            await(()->!callbacks.isEmpty());callbacks.remove().run();ok(errors.get()==1&&success.get()==42);
            CountDownLatch started=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();
            TaskHandle running=scheduler.submit(()->{started.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){interrupted.set(true);}});
            ok(started.await(5,TimeUnit.SECONDS));TaskHandle pending=scheduler.submit(()->{throw new AssertionError("cancelled queue ran");});ok(pending.cancel());ok(running.cancel());await(interrupted::get);await(()->scheduler.activeTaskCount()==0);ok(pending.isCancelled());
            scheduler.submit(()->9,v->success.set(v),e->errors.incrementAndGet()).future().get(5,TimeUnit.SECONDS);await(()->!callbacks.isEmpty());scheduler.shutdown();callbacks.remove().run();ok(success.get()==42);ok(scheduler.isShutdown());
        }finally{scheduler.shutdown();}
        AsyncTaskScheduler stopping=new AsyncTaskScheduler(1,main);
        try{
            CountDownLatch started=new CountDownLatch(1);
            TaskHandle active=stopping.submit(()->{started.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException expected){Thread.currentThread().interrupt();}});
            ok(started.await(5,TimeUnit.SECONDS));TaskHandle waiting=stopping.submit(()->{throw new AssertionError("abandoned task ran");});
            stopping.shutdown();await(()->stopping.activeTaskCount()==0);ok(active.isCancelled());ok(waiting.isCancelled());ok(waiting.isDone());ok(stopping.queueSize()==0);
        }finally{stopping.shutdown();}
        CancellationToken token=new CancellationToken();token.cancel();AsyncTaskScheduler precancelled=new AsyncTaskScheduler(1,main);
        try{TaskHandle handle=precancelled.submit("cancelled",()->{throw new AssertionError("pre-cancelled task ran");},null,null,token);ok(handle.isCancelled());ok(precancelled.activeTaskCount()==0);}finally{precancelled.shutdown();}
        System.out.println("Audit111 Unicode/history/scopes/shortcut/scheduler checks passed: "+checks);
    }
}
