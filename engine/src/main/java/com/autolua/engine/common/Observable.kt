package com.autolua.engine.common

interface Observable<T> {
  fun addObserver(observer: Observer<T>)
  fun removeObserver(observer: Observer<T>)
  fun addObserver(observer: (T,flags:Int) -> Unit)
  fun removeObserver(observer: (T,flags:Int) -> Unit)
  fun addObserver(observer: (T) -> Unit)
  fun removeObserver(observer: (T) -> Unit)
  fun addObserver(flags:Int,observer: (T) -> Unit)


  fun notifyObservers(data: T,flags: Int = 0)
  val listener:((T) -> Unit)
}

