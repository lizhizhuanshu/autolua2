package com.autolua.engine.core

import android.util.Log
import com.autolua.engine.base.LuaContext
import com.autolua.engine.common.Observable
import com.autolua.engine.common.ObservableImpOnMainThread
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class AutoLuaEngineProxy:AutoLuaEngine, Observable<AutoLuaEngine.State> by ObservableImpOnMainThread() {
  @Volatile
  private var engine:AutoLuaEngine? = null
  @Volatile
  private var engineCreator:(()->AutoLuaEngine)? = null

  private val executor = Executors.newSingleThreadExecutor()
  private val customDispatcher = executor.asCoroutineDispatcher()
  private val customScope = CoroutineScope(customDispatcher)

  companion object {
    val instance by lazy { AutoLuaEngineProxy() }
  }

  override fun setRootDir(rootDir: String) {
    customScope.launch {
      ensureEngine().setRootDir(rootDir)
    }
  }

  fun startTask(){
    customScope.launch {
      val script = """
        loadfile("init.lua")()
        loadfile('main.lua')()
      """.trimIndent()
      ensureEngine().execute(script)
    }
  }


  fun setEngineCreator(creator:()->AutoLuaEngine){
    engineCreator = creator
  }

  private fun ensureEngine(): AutoLuaEngine {
    if(engine == null){
      engine = engineCreator?.invoke()
      engine?.addObserver(listener)
    }
    if(engine == null){
      throw IllegalStateException("engine is null")
    }
    return engine!!
  }



  override fun start(callback: Callback?) {
    customScope.launch {
      val engine = ensureEngine()
      engine.start(callback)
    }
  }

  override fun stop() {
    customScope.launch {
      engine?.stop()
    }
  }

  override fun getState(target: AutoLuaEngine.Target): AutoLuaEngine.State {
    return engine?.getState(target) ?: AutoLuaEngine.State.IDLE
  }

  override fun destroy() {
    customScope.launch {
      engine?.destroy()
      engine = null
    }
  }


  override fun execute(script: String, flags: Int): Int {
    customScope.launch {
      ensureEngine().execute(script, flags)
    }
    return 0
  }

  override fun execute(script: ByteArray, codeMode: LuaContext.CodeMode, flags: Int): Int {
    customScope.launch {
      ensureEngine().execute(script, codeMode, flags)
    }
    return 0
  }

  override fun interrupt() {
    customScope.launch {
      ensureEngine().interrupt()
    }
  }

  override fun startDebugger(debuggerConfigure: AutoLuaEngine.DebuggerConfigure) {
    customScope.launch {
      val engine = ensureEngine()
      if(engine.getState() == AutoLuaEngine.State.IDLE){
        engine.start()
      }
      engine.startDebugger(debuggerConfigure)
    }
  }

  override fun stopDebugger() {
    customScope.launch {
      ensureEngine().stopDebugger()
    }
  }

}