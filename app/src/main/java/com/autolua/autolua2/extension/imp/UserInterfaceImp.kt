package com.autolua.autolua2.extension.imp


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.util.LongSparseArray
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.autolua.autolua2.base.Configure
import com.autolua.autolua2.base.Path
import com.autolua.autolua2.extension.FloatView
import com.autolua.autolua2.extension.UserInterface
import com.google.gson.Gson
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.immomo.mls.Constants
import com.immomo.mls.InitData
import com.immomo.mls.MLSInstance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

class UserInterfaceImp (): UserInterface {
  private lateinit var windowManager:WindowManager
  private lateinit var context: Context
  @SuppressLint("StaticFieldLeak")
  companion object{
    val instance:UserInterfaceImp by lazy { UserInterfaceImp() }
  }

  fun init(context: Context) {
    this.context = context
    windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  }


  override fun showToast(message: String, duration: Int) {
    CoroutineScope(Dispatchers.Main).launch {
      Toast.makeText(context, message, duration).show()
    }
  }


  override fun createView(uri: String, params:WindowManager.LayoutParams): FloatView? {
    val floatView = FloatViewImp(windowManager,context,params)
    if(!floatView.init(uri)){
      return null
    }
    return floatView
  }

  private val signals = ConcurrentHashMap <String,LinkedBlockingQueue<Any>>()

  private val lock = ReentrantReadWriteLock()

  private fun getOrPutSignalQueue(signal: String):LinkedBlockingQueue<Any>{
    var queue:LinkedBlockingQueue<Any>?
    lock.read{
      queue = signals[signal]
    }
    if(queue != null){
      return queue!!
    }
    val newQueue = LinkedBlockingQueue<Any>()
    lock.write{
      if(signals[signal] != null){
        lock.writeLock().unlock()
        return signals[signal]!!
      }
      signals[signal] = newQueue
    }
    return newQueue
  }

  override fun putSignal(signal: String, data: Any?) {
    val queue = getOrPutSignalQueue(signal)
    queue.put(data)
  }

  override fun takeSignal(signal: String, timeout: Long): Any? {
    val queue = getOrPutSignalQueue(signal)
    if(timeout>=0){
      return queue.poll(timeout,java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    return queue.take()
  }

  override fun onRelease() {
    signals.clear()
  }


}