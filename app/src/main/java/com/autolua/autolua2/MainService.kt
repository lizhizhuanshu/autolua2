package com.autolua.autolua2

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
import androidx.core.app.NotificationCompat
import com.autolua.autolua2.base.Constants
import com.autolua.autolua2.project.ProjectManagerImp
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineProxy

class MainService : Service() {

  private lateinit var wakeLock: WakeLock
  override fun onCreate() {
    super.onCreate()
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val notificationChannel = NotificationChannel(
        Constants.FOREGROUND_SERVICE_CHANNEL_ID,
        resources.getString(R.string.main_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT
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
      .setOngoing(true)
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
  }

  override fun onDestroy() {
    super.onDestroy()
    wakeLock.release()
    ProjectManagerImp.instance.stop()
    AutoLuaEngineProxy.instance.destroy()
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    return START_REDELIVER_INTENT
  }

  private var inputMethodService: IInputMethodService? = null

  private inner  class MyAutoLuaEngineService : IMainService.Stub() {
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