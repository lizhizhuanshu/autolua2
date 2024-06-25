package com.autolua.autolua2.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.autolua.autolua2.R
import com.autolua.autolua2.databinding.ActivityProjectBinding
import com.autolua.autolua2.view.imp.FloatControllerImp
import com.autolua.engine.core.AutoLuaEngineProxy
import com.immomo.luanative.hotreload.HotReloadServer
import com.immomo.mls.MLSBundleUtils
import com.immomo.mls.MLSInstance

class ProjectActivity: AppCompatActivity(){
  private lateinit var binding: ActivityProjectBinding
  private lateinit var instance: MLSInstance;

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityProjectBinding.inflate(layoutInflater)
    setContentView(binding.root)
    val root = binding.root
    val frameLayout = root.findViewById<FrameLayout>(R.id.project_ui)
    val initData = MLSBundleUtils.parseFromBundle(intent.extras)!!.showLoadingView(true)

    instance = MLSInstance(this,false,false)
    instance.setContainer(frameLayout)
    instance.setData(initData)
    val mainRootDir = intent.getStringExtra("LUA_MAIN_DIR")
    binding.startProject.setOnClickListener {
      AutoLuaEngineProxy.instance.setRootDir(mainRootDir!!)
      FloatControllerImp.instance.reShow()
      finish()
    }
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