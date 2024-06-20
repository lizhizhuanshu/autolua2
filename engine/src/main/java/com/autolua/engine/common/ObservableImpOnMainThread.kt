package com.autolua.engine.common

class ObservableImpOnMainThread<T>: ObservableImp<T>() {
  private val handler = android.os.Handler(android.os.Looper.getMainLooper()){
    super.notifyObservers(it.obj as T,it.what)
    true
  }

  override fun notifyObservers(data: T, flags: Int) {
    handler.obtainMessage(flags,data).sendToTarget()
  }
}