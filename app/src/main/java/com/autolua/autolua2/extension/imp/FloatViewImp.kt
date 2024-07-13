package com.autolua.autolua2.extension.imp

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import android.widget.FrameLayout
import com.autolua.autolua2.base.Path
import com.autolua.autolua2.extension.FloatView
import com.immomo.mls.Constants
import com.immomo.mls.InitData
import com.immomo.mls.MLSInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FloatViewImp(private val windowManager:WindowManager,
                   private val context: Context,
  private val layoutParams:WindowManager.LayoutParams):FloatView
{
  @Volatile
  private var showing:Boolean = false
  private lateinit var mln:MLSInstance
  private lateinit var view: FrameLayout

  private fun decodeUrl(url:String):InitData{
    val nUrl = Path.ui(url)
    val initData = InitData(nUrl)
    if(nUrl.startsWith("http://127.0.0.1")){
      initData.loadType = Constants.LT_FORCE_DOWNLOAD
    }
    return initData
  }

  fun init(url:String):Boolean{
    val r = CoroutineScope(Dispatchers.Main).async {
      mln = MLSInstance(context,false,false)
      view = FrameLayout(context)
      val initData = decodeUrl(url)
      mln.setContainer(view)
      mln.setData(initData)
      if(!mln.isValid){
        return@async false
      }
      if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      }else{
        layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE
      }
      layoutParams.format = PixelFormat.RGBA_8888
      layoutParams.flags = layoutParams.flags or  WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      return@async true
    }
    return runBlocking {
      r.await()
    }
  }

  override fun show() {
    CoroutineScope(Dispatchers.Main).launch {
      if (!showing) {
        windowManager.addView(view, layoutParams)
        showing = true
        mln.onResume()
      }
    }
  }

  override fun hide() {
    CoroutineScope(Dispatchers.Main).launch {
      if (showing) {
        windowManager.removeView(view)
        showing = false
        mln.onPause()
      }
    }
  }

  override fun setPosition(x: Int, y: Int) {
    CoroutineScope(Dispatchers.Main).launch {
      layoutParams.x = x
      layoutParams.y = y
      if (showing) {
        windowManager.updateViewLayout(view, layoutParams)
      }
    }
  }

  override fun setSize(width: Int, height: Int) {
    CoroutineScope(Dispatchers.Main).launch {
      layoutParams.width = width
      layoutParams.height = height
      if (showing) {
        windowManager.updateViewLayout(view, layoutParams)
      }
    }
  }

  override fun setContent(url: String) {
    CoroutineScope(Dispatchers.Main).launch {
      val initData = decodeUrl(url)
      mln.setData(initData)
    }
  }

  override fun isShowing(): Boolean {
    return showing
  }

  override fun onRelease() {
    CoroutineScope(Dispatchers.Main).launch {
      if (showing) {
        windowManager.removeView(view)
      }
      mln.onDestroy()
    }
  }

  override fun setAlpha(alpha: Float) {
    CoroutineScope(Dispatchers.Main).launch {
      layoutParams.alpha = alpha
      if (showing) {
        windowManager.updateViewLayout(view, layoutParams)
      }
    }
  }
}