package com.autolua.autolua2.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autolua.autolua2.IInputMethodService
import com.autolua.autolua2.IMainService
import com.autolua.autolua2.R
import com.autolua.autolua2.base.Constants
import com.autolua.autolua2.extension.UserInterface
import com.autolua.autolua2.extension.imp.UserInterfaceImp
import com.autolua.autolua2.view.FloatController
import com.autolua.autolua2.view.imp.FloatControllerImp
import com.autolua.engine.common.Observable
import com.autolua.engine.common.ObservableImpOnMainThread
import com.autolua.engine.common.Observer
import com.autolua.engine.common.Utils
import com.autolua.engine.core.AutoLuaEngine

class AutoLuaEngineServiceImp : Service() {

  private lateinit var wakeLock: WakeLock
  private var engine: AutoLuaEngine? = null
  private lateinit var ui: UserInterface
  private lateinit var controller: FloatController
  @Volatile
  private var mainRoot: String? = null
  override fun onCreate() {
    super.onCreate()
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationChannel = NotificationChannel(
        Constants.FOREGROUND_SERVICE_CHANNEL_ID,
        resources.getString(R.string.main_notification_channel_name), NotificationManager.IMPORTANCE_HIGH
      )

      notificationChannel.enableLights(true)
      notificationChannel.lightColor = Color.RED
      notificationChannel.setShowBadge(true)
      notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      notificationManager.createNotificationChannel(notificationChannel)
    }

    val builder = NotificationCompat.Builder(this)
    builder.setContentTitle(resources.getString(R.string.engine_notification_title))
      .setSmallIcon(R.mipmap.ic_launcher_round)
      .setContentText(resources.getString(R.string.engine_notification_content))
      .setWhen(System.currentTimeMillis())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      builder.setChannelId(Constants.FOREGROUND_SERVICE_CHANNEL_ID)

    val notification = builder.build()
    notification.defaults = Notification.DEFAULT_SOUND
    startForeground(Constants.ENGINE_SERVICE_NOTIFICATION_ID, notification)
    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
    wakeLock = powerManager.newWakeLock(
      PowerManager.PARTIAL_WAKE_LOCK,
      this.javaClass.canonicalName
    )
    wakeLock.acquire(10*60*1000L /*10 minutes*/)
    ui = UserInterfaceImp.instance
    controller = FloatControllerImp(this,40)
    controller.setClickListener {
      if(it == FloatController.State.IDLE){
        if(mainRoot != null){
          service.runTask(mainRoot!!)
        }else{
          Log.d("AutoLuaEngineServiceImp", "onCreate: mainRoot is null")
        }
      }else{
        engine?.interrupt()
      }
    }

  }

  override fun onDestroy() {
    super.onDestroy()
    wakeLock.release()
    if(engine != null){
      engine!!.stop()
    }
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    val mainDir = intent.getStringExtra("LUA_MAIN_DIR")
    if (mainDir != null) {
      mainRoot = mainDir
      controller.reShow()
    }else{
      controller.hide()
    }
    return START_REDELIVER_INTENT
  }

  private var inputMethodService: IInputMethodService? = null

  private val engineObserver = object : Observer<AutoLuaEngine.State> {
    override fun onUpdate(data: AutoLuaEngine.State, flags: Int) {
      if(flags == AutoLuaEngine.Target.WORKER.value){
        if(data == AutoLuaEngine.State.IDLE){
          controller.updateState(FloatController.State.IDLE)
        }else{
          controller.updateState(FloatController.State.RUNNING)
        }
      }
      service.notifyObservers(Utils.convertEnum(data), flags)
    }
  }
  private fun createEngine(): AutoLuaEngine {
    val engine = com.autolua.engine.core.root.Proxy(this@AutoLuaEngineServiceImp)
    engine.setLocalServices(arrayListOf(
      AutoLuaEngine.LocalService("UI",ui, UserInterface::class.java)
    ))
    engine.addObserver(engineObserver)
    return engine
  }


  private inner  class MyAutoLuaEngineService : IMainService.Stub(),
    AutoLuaEngineService, Observable<AutoLuaEngineService.State> by ObservableImpOnMainThread() {
    override fun getState(): AutoLuaEngineService.State {
      if(engine == null){
        return AutoLuaEngineService.State.IDLE
      }
      return Utils.convertEnum(engine!!.getState(AutoLuaEngine.Target.ENGINE))
    }



    override fun start() {
      if(engine == null) {
        val engine = createEngine()
        this@AutoLuaEngineServiceImp.engine = engine
        engine.start()
      }
    }

    override fun stop() {
      if(engine != null) {
        engine!!.stop()
        engine = null
      }
    }

    private fun ensureExecuteTask(task: (engine:AutoLuaEngine?)->Unit){
      var e = engine
      if(e != null) return task(e)
      e = createEngine()
      engine = e
      e.start{
        if(it == AutoLuaEngine.ResultCode.SUCCESS){
          task(e)
        }else{
          task(null)
        }
      }
    }




    override fun runTask(rootDir: String) {
      ensureExecuteTask {
        if (it != null) {
          it.setRootDir(rootDir)
          val script = """
            loadfile("init.lua")()
            loadfile('main.lua')()
          """.trimIndent()
          it.execute(script)
          return@ensureExecuteTask
        }else{
          Log.d("AutoLuaEngineServiceImp", "runTask: engine is null")
        }
      }
    }
    

    override fun startDebugService(port: Int, ip: String?, auth: String?) {
      val info = AutoLuaEngine.DebuggerConfigure(port)
      info.host = ip
      info.auth = auth
      ensureExecuteTask {
        if(it != null){
          it.startDebugger(info)
        }else{
          Log.d("AutoLuaEngineServiceImp", "startDebugService: engine is null")
        }
      }
    }

    override fun stopDebugService() {
      engine?.stopDebugger()
    }


    override fun getDebugServiceState(): AutoLuaEngineService.State {
      val debugState: AutoLuaEngine.State = engine?.getState(AutoLuaEngine.Target.DEBUGGER) ?: return AutoLuaEngineService.State.IDLE
      return Utils.convertEnum(debugState)
    }

    override fun bind(input: IInputMethodService?) {
      inputMethodService = input
    }

    override fun unbind() {
      inputMethodService = null
    }

  }

  private var service = MyAutoLuaEngineService()
  override fun onBind(intent: Intent): IBinder {
    return service
  }
}