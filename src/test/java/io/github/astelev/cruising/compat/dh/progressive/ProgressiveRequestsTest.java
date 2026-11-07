package io.github.astelev.cruising.compat.dh.progressive;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgressiveRequestsTest {
    static class Backend implements ProgressiveRequests.Backend<String> {
        final List<Long> submitted=new ArrayList<>();
        final Map<Long,CompletableFuture<String>> futures=new HashMap<>();
        final Set<Long> cached=new HashSet<>();
        public boolean ready(long pos){return cached.contains(pos);}
        public CompletableFuture<String> enqueue(long pos){submitted.add(pos);return futures.computeIfAbsent(pos,k->new CompletableFuture<>());}
    }
    @Test void waitsForPublicationRatherThanGenerationCompletion() {
        var b=new Backend();var s=new ProgressiveRequests<>(b,8);
        var f=s.request(1,List.of(8L,4L,2L));
        assertEquals(List.of(8L),b.submitted);
        b.futures.get(8L).complete("pooled source");
        assertEquals(List.of(8L),b.submitted);
        s.published(8,null);s.published(4,null);s.published(2,null);
        assertEquals(List.of(8L,4L,2L,1L),b.submitted);
        assertFalse(f.isDone());b.futures.get(1L).complete("fine");assertEquals("fine",f.join());assertEquals(0,s.pending());
    }
    @Test void sharesParentsAndCancellingOneChildDoesNotCancelSiblings() {
        var b=new Backend();var s=new ProgressiveRequests<>(b,8);
        var first=s.request(1,List.of(8L,4L));var second=s.request(2,List.of(8L,4L));
        assertSame(second,s.request(2,List.of(8L,4L)));
        assertEquals(List.of(8L),b.submitted);s.cancelIf(p->p==1);
        s.published(8,null);s.published(4,null);
        assertTrue(first.isCancelled());assertEquals(List.of(8L,4L,2L),b.submitted);
        b.futures.get(2L).complete("done");assertEquals("done",second.join());
    }
    @Test void usesNativeCachedStagesAndBoundsBacklog() {
        var b=new Backend();b.cached.add(8L);var s=new ProgressiveRequests<>(b,1);
        var f=s.request(1,List.of(8L,4L));assertEquals(List.of(4L),b.submitted);
        assertFalse(s.canAccept());assertNull(s.request(2,List.of(8L)));
        s.close();assertTrue(f.isCancelled());assertEquals(0,s.pending());assertEquals(0,s.stages());
        assertTrue(s.request(3,List.of()).isCompletedExceptionally());
    }
    @Test void failedStagesCanRetryWithoutPoisoningSiblings() {
        var b=new Backend();var s=new ProgressiveRequests<>(b,8);
        var f=s.request(1,List.of(8L));b.futures.get(8L).completeExceptionally(new IllegalStateException("failed"));
        assertTrue(f.isCompletedExceptionally());b.futures.remove(8L);
        var retry=s.request(1,List.of(8L));assertEquals(List.of(8L,8L),b.submitted);
        s.published(8,null);b.futures.get(1L).complete("ok");assertEquals("ok",retry.join());
    }
    @Test void alreadyPublishedPreviewIsReused() {
        var b=new Backend();var s=new ProgressiveRequests<>(b,8);
        s.published(8,null);s.request(1,List.of(8L));assertEquals(List.of(1L),b.submitted);
    }
    @Test void orphanParentsAreCancelledOnlyAfterTheirLastConsumerLeaves() {
        var cancelled=new ArrayList<Long>();var b=new Backend(){@Override public void cancelQueued(long pos){cancelled.add(pos);}};
        var s=new ProgressiveRequests<>(b,8);var first=s.request(1,List.of(8L,4L));var second=s.request(2,List.of(8L,4L));
        s.cancelIf(p->p==1);assertFalse(cancelled.contains(8L));assertTrue(s.needs(8));assertFalse(s.needs(1));
        s.cancelIf(p->p==2);assertTrue(first.isCancelled());assertTrue(second.isCancelled());
        assertTrue(cancelled.contains(8L));assertFalse(s.needs(8));assertEquals(0,s.stages());assertEquals(0,s.pending());
    }
    @Test void cancellingFineWorkDoesNotForgetPublishedCoverage() {
        var b=new Backend();var s=new ProgressiveRequests<>(b,8);s.request(1,List.of(8L));
        b.futures.get(8L).complete("coarse");s.published(8,null);s.cancelIf(p->p==1);
        s.request(2,List.of(8L));assertEquals(List.of(8L,1L,2L),b.submitted);
    }

}
