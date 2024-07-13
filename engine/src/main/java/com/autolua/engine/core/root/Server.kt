package com.autolua.engine.core.root

import android.annotation.SuppressLint
import android.os.Parcel
import android.os.Parcelable
import com.autolua.engine.base.MethodCache
import com.autolua.engine.base.Releasable
import com.autolua.engine.common.Observer
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineOnLocal
import com.autolua.engine.common.Utils
import com.autolua.engine.extension.display.DisplayImplement
import com.autolua.engine.extension.input.InputManagerImp
import com.autolua.engine.extension.node.imp.root.UiAutomatorImp
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess


class Server {
  private val transporter: Transporter = TransportImp(System.`in`,System.err,true)
  private lateinit var engine: AutoLuaEngine
  private val observer:Observer<AutoLuaEngine.State> = newService(InvokePackage.OBSERVER)
  private val codeProvider: AutoLuaEngine.CodeProvider = newService(InvokePackage.CODE_PROVIDER)
  private val resourceProvider: AutoLuaEngine.ResourceProvider = newService(InvokePackage.RESOURCE_PROVIDER)
  private val latch = CountDownLatch(1)
  private val methodCache = MethodCache()
  private val uiAutomator = UiAutomatorImp()

  fun run(){
    transporter.start()
    latch.await()
    onDestroy()
    transporter.rawWrite("exit".toByteArray())
  }

  private fun onDestroy(){
    engine.stop()
    Thread.sleep(3000)
    transporter.stop()
    uiAutomator.destroy()
  }

  private fun findMethod(name:String,args: Array<out Any>?):Method{
    val clazz = AutoLuaEngine::class.java
    return methodCache.findMethod(clazz,name) ?: throw NoSuchMethodException(name)
  }

  private fun onHandlerRequest(parcel: Parcel){
    val invokePackage: InvokePackage = parcel.readValue(InvokePackage::class.java.classLoader) as InvokePackage?
      ?: return
    val callId = invokePackage.callId
    val methodName = invokePackage.method
    println("root AutoLuaEngine invoke $methodName")
    val args = invokePackage.args
    val response = Parcel.obtain()
    response.writeByte(2)
    response.writeInt(callId)
    try{
      if(methodName == "destroy"){
        latch.countDown()
        response.writeNoException()
        response.writeValue(null)
      }else{
        val method = findMethod(methodName,args)
        val result = method.invoke(engine, *args ?: emptyArray())
        response.writeNoException()
        response.writeValue(result)
      }
    }catch (e:Exception){
      if(e is Parcelable){
        response.writeException(e)
      }else{
        response.writeException(RuntimeException(e))
      }
    }
    val data = response.marshall()
    response.recycle()
    transporter.send(data)
  }

  private fun initTransporter(){
    transporter.setOnMessage {
      val parcel = Parcel.obtain()
      parcel.unmarshall(it,0,it.size)
      parcel.setDataPosition(0)
      val type = parcel.readByte().toInt()
      Utils.log("server","receive type $type data size ${it.size}")
      if(type == 1){
        try{
          onHandlerRequest(parcel)
        }finally{
          parcel.recycle()
        }
      }else{
        val id = parcel.readInt()
        dataCallbackManager.notify(id,parcel)
      }
    }
  }
  private fun serviceInfo2LocalService(info: Client.LocalService):AutoLuaEngine.LocalService<*>{
    val interfaceClass = Class.forName(info.interfaceName)
    return if(info.serviceId!=null){
      val obj = newService(info.serviceId,interfaceClass)
      AutoLuaEngine.LocalService(info.name,obj,interfaceClass)
    }
    else{
      val r= AutoLuaEngine.LocalService<Any>(info.name, AutoLuaEngine.LocalService.Type.CLASS)
      r.mClass = Class.forName(info.className!!) as Class<Any>
      r.mInterface = interfaceClass
      r
    }
  }

  @SuppressLint("Recycle")
  private fun initEngine():Boolean{
    val data = transporter.rawRead()
    if(data == null || data.isEmpty()){
      exitProcess(1)
    }
    val parcel = Parcel.obtain()
    parcel.unmarshall(data,0,data.size)
    parcel.setDataPosition(0)
    val environments = parcel.readValue(AutoLuaEngine.Environment::class.java.classLoader) as List<AutoLuaEngine.Environment<*>>
    val localServices = parcel.readValue(Client.LocalService::class.java.classLoader) as List<Client.LocalService>
    val remoteServices = parcel.readValue(AutoLuaEngine.RemoteServerConfigure::class.java.classLoader) as List<AutoLuaEngine.RemoteServerConfigure>
    val builder = AutoLuaEngineOnLocal.Builder()
    for(env in environments){
      builder.addEnvironment(env.key,env.value)
    }
    for(info in localServices){
      builder.addLocalService(serviceInfo2LocalService(info))
    }
    for(info in remoteServices){
      builder.addRemoteService(info)
    }
    builder.addCodeProvider(codeProvider)
    builder.addResourceProvider(resourceProvider)
    builder.setDisplay(DisplayImplement())
    builder.setInputManager(InputManagerImp.instance)
    builder.setUiAutomator(uiAutomator)
    builder.isRoot(true)
    engine = builder.build()
    AutoLuaEngineOnLocal.changeLogChannel(1)
    engine.addObserver(observer)
    return true
  }

  fun init():Boolean{
    initTransporter()
    if(initEngine()){
      transporter.rawWrite("ok".toByteArray())
      return true
    }else{
      transporter.rawWrite("abort".toByteArray())
    }
    return false
  }

  companion object{
    @JvmStatic
    fun main(args: Array<String>){
      Utils.inRootProcess = true
      val server = Server()
      if(!server.init()){
        exitProcess(1)
      }else{
        Utils.log("Server","start")
        server.run()
        exitProcess(0)
      }
    }
  }

  private fun newService(serviceId:UInt,serviceInterface:Class<*>):Any{
    val interfaces = if(!serviceInterface.isAssignableFrom(Releasable::class.java)) arrayOf(serviceInterface,Releasable::class.java) else arrayOf(serviceInterface)
    return Proxy.newProxyInstance(serviceInterface.classLoader, interfaces,ServiceInvocationHandler(serviceId))
  }

  private inline fun <reified T : Any> newService(serviceId:UInt): T {
    return newService(serviceId,T::class.java) as T
  }

  private fun invoke(serviceId:UInt, method:Method, args:Array<out Any>?):Any?{
    val parcel = Parcel.obtain()
    val callId = dataCallbackManager.create()
    val invokePackage = InvokePackage(serviceId,callId,method.name,args)
    parcel.writeByte(1)
    parcel.writeValue(invokePackage)
    val data = parcel.marshall()
    parcel.recycle()
    transporter.send(data)
    val response = dataCallbackManager.waitAndRemote(callId,0)
    try{
      Utils.log("server","invoke wait response ${response.dataSize()}  ${response.dataPosition()}")
      response.readException()
      if(method.returnType.isInterface){
        val result = response.readValue(Long::class.java.classLoader) as Long?
        Utils.log("server","invoke result $result")
        return if(result!=null && result >0){
          newService(result.toUInt(),method.returnType)
        }else
          null
      }
      val result = response.readValue(method.returnType.classLoader)
      return result
    }finally {
      response.recycle()
    }

  }

  private val dataCallbackManager = DataCallbackManager<Parcel>()

  inner class ServiceInvocationHandler(private val serviceId:UInt): InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
      Utils.log("server","invoke ${method.name}")
      return this@Server.invoke(serviceId,method,args)
    }
  }
}