package com.autolua.engine.core.root


import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineOnLocal
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.system.exitProcess

class Bootstrap {

  companion object {
    private fun abort(message: String): Nothing {
      throw RuntimeException(message)
    }

    private fun rawMain(args: Array<String>){
      //从标准输入读取一行数据
      val environment = readlnOrNull() ?: abort("No environment found")
      val localServiceInfo = readlnOrNull() ?: abort("No local service info found")
      val fatherHostInfo = readlnOrNull() ?: abort("No host info found")
      val otherHostInfo = readlnOrNull() ?: abort("No other host info found")

      val gson = Gson()
      val type = object : TypeToken<Array<AutoLuaEngine.Environment<*>>>() {}.type
      val env:Array<AutoLuaEngine.Environment<*>> = gson.fromJson(environment,type)

      val localService = gson.fromJson(localServiceInfo,
        Array<Proxy.LocalServiceInfo>::class.java)
      val localServices = mutableListOf<AutoLuaEngine.LocalService<*>>()
      for(info in localService){
        val local = AutoLuaEngine.LocalService<Any>(info.name)
        local.mClass = Class.forName(info.className) as Class<Any>?
        local.mInterface = Class.forName(info.interfaceName)
        localServices.add(local)
      }
      System.out.flush()
      val fatherHost = AutoLuaEngine.RemoteServerConfigure("father",0)
      fatherHost.rpcServices.addAll(gson.fromJson(fatherHostInfo,
        Array<AutoLuaEngine.RPCServiceInfo>::class.java))
      fatherHost.services = AutoLuaEngine.RemoteServerConfigure.CODE_PROVIDER or
        AutoLuaEngine.RemoteServerConfigure.RESOURCE_PROVIDER or
        AutoLuaEngine.RemoteServerConfigure.OBSERVER or
        AutoLuaEngine.RemoteServerConfigure.CONTROLLER

      val otherRemoteHost = gson.fromJson(otherHostInfo,
        Array<AutoLuaEngine.RemoteServerConfigure>::class.java)
      println(Gson().toJson(fatherHost))
      val engine = AutoLuaEngineOnLocal()
      engine.setEnvironment(env.toList())
      engine.setLocalServices(localServices)
      for(host in otherRemoteHost){
        engine.addRemoteService(host)
      }
      engine.startFatherService(fatherHost)
      println("ok")
      System.out.flush()
      engine.start()
      engine.waitForStop()
      engine.close()
    }

    @JvmStatic
    fun main(args: Array<String>) {
      try{
        AutoLuaEngineOnLocal.changeLogChannel(1)
        rawMain(args)
      }catch (e:Exception){
        e.printStackTrace(System.out)
        println("abort")
        System.out.flush()
      }finally {
        println("AutoLuaEngineByRoot exit")
        exitProcess(0)
      }
    }
  }
}