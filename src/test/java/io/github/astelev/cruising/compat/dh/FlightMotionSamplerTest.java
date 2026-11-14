package io.github.astelev.cruising.compat.dh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlightMotionSamplerTest {
    @Test void repeatedTicksAndSingleMillisecondCameraCorrectionDoNotFakeFastTravel(){
        var s=new FlightMotionSampler();s.sample(0,500,0,0);
        for(int t=1;t<200;t++)s.sample(0,500,0,t);
        assertEquals(2.5,s.sample(.5,500,0,200).speed(),1e-8);
    }
    @Test void sustainedFastTravelAndDirectionAreMeasured(){
        var s=new FlightMotionSampler();s.sample(0,500,0,0);
        var m=s.sample(200,500,-100,200);assertEquals(1000,m.xVelocity());assertEquals(-500,m.zVelocity());assertEquals(Math.sqrt(1250000),m.speed(),1e-8);
    }
    @Test void stoppingAndResetClearOldMotion(){
        var s=new FlightMotionSampler();s.sample(0,500,0,0);s.sample(200,500,0,200);
        assertEquals(0,s.sample(200,500,0,400).speed());s.reset();assertEquals(0,s.sample(50000,500,0,500).speed());
    }
}
