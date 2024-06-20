package com.autolua.engine.common

open class Listenable<T> {
  @Volatile
  private var listener:Listener<T>? = null
  fun setListener(listener:Listener<T>?){
    this.listener = listener
  }
  fun notify(data:T){
    listener?.invoke(data)
  }
}

typealias Listener<T> = (T) -> Unit