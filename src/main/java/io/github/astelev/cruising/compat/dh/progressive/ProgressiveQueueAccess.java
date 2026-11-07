package io.github.astelev.cruising.compat.dh.progressive;
public interface ProgressiveQueueAccess {
    boolean cruising$previewSupported();
    boolean cruising$previewAllowed(long pos);
    boolean cruising$previewNear(long pos,int radius);
}
