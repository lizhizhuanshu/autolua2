package com.autolua.autolua2.debug

import com.autolua.engine.common.Observable
import io.netty.buffer.ByteBuf

interface DebugServer : Observable<DebugServer.State> {
  fun getState():State
  fun start(port:Int,auth:String, callback: (success:Boolean) -> Unit)
  fun stop()


  fun setUIDebugKitListener(listener:(connection:Boolean) -> Unit)
  fun startUIDebug()

  fun sendMessageToUIDebugKit(type:Int, data:ByteArray)
  fun setUIDebugKitMessageHandler(handler: (type:Int, data:ByteArray) -> Unit)
  fun requestUICode(path:String): ByteArray?


  fun setPostProjectHandler(callback: (project:String, data:ByteBuf) -> Unit)



  enum class State{
    IDLE,
    STARTING,
    RUNNING,
    STOPPING
  }
}