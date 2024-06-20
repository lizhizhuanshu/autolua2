package com.autolua.autolua2.debug

import com.autolua.engine.common.Observable


interface DebugService: Observable<DebugService.State> {

  fun set(port:Int,auth:String,startBroadcast:Boolean)
  fun start()
  fun stop()
  fun getState(): State

  enum class State{
    IDLE,
    STARTING,
    RUNNING,
    STOPPING
  }
}