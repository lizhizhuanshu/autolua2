package com.autolua.autolua2.extension

import android.view.WindowManager
import com.autolua.engine.base.Releasable

interface UserInterface:Releasable{
  fun showToast(message: String, duration: Int)
  fun createFloatView(uri: String, params: WindowManager.LayoutParams):Long
  fun releaseFloatView(id:Long)
  fun showFloatView(id:Long)
  fun hideFloatView(id:Long)
  fun destroyFloatView(id:Long)
  fun setFloatViewPosition(id:Long,x:Int,y:Int)
  fun setFloatViewSize(id:Long,width:Int,height:Int)
  fun setFloatViewContent(id:Long, uri:String)
  fun changeFloatViewParams(id:Long,params:String)
  fun putSignal(signal:String, data:Any?)
  fun takeSignal(signal:String,timeout:Long):Any?
}