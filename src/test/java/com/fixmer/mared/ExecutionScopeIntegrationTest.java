package com.fixmer.mared;

import com.fixmer.genesis.technology.runtime.ExecutionScope;
import com.fixmer.genesis.technology.runtime.ExecutionScope.Kind;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionScopeIntegrationTest {
    @Test void cancellationWaitsForExecutorAcknowledgementAndRunsCallbacksOnce() {
        var scope=new ExecutionScope();var calls=new AtomicInteger();
        scope.own(Kind.EVENT,calls::incrementAndGet);
        var executor=scope.own(Kind.EXECUTOR,calls::incrementAndGet);
        scope.close();scope.close();
        assertEquals(2,calls.get());assertEquals(1,scope.snapshot().resources());
        assertEquals(1,scope.snapshot().count(Kind.EXECUTOR));
        executor.close();assertEquals(0,scope.snapshot().resources());
        var late=scope.own(Kind.TIMER,calls::incrementAndGet);
        assertFalse(late.active());assertEquals(3,calls.get());assertEquals(0,scope.snapshot().resources());
    }
    @Test void normalCompletionReleasesCapacityAndSnapshotsAreImmutable() {
        var scope=new ExecutionScope(1);var lease=scope.own(Kind.BIND,()->{});
        var snapshot=scope.snapshot();
        assertThrows(IllegalStateException.class,()->scope.own(Kind.TIMER,()->{}));
        lease.close();lease.close();scope.own(Kind.EVENT,()->{});
        assertEquals(1,snapshot.count(Kind.BIND));assertEquals(1,scope.snapshot().count(Kind.EVENT));
        assertThrows(UnsupportedOperationException.class,()->snapshot.counts().clear());
        scope.close();assertEquals(0,scope.snapshot().resources());
    }
    @Test void failingCleanupDoesNotSkipOtherResources() {
        var scope=new ExecutionScope();var calls=new AtomicInteger();
        scope.own(Kind.EVENT,()->{throw new IllegalStateException("cleanup failed");});
        scope.own(Kind.BLOCK,calls::incrementAndGet);scope.close();
        assertEquals(1,calls.get());assertEquals(0,scope.snapshot().resources());
        assertNotNull(scope.snapshot().failure());assertEquals("cleanup failed",scope.snapshot().failure().getMessage());
    }
    @Test void registrationRacingCancellationCannotEscapeItsOwner() throws Exception {
        for(int i=0;i<100;i++){
            var scope=new ExecutionScope();var calls=new AtomicInteger();var failure=new AtomicReference<Throwable>();
            var gate=new CountDownLatch(1);
            var thread=new Thread(()->{try{gate.await();scope.own(Kind.EVENT,calls::incrementAndGet);}catch(Throwable error){failure.set(error);}});
            thread.start();gate.countDown();scope.close();thread.join(5000);
            assertFalse(thread.isAlive());assertNull(failure.get());assertEquals(1,calls.get());assertEquals(0,scope.snapshot().resources());
        }
    }
    @Test void cancellationCallbackDoesNotHoldTheScopeLock() {
        var scope=new ExecutionScope();var acknowledged=new CountDownLatch(1);
        scope.own(Kind.EVENT,()->{
            var worker=new Thread(()->{scope.snapshot();acknowledged.countDown();});worker.start();
            try { if(!acknowledged.await(2,TimeUnit.SECONDS))throw new IllegalStateException("scope lock held during callback"); }
            catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
        });
        scope.close();assertNull(scope.snapshot().failure());assertEquals(0,acknowledged.getCount());
    }
}
