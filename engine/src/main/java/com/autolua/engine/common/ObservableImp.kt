package com.autolua.engine.common

import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

open class ObservableImp<T>: Observable<T> {
  private val observers =  CopyOnWriteArraySet<Observer<T>>()

  override fun addObserver(observer: Observer<T>) {
    observers.add(observer)
  }

  override fun removeObserver(observer: Observer<T>) {
    observers.remove(observer)
  }

  private class MyObserver<T>(val callback: (T,flags:Int) -> Unit): Observer<T> {
    override fun onUpdate(data: T, flags: Int) {
      callback(data,flags)
    }
  }

  private class MyObserverTwo<T>(val callback: (T) -> Unit,val flags:Int?=null): Observer<T> {
    override fun onUpdate(data: T, flags: Int) {
      if(this.flags == null || this.flags == flags)
        callback(data)
    }
  }

  override fun addObserver(observer: (T,Int) -> Unit) {
    addObserver(MyObserver(observer))
  }

  private lateinit var observerMap: MutableMap<Any,Observer<T>>
  init{
    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.N){
      observerMap = ConcurrentHashMap()
    }
  }

  override fun removeObserver(observer: (T, Int) -> Unit) {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      observers.removeIf {
        it is MyObserver<T> && it.callback == observer
      }
    }else{
      val aObserver = observerMap.remove(observer)
      if(aObserver != null){
        observers.remove(aObserver)
      }
    }
  }

  override fun addObserver(observer: (T) -> Unit) {
    addObserver(MyObserverTwo(observer))
  }

  override fun removeObserver(observer: (T) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      observers.removeIf {
        it is MyObserverTwo<T> && it.callback == observer
      }
    }else{
      val aObserver = observerMap.remove(observer)
      if(aObserver != null){
        observers.remove(aObserver)
      }
    }
  }

  override fun addObserver(flags: Int, observer: (T) -> Unit) {
    addObserver(MyObserverTwo(observer,flags))
  }

  override val listener:((T) -> Unit) by lazy {
    {
      notifyObservers(it)
    }
  }

  override fun notifyObservers(data: T, flags: Int) {
    observers.forEach {
      it.onUpdate(data,flags)
    }
  }



  fun clearObservers() {
    observers.clear()
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      observerMap.clear()
    }
  }
}