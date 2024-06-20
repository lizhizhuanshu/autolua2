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

class UserInterfaceImp(): UserInterface {
  private val floatViews:LongSparseArray<FloatView> = LongSparseArray()
  private var floatViewId:Long = 0
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

  private fun newID():Long{
    while (true){
      floatViewId++
      if( floatViewId != 0L && floatViews[floatViewId] == null){
        return floatViewId
      }
    }
  }

  override fun createFloatView(uri: String, params:WindowManager.LayoutParams): Long {
    val nUri = Path.ui(uri)
    val deferred = CoroutineScope(Dispatchers.Main).async {
      val instance = MLSInstance(context,false,false)
      val layout = FrameLayout(context)
      val initData = InitData(nUri)
      if(nUri.startsWith("http://127.0.0.1")){
        initData.loadType = Constants.LT_FORCE_DOWNLOAD
      }
      instance.setContainer(layout)
      instance.setData(initData)
      Log.d("UserInterfaceImp","createFloatView uri:$nUri")
      if(!instance.isValid)
        return@async 0L

      if(Build.VERSION.SDK_INT >=Build.VERSION_CODES.O){
        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      }else{
        params.type = WindowManager.LayoutParams.TYPE_PHONE
      }
      params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      params.format = PixelFormat.RGBA_8888
      val id = newID()
      val floatView = FloatView(id,nUri,instance,layout, params)
      floatViews.put(id,floatView)
      id
    }
    val r = runBlocking {
      deferred.await()
    }
    Log.d("UserInterfaceImp","createFloatView id:$r")
    return r
  }

  override fun releaseFloatView(id: Long) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      if(floatView.showing){
        windowManager.removeView(floatView.layout)
      }
      floatView.instance.onDestroy()
      floatViews.remove(id)
    }
  }

  override fun showFloatView(id: Long) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      if(floatView.showing){
        return@launch
      }

      Log.d("UserInterfaceImp","showFloatView id:$id")
      windowManager.addView(floatView.layout,floatView.params)
      floatView.showing = true
    }
  }

  override fun hideFloatView(id: Long) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      if(!floatView.showing){
        return@launch
      }
      windowManager.removeView(floatView.layout)
      floatView.showing = false
    }
  }

  override fun destroyFloatView(id: Long) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      if(floatView.showing){
        windowManager.removeView(floatView.layout)
      }
      floatView.instance.onDestroy()
      floatViews.remove(id)
    }
  }

  override fun setFloatViewPosition(id: Long, x: Int, y: Int) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      floatView.params.x = x
      floatView.params.y = y
      if(floatView.showing){
        windowManager.updateViewLayout(floatView.layout,floatView.params)
      }
    }
  }

  override fun setFloatViewSize(id: Long, width: Int, height: Int) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      floatView.params.width = width
      floatView.params.height = height
      if(floatView.showing){
        windowManager.updateViewLayout(floatView.layout,floatView.params)
      }
    }
  }

  private fun <T : Any> updateObjectFromJson(obj: T, json: String) {
    val gson = Gson()
    val jsonObject = JsonParser.parseString(json).asJsonObject
    val kClass = obj::class
    for (property in kClass.declaredMemberProperties) {
      property.isAccessible = true
      val jsonElement = jsonObject.get(property.name)
      if (jsonElement != null && jsonElement != JsonNull.INSTANCE) {
        if(property is KMutableProperty<*>){
          property.setter.call(obj,gson.fromJson(jsonElement,property.returnType.javaType))
        }
      }
    }
  }

  override fun setFloatViewContent(id: Long, uri: String) {
    val nUri = Path.ui(uri)
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      floatView.instance.setData(InitData(nUri))
    }
  }

  override fun changeFloatViewParams(id: Long, params: String) {
    CoroutineScope(Dispatchers.Main).launch {
      val floatView = floatViews[id]?:return@launch
      updateObjectFromJson(floatView.params,params)
      if(floatView.showing){
        windowManager.updateViewLayout(floatView.layout,floatView.params)
      }
    }
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
    CoroutineScope(Dispatchers.Main).launch {
      for (i in 0 until floatViews.size()){
        val floatView = floatViews.valueAt(i)
        if(floatView.showing){
          windowManager.removeView(floatView.layout)
        }
        floatView.instance.onDestroy()
      }
      floatViews.clear()
    }
  }

  data class FloatView(val id:Long,
                       val uri:String,
                       val instance:MLSInstance,
                        val layout:FrameLayout,
                       val params:WindowManager.LayoutParams){
    var showing = false
  }

}