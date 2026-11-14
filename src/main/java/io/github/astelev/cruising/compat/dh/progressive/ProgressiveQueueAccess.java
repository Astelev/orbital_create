package io.github.astelev.cruising.compat.dh.progressive;
public interface ProgressiveQueueAccess {
    record Focus(int x,int z,int period,int viewRadius){public Focus(int x,int z,int period){this(x,z,period,Integer.MAX_VALUE);}}
    default Focus cruising$previewFocus(){return null;}
    default void cruising$markPreview(long pos,boolean needed){}
    default void cruising$previewExecute(Runnable task){task.run();}
    default int cruising$previewWaiting(){return 0;}
    boolean cruising$previewSupported();
    boolean cruising$previewAllowed(long pos);
    boolean cruising$previewNear(long pos,int radius);
}
