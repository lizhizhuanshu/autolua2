package com.autolua.autolua2.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.autolua.autolua2.R
import com.autolua.autolua2.base.Constants
import com.autolua.autolua2.activity.DebugActivity
import com.autolua.engine.common.Observable

import com.autolua.engine.common.Utils
import com.autolua.autolua2.engine.AutoLuaEngineServiceImp
import com.autolua.autolua2.engine.AutoLuaEngineService
import com.autolua.engine.common.ObservableImpOnMainThread
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autolua.autolua2.base.Configure
import com.autolua.autolua2.project.ProjectManagerImp
import com.autolua.engine.core.AutoLuaEngine
import com.immomo.luanative.hotreload.HotReloadServer
import com.immomo.luanative.hotreload.Transporter
import com.immomo.mls.HotReloadHelper
import com.immomo.mls.MLSBundleUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.HashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReferenceArray

class DebugServiceImp : Service() {
  companion object {
    private const val TAG = "DebugServiceImp"
  }

  private var state = DebugService.State.IDLE
  private fun tryChangeAndNotify(state: DebugService.State) {
    if (this.state == state) return
    this.state = state
    binder.notifyObservers(state)
  }
  private val componentState = AtomicReferenceArray<DebugService.State>(3)
  @Volatile
  private var engineService:AutoLuaEngineService? = null
  private val debugServerObserver: (debugServerState: DebugServer.State)->Unit = {
    componentState.set(0,Utils.convertEnum(it))
    Log.d(TAG,"debugServerObserver $it")
    componentUpdateState()
  }
  private val engineDebugObserver = { state:AutoLuaEngineService.State ->
    componentState.set(1,Utils.convertEnum(state))
    Log.d(TAG,"engineDebugObserver $state")
    componentUpdateState()
  }
  private val broadcastServiceListener: (state: BroadcastLocationServer.State)->Unit = {
    componentState.set(2,Utils.convertEnum(it))
    Log.d(TAG,"broadcastServiceListener $it")
    componentUpdateState()
  }

  private fun findNotComponentState(state:DebugService.State,endIndex:Int):Int{
    for (i in 0 until endIndex){
      val e = componentState.get(i)
      if(e != state) return i
    }
    return -1
  }

  private fun findComponentState(state:DebugService.State,endIndex:Int):Int{
    for (i in 0 until endIndex){
      val e = componentState.get(i)
      if(e == state) return i
    }
    return -1
  }

  private fun componentUpdateState(){
    synchronized(this){
      val endIndex = if(startBroadcast) 3 else 2
      if(findComponentState(DebugService.State.STARTING,endIndex)>=0){
        tryChangeAndNotify(DebugService.State.STARTING)
        return
      }
      if(findComponentState(DebugService.State.STOPPING,endIndex)>=0){
        tryChangeAndNotify(DebugService.State.STOPPING)
        engineService?.stopDebugService()
        debugServer.stop()
        broadcastServer.stop()
      }

      if(findNotComponentState(DebugService.State.IDLE,endIndex)==-1){
        tryChangeAndNotify(DebugService.State.IDLE)
        return
      }
      if(findNotComponentState(DebugService.State.RUNNING,endIndex)==-1){
        tryChangeAndNotify(DebugService.State.RUNNING)
        Configure.rootDir = "http://127.0.0.1:${port!!}/"
        return
      }
    }
  }


  private val serviceConnection: ServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      Log.d(TAG, "onServiceConnected: $name")
      engineService = (service as AutoLuaEngineService)
      engineService?.addObserver( AutoLuaEngine.Target.DEBUGGER.value,engineDebugObserver)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      engineService = null
    }
  }

  private val receiver = object :BroadcastReceiver(){
    override fun onReceive(p0: Context?, p1: Intent?) {
      debugServer.startUIDebug()
    }
  }



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
    builder.setContentTitle(resources.getString(R.string.debug_notification_title))
      .setSmallIcon(R.mipmap.ic_launcher_round)
      .setContentText(resources.getString(R.string.debug_notification_content))
      .setWhen(System.currentTimeMillis())

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      builder.setChannelId(Constants.FOREGROUND_SERVICE_CHANNEL_ID)

    val notification = builder.build()
    notification.defaults = Notification.DEFAULT_SOUND
    startForeground(Constants.DEBUG_SERVICE_NOTIFICATION_ID, notification)
    Intent(this,AutoLuaEngineServiceImp::class.java).also { intent ->
      bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(DebugActivity.STARTED_BROADCAST))
  }

  private var eventLoopGroup: EventLoopGroup = NioEventLoopGroup(1)
  private var debugServer:DebugServer = DebugServerImp(eventLoopGroup)
  private var broadcastServer:BroadcastLocationServer = BroadcastLocationServer(eventLoopGroup)

  private val uiTransporter = object : Transporter {
    private val queue = LinkedBlockingQueue<Transporter.Data>()
    override fun send(type: Int, data: ByteArray) {
      debugServer.sendMessageToUIDebugKit(type,data)
    }

    override fun take(p0: Transporter.Data): Boolean {
      Log.d(TAG,"uiTransporter take")
      val data = queue.take()
      Log.d(TAG,"uiTransporter take ${data.type}")
      p0.type = data.type
      p0.message = data.message
      return true
    }

    fun put(type: Int, data: ByteArray){
      val d = Transporter.Data()
      d.type = type
      d.message = data
      queue.put(d)
    }
  }
  init{
    broadcastServer.setListener(broadcastServiceListener)
    debugServer.addObserver(debugServerObserver)
    debugServer.setUIDebugKitListener {
      if(it){
        startUIDebugActivity()
      }else{
        finishUIDebugActivity()
      }
    }
    debugServer.setUIDebugKitMessageHandler{ type, data ->
      uiTransporter.put(type,data)
    }
    debugServer.setPostProjectHandler { project, data ->
      val bytes = ByteArray(data.readableBytes())
      data.readBytes(bytes)
      ProjectManagerImp.instance.addProject(project,bytes)
    }
    HotReloadServer.getInstance().setTransporter(uiTransporter)
    HotReloadHelper.setCodeProvider {
      Log.d(TAG,"requestUICode $it")
      val r =debugServer.requestUICode(it)
      r
    }
  }

  private fun startUIDebugActivity(){
    CoroutineScope(Dispatchers.Main).launch {
      val initData =
        MLSBundleUtils.createInitData(com.immomo.mls.Constants.ASSETS_PREFIX + "hotreload.lua?ct="+ HotReloadServer.NET_CONNECTION)
      initData.extras = HashMap<Any,Any>()
      initData.extras["ROOT_DIR"] = Configure.rootDir
      initData.extras["RESOURCE_DIR"] = Configure.resDir
      val intent = Intent(this@DebugServiceImp, DebugActivity::class.java)
      intent.putExtras(MLSBundleUtils.createBundle(initData))
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
    }
  }

  private fun finishUIDebugActivity(){
    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(DebugActivity.CLOSE_BROADCAST))
  }

  override fun onDestroy() {
    super.onDestroy()
    HotReloadServer.getInstance().setTransporter(null)
    HotReloadHelper.setCodeProvider(null)
    engineService?.removeObserver(engineDebugObserver)
    unbindService(serviceConnection)
    debugServer.removeObserver(debugServerObserver)
    debugServer.stop()
    broadcastServer.stop()
    eventLoopGroup.shutdownGracefully()
    LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
  }

  private var port:Int? = null
  private var auth:String? = null
  private var startBroadcast:Boolean = false


  private inner class DebugServiceImpBinder() :Binder(),
    DebugService,
    Observable<DebugService.State> by ObservableImpOnMainThread() {

    override fun set(port: Int, auth: String, startBroadcast: Boolean) {
      this@DebugServiceImp.port = port
      this@DebugServiceImp.auth = auth
      this@DebugServiceImp.startBroadcast = startBroadcast
    }

    override fun start() {
      Log.d(TAG,"start debug service")
      if(port == null || auth == null){
        Toast.makeText(this@DebugServiceImp,"port or auth is null",Toast.LENGTH_SHORT).show()
        return
      }
//      HotReloadServer.getInstance().start()
      if (startBroadcast) {
        broadcastServer.start(port!!)
      }
      debugServer.start(port!!,auth!!) {
        if (it) {
          engineService?.startDebugService(port!!,null,auth!!)
        }else{
          debugServer.stop()
          Toast.makeText(this@DebugServiceImp,"start debug server failed",Toast.LENGTH_SHORT).show()
        }
      }
    }

    override fun stop() {
      HotReloadServer.getInstance().stop()
      engineService?.stopDebugService()
      debugServer.stop()
      broadcastServer.stop()
    }

    override fun getState(): DebugService.State {
      synchronized(this@DebugServiceImp){
        return state
      }
    }

  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_REDELIVER_INTENT
  }

  private val binder = DebugServiceImpBinder()
  override fun onBind(intent: Intent): IBinder {
    return binder
  }
}