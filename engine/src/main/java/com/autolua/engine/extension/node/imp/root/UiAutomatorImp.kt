package com.autolua.engine.extension.node.imp.root

import android.app.UiAutomation
import android.os.Bundle
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.autolua.engine.extension.node.UiAutomator
import com.autolua.engine.extension.node.UiObject
import java.util.concurrent.atomic.AtomicBoolean

class UiAutomatorImp:UiAutomator {
  private val thread = HandlerThread("UiAutomator")
  private var uiAutomation:UiAutomation? = null
  private val inited = AtomicBoolean(false)
  override fun init():Boolean {
    if (inited.compareAndSet(false, true)) {
      thread.start()
      try {
        val clazz = Class.forName("android.app.UiAutomationConnection")
        val connection = clazz.newInstance()
        val constructor =
          UiAutomation::class.java.getConstructor(
            Looper::class.java,
            Class.forName("android.app.IUiAutomationConnection")
          )
        for (i in 0 until MAX_CONNECT_SUM) {
          uiAutomation = constructor.newInstance(thread.looper, connection)
          UiAutomation::class.java.getMethod("connect", Int::class.javaPrimitiveType)
            .invoke(uiAutomation, 1)
          val r = uiAutomation!!.rootInActiveWindow
          if (r != null) {
            System.err.println("UiAutomation " + (i + 1) + " connect right")
            return true
          } else UiAutomation::class.java.getMethod("disconnect").invoke(uiAutomation)
        }
      } catch (e: Exception) {
        e.printStackTrace(System.err)
      }
      return false
    }
    return true
  }

  private fun ensureInit(){
    if(!inited.get()){
      val r = init()
      if(!r){
        throw RuntimeException("UiAutomator init failed")
      }
    }
  }

  override fun getRootInActiveWindow(): UiObject {
    ensureInit()
    var r: AccessibilityNodeInfo?
    while (true) {
      r = uiAutomation!!.rootInActiveWindow
      if (r != null) break
      SystemClock.sleep(60)
    }
    return UiObject(r!!)
  }

  override fun setText(text: String): Boolean {
    ensureInit()
    val obj = uiAutomation!!.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
    val b = Bundle()
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    return obj.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
  }

  override fun destroy() {
    if (inited.compareAndSet(true, false)) {
      UiAutomation::class.java.getMethod("disconnect").invoke(uiAutomation)
      thread.quit()
    }
  }

  companion object{
    private const val MAX_CONNECT_SUM = 100
  }
}