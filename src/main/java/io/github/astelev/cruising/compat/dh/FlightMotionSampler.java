package io.github.astelev.cruising.compat.dh;

/** Wall-time window avoids false speed spikes when several client ticks share one rendered camera pose. */
public final class FlightMotionSampler {
    public record Motion(double speed,double xVelocity,double zVelocity){}
    private static final Motion STILL=new Motion(0,0,0);
    private long time;private double x,y,z;private boolean initialized;private Motion last=STILL;
    public void reset(){initialized=false;last=STILL;}
    public Motion sample(double nextX,double nextY,double nextZ,long now){
        if(!initialized || now<time){initialized=true;time=now;x=nextX;y=nextY;z=nextZ;return last=STILL;}
        long elapsed=now-time;if(elapsed<200)return last;
        double scale=1000.0/elapsed,dx=nextX-x,dy=nextY-y,dz=nextZ-z;
        last=new Motion(Math.sqrt(dx*dx+dy*dy+dz*dz)*scale,dx*scale,dz*scale);
        time=now;x=nextX;y=nextY;z=nextZ;return last;
    }
}
