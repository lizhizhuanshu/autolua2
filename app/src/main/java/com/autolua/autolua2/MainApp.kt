package com.autolua.autolua2

import com.autolua.autolua2.extension.UserInterface
import com.autolua.autolua2.extension.imp.UserInterfaceImp
import com.autolua.autolua2.mln.ActivityLifecycleMonitor
import com.autolua.autolua2.mln.AutoLuaEngineResultCode
import com.autolua.autolua2.mln.AutoLuaEngineState
import com.autolua.autolua2.mln.GlobalStateListener
import com.autolua.autolua2.project.ProjectManagerImp
import com.autolua.autolua2.mln.provider.GlideImageProvider
import com.autolua.autolua2.mln.ud.UI
import com.autolua.autolua2.view.FloatController
import com.autolua.autolua2.view.imp.FloatControllerImp
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineProxy
import com.autolua.engine.core.root.Client
import com.immomo.mls.MLSBuilder
import com.immomo.mls.MLSEngine
import com.immomo.mls.`fun`.lt.SIApplication
import com.immomo.mls.global.LVConfigBuilder

class MainApp: android.app.Application() {
  private val TAG = "MainApp"
  private var sdCardDir:String = ""
    get() = field

  private fun ensureDir(dir: String) {
    val file = java.io.File(dir)
    if (!file.exists()) {
      file.mkdirs()
    }
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    sdCardDir = getRootPath()
    if(!sdCardDir.endsWith("/")){
      sdCardDir += "/"
    }
    val uiRootDir = sdCardDir + "ui/"

    ProjectManagerImp.instance.init(this.applicationContext, sdCardDir + "project")

    /// -----------配合 Application 使用------------
    SIApplication.isColdBoot = true
    registerActivityLifecycleCallbacks(ActivityLifecycleMonitor())

    ensureDir(uiRootDir+"cache")

    val config = LVConfigBuilder(this).
            setRootDir(uiRootDir).
            setCacheDir(uiRootDir + "cache").
            setImageDir(uiRootDir + "image").
            setGlobalResourceDir(uiRootDir + "res").
            build()
    MLSEngine.init(this, true).
            setLVConfig(config).
            setImageProvider(GlideImageProvider()).
            setGlobalStateListener(GlobalStateListener()).
            setDefaultLazyLoadImage(false).
            registerSC().
            registerUD().
            registerSingleInsance(
              MLSBuilder.SIHolder(UI.LUA_CLASS_NAME,UI::class.java)
//              MLSBuilder.SIHolder(UIDebuggerUD.LUA_CLASS_NAME,UIDebuggerUD::class.java)
            ).
            registerConstants(
              AutoLuaEngineState::class.java,
              AutoLuaEngineResultCode::class.java
            ).
            buildWhenReady();
    UserInterfaceImp.instance.init(this)
    FloatControllerImp.instance.init(this, 50)
    FloatControllerImp.instance.setClickListener {
      if(it == FloatController.State.IDLE){
        AutoLuaEngineProxy.instance.startTask()
      }else{
        AutoLuaEngineProxy.instance.interrupt()
      }
    }

    AutoLuaEngineProxy.instance.addObserver(AutoLuaEngine.Target.WORKER.value) {
      FloatControllerImp.instance.updateState(
        if(it == AutoLuaEngine.State.IDLE) FloatController.State.IDLE else FloatController.State.RUNNING)
    }

    AutoLuaEngineProxy.instance.setEngineCreator {
      val builder = Client.Builder(this@MainApp)
      builder.addLocalService("UI", UserInterfaceImp.instance, UserInterface::class.java)
      builder.addLocalService("FloatController", FloatControllerImp.instance, FloatController::class.java)
      builder.build()!!
    }
    AutoLuaEngineProxy.instance.start()

    startService(android.content.Intent(this,
      MainService::class.java))
  }

  private fun getRootPath(): String {
    return getExternalFilesDir(null)?.absolutePath!!
  }

  companion object {
    private lateinit var instance: MainApp
    fun getInstance(): MainApp {
      return instance
    }

    fun getPackageNameImpl(): String {
      var sPackageName = instance.packageName
      if (sPackageName.contains(":")) {
        sPackageName = sPackageName.substring(0, sPackageName.lastIndexOf(":"))
      }
      return sPackageName
    }
  }
}