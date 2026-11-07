package io.github.astelev.cruising.compat.dh.progressive;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProgressivePriorityTest {
    @Test void coarseTiersWinAndPreserveNativeForwardPriorityWithinTier(){
        int previous=-1;
        for(int d=3;d>=0;d--){int low=ProgressivePriority.score(0,d,3,true),high=ProgressivePriority.score(Integer.MAX_VALUE,d,3,true);assertTrue(low>previous);assertTrue(high>=low);previous=high;}
        assertTrue(ProgressivePriority.score(50,2,3,true)<ProgressivePriority.score(100,2,3,true));
        assertTrue(ProgressivePriority.score(0,0,4,true)>=0);
    }
    @Test void ancestorCoveringThePlayerRemainsNearEvenWhenItsCenterIsFarAway(){
        assertEquals(0,ProgressivePriority.sectionDistance(512,512,1,1,0,1024));
        assertEquals(488,ProgressivePriority.sectionDistance(512,512,1512,512,0,1024));
        assertEquals(1,ProgressivePriority.sectionDistance(-65280,256,65535,0,131072,512));
    }
    @Test void periodicDistanceIncludesOppositeBoundaryAndHandlesOverflow(){
        assertEquals(2,ProgressivePriority.distance(-65535,10,65535,10,131072));
        assertEquals(131070,ProgressivePriority.distance(-65535,10,65535,10,0));
        assertEquals(4294967295L,ProgressivePriority.distance(0,Integer.MIN_VALUE,0,Integer.MAX_VALUE,131072));
    }
}
