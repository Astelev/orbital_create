package io.github.astelev.cruising.compat.dh.progressive;

/** Coarse surface tiers within the preview band precede full chunk work; preserve native rank within a tier. */
public final class ProgressivePriority {
    private static final int STRIDE=1<<28;
    public static int score(int nativeRank,int dataDetail,int initialDetail,boolean near) {
        int tier=near && dataDetail>0?Math.max(0,initialDetail-dataDetail):initialDetail;
        return Math.min(6,tier)*STRIDE+Math.min(STRIDE-1,Math.max(0,nativeRank));
    }
    public static long distance(int x,int z,int targetX,int targetZ,int width) {
        long dx=Math.abs((long)x-targetX);if(width>0){dx%=width;dx=Math.min(dx,width-dx);}
        return Math.max(dx,Math.abs((long)z-targetZ));
    }
    public static long sectionDistance(int centerX,int centerZ,int targetX,int targetZ,int worldWidth,int sectionWidth) {
        return Math.max(0,distance(centerX,centerZ,targetX,targetZ,worldWidth)-sectionWidth/2L);
    }
    public static int initialDetail(int configured,int period){return period<=0?configured:Math.min(configured,Math.max(0,31-Integer.numberOfLeadingZeros(period/2)-6));}
    private ProgressivePriority(){}
}
