package com.autolua.autolua2.extension

import android.view.WindowManager
import com.autolua.engine.base.Releasable
interface UserInterface:Releasable{
  fun showToast(message: String, duration: Int)
  fun createView(uri: String, params: WindowManager.LayoutParams):FloatView?
  fun putSignal(signal:String, data:Any?)
  fun takeSignal(signal:String,timeout:Long):Any?
}