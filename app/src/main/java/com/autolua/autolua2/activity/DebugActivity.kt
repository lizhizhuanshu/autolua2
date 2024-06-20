package com.autolua.autolua2.activity

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.immomo.luanative.hotreload.HotReloadServer
import com.immomo.mls.Constants
import com.immomo.mls.HotReloadHelper
import com.immomo.mls.MLSBundleUtils
import com.immomo.mls.MLSInstance

class DebugActivity : AppCompatActivity() {
  private lateinit var instance: MLSInstance
  private lateinit var receiver: BroadcastReceiver

  companion object{
    const val STARTED_BROADCAST = "com.immomo.mls.DebugActivity.STARTED_BROADCAST"
    const val CLOSE_BROADCAST = "com.immomo.mls.DebugActivity.CLOSE_BROADCAST"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val frameLayout = FrameLayout(this)
    setContentView(frameLayout,
      ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    val initData = MLSBundleUtils.parseFromBundle(intent.extras)!!.showLoadingView(true)
    initData.loadType = Constants.LT_FORCE_DOWNLOAD
    instance = MLSInstance(this,true,true)
    instance.setContainer(frameLayout)
    instance.setData(initData)
    receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        finish()
      }
    }
    val broadcastManager = LocalBroadcastManager.getInstance(this)
    broadcastManager.registerReceiver(receiver, IntentFilter(CLOSE_BROADCAST))
    broadcastManager.sendBroadcast(Intent(STARTED_BROADCAST))
    HotReloadHelper.setUseWifi("localhost",8176)
  }


  @SuppressLint("MissingSuperCall")
  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    LocalBroadcastManager.getInstance(this).
      sendBroadcast(Intent(STARTED_BROADCAST))
    HotReloadHelper.setUseWifi("localhost",8176)
  }

  override fun onResume() {
    super.onResume()
    instance.onResume()
  }

  override fun onPause() {
    super.onPause()
    instance.onPause()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_BACK) {
      if (event.action != KeyEvent.ACTION_UP) instance.dispatchKeyEvent(event)
      if (!instance.backKeyEnabled) return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onDestroy() {
    super.onDestroy()
    instance.onDestroy()
    HotReloadServer.getInstance().stop()
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (instance.onActivityResult(requestCode, resultCode, data)) return
    super.onActivityResult(requestCode, resultCode, data)
  }


}