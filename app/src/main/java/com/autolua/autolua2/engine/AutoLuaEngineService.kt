package com.autolua.autolua2.engine

import com.autolua.engine.common.Observable

interface AutoLuaEngineService: Observable<AutoLuaEngineService.State> {
  fun getState(): State
  fun start()
  fun stop()
  fun runTask(rootDir:String)

  fun startDebugService(port:Int,ip:String?,auth:String?)
  fun stopDebugService()
  fun getDebugServiceState(): State

  enum class State{
    IDLE,
    STARTING,
    RUNNING,
    STOPPING
  }

}