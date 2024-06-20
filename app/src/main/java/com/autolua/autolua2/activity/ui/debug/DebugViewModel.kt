package com.autolua.autolua2.activity.ui.debug

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.autolua.autolua2.base.Constants
import com.autolua.autolua2.activity.PreferencesHelper
import java.net.Inet4Address
import java.net.NetworkInterface

class DebugViewModel(application:Application):AndroidViewModel(application)  {
  private val preferencesHelper = PreferencesHelper(application)

  private fun getIPAddress(): String? {
    try {
      // 遍历所有的网络接口
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces.hasMoreElements()) {
        val networkInterface = interfaces.nextElement()

        // 确保接口不是回环接口且处于激活状态
        if (networkInterface.isLoopback || !networkInterface.isUp) continue

        // 获取网络接口的所有 IP 地址
        val addresses = networkInterface.inetAddresses
        while (addresses.hasMoreElements()) {
          val address = addresses.nextElement()

          // 检查是否是 IPv4 地址
          if (address is Inet4Address) {
            return address.hostAddress
          }
        }
      }
    } catch (ex: Exception) {
      ex.printStackTrace()
    }
    return null
  }

  private val _myIP = MutableLiveData<String>().apply {
    val connectivityManager =
      application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val actionNetwork = connectivityManager.activeNetwork
      val linkProperties = connectivityManager.getLinkProperties(actionNetwork)
      value = linkProperties?.let {
        var result:String? = null
        val addresses = linkProperties.linkAddresses
        for (address in addresses) {
          if (address.address is Inet4Address) {
            result = address.address.hostAddress
          }
        }
        result
      }?:"No IP Address"
    } else {
      value = getIPAddress()?:"No IP Address"
    }
  }

  val myIP: LiveData<String> = _myIP

  private val _listenPort = MutableLiveData<Int>().apply {
    value = preferencesHelper.getInt("listenPort", 8177)
  }
  val listenPort: LiveData<Int> = _listenPort

  fun saveListenPort(port:Int){
    if(port == _listenPort.value) return
    _listenPort.value = port
    preferencesHelper.saveInt("listenPort", port)
  }


  private val _debugToken = MutableLiveData<String>().apply {
    value = preferencesHelper.getString("debugToken", Constants.DEBUG_DEFAULT_TOKEN)
  }

  val debugToken: LiveData<String> = _debugToken

  fun saveDebugToken(token:String){
    if(token == _debugToken.value) return
    _debugToken.value = token
    preferencesHelper.saveString("debugToken", token)
  }

  private val _broadcastLocation = MutableLiveData<Boolean>().apply {
    value = preferencesHelper.getBoolean("broadcastLocation", false)
  }

  val broadcastLocation: LiveData<Boolean> = _broadcastLocation

  fun saveBroadcastLocation(broadcast:Boolean){
    if(broadcast == _broadcastLocation.value) return
    _broadcastLocation.value = broadcast
    preferencesHelper.saveBoolean("broadcastLocation", broadcast)
  }

}