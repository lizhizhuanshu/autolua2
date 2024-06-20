package com.autolua.engine.core.composite

import com.autolua.engine.core.AutoLuaEngine
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class MessageObserver: AutoLuaEngine.MessageObserver{
  private val observers:MutableList<AutoLuaEngine.MessageObserver> = mutableListOf()
  private val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()

  fun attach(observer: AutoLuaEngine.MessageObserver) {
    lock.write{
      observers.add(observer)
    }
  }

  fun detach(observer: AutoLuaEngine.MessageObserver) {
    lock.write{
      observers.remove(observer)
    }
  }

  override fun onWarning(message: String) {
    lock.read {
      observers.forEach {
        it.onWarning(message)
      }
    }
  }

  override fun onError(message: String) {
    lock.read {
      observers.forEach {
        it.onError(message)
      }
    }
  }
}