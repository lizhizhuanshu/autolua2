package com.autolua.autolua2.view.imp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import com.autolua.autolua2.R
import com.autolua.autolua2.view.FloatController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@SuppressLint("ClickableViewAccessibility")
class FloatControllerImp private constructor() : BroadcastReceiver(),FloatController{
  private lateinit var windowManager:WindowManager
  private lateinit var imgButton:ImageButton
  @Volatile
  private var listener: ((state: FloatController.State) -> Unit)? = null
  private var state = FloatController.State.IDLE
  private var displayMetrics = Resources.getSystem().displayMetrics
  @Volatile
  private var showing = false

  companion object{
    val instance by lazy {
      FloatControllerImp()
    }
    private const val TAG = "FloatControllerImp"
    private fun Int.toPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
  }

  fun init(context:Context,imageDiameter:Int){
    windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    layoutParams.width = imageDiameter.toPx()
    layoutParams.height = imageDiameter.toPx()
    imgButton = ImageButton(context)
    imgButton.scaleType = ImageView.ScaleType.CENTER_CROP;
    imgButton.setBackgroundColor(Color.TRANSPARENT);
    imgButton.setOnClickListener {
      listener?.invoke(state)
    }
    imgButton.setImageResource(R.drawable.start_engine)
    imgButton.setOnTouchListener(object :OnTouchListener{
      private var lastX:Int = 0
      private var lastY:Int = 0
      override fun onTouch(p0: View?, p1: MotionEvent): Boolean {
        when(p1.action){
          MotionEvent.ACTION_DOWN -> {
            lastX = p1.rawX.toInt()
            lastY = p1.rawY.toInt()
          }
          MotionEvent.ACTION_MOVE -> {
            val nowX = p1.rawX.toInt()
            val nowY = p1.rawY.toInt()
            val dx = nowX - lastX
            val dy = nowY - lastY
            var x = layoutParams.x + dx
            if(x <0)
              x = 0
            else {
              val maxX = displayMetrics.widthPixels - layoutParams.width
              if(x > maxX) x = maxX
            }
            lastX = nowX
            lastY = nowY
            rawMove(x,layoutParams.y + dy)
          }
          MotionEvent.ACTION_UP ->{
            if(layoutParams.x+layoutParams.width/2 < displayMetrics.widthPixels / 2){
              rawMove(0,layoutParams.y)
            }else {
              rawMove(displayMetrics.widthPixels - layoutParams.width, layoutParams.y)
            }
          }
        }
        return false
      }
    })
  }

  private val layoutParams = WindowManager.LayoutParams().apply {
    type =  if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }else{
      WindowManager.LayoutParams.TYPE_PHONE
    }
    format = PixelFormat.RGBA_8888
    gravity = Gravity.START or Gravity.TOP
    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
  }

  private fun rawMove(x:Int,y:Int){
    layoutParams.x = x
    layoutParams.y = y
    windowManager.updateViewLayout(imgButton,layoutParams)
  }


  override fun onReceive(p0: Context?, p1: Intent?) {
    displayMetrics = Resources.getSystem().displayMetrics
    if(showing){
      reShow()
    }
  }

  override fun isShowing(): Boolean {
    return showing
  }

  override fun show() {
    CoroutineScope(Dispatchers.Main).launch {
      if(!showing){
        showing =true
        windowManager.addView(imgButton,layoutParams)
      }
    }
  }

  override fun reShow() {
    CoroutineScope(Dispatchers.Main).launch {
      val x = displayMetrics.widthPixels- layoutParams.width
      val y = displayMetrics.heightPixels / 3
      layoutParams.x = x
      layoutParams.y = y
      if(showing){
        windowManager.updateViewLayout(imgButton,layoutParams)
      }else{
        showing =true
        windowManager.addView(imgButton,layoutParams)
      }
    }
  }

  override fun hide() {
    CoroutineScope(Dispatchers.Main).launch {
      if(showing){
        windowManager.removeView(imgButton)
      }
    }
  }

  override fun setPosition(x: Int, y: Int) {
    CoroutineScope(Dispatchers.Main).launch {
      layoutParams.x = x
      layoutParams.y = y
      windowManager.updateViewLayout(imgButton,layoutParams)
    }
  }

  override fun updateState(state: FloatController.State) {
    CoroutineScope(Dispatchers.Main).launch {
      if (this@FloatControllerImp.state != state){
        this@FloatControllerImp.state = state
        when(state){
          FloatController.State.IDLE -> {
            imgButton.setImageResource(R.drawable.start_engine)
          }
          FloatController.State.RUNNING -> {
            imgButton.setImageResource(R.drawable.stop_engine)
          }
        }
      }
    }
  }

  override fun setClickListener(listener: (state: FloatController.State) -> Unit) {
    this.listener = listener
  }
}