package com.autolua.autolua2.mln;


import com.autolua.engine.core.AutoLuaEngine;
import com.immomo.mls.wrapper.Constant;
import com.immomo.mls.wrapper.ConstantClass;

@ConstantClass(alias = "ALEResult")
public interface AutoLuaEngineResultCode {
    @Constant
    int OK = AutoLuaEngine.ResultCode.SUCCESS.getValue();
    @Constant
    int SU_FAIL = AutoLuaEngine.ResultCode.SU_FAIL.getValue();
    @Constant
    int RUNNING = AutoLuaEngine.ResultCode.RUNNING.getValue();
    @Constant
    int OTHER_ERROR = AutoLuaEngine.ResultCode.OTHER_ERROR.getValue();
}
