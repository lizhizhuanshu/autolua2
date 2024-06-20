package com.autolua.autolua2.mln;
import com.immomo.mls.wrapper.Constant;
import com.immomo.mls.wrapper.ConstantClass;

@ConstantClass(alias = "ALEState")
public interface AutoLuaEngineState {
    @Constant
    int IDLE = 0;
    @Constant
    int STARTING = 1;
    @Constant
    int RUNNING = 2;
    @Constant
    int STOPPED = 3;
    @Constant
    int PAUSING = 4;
    @Constant
    int PAUSED = 5;
}

