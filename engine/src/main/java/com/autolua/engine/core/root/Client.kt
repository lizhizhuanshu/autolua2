package com.autolua.engine.core.root

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.autolua.engine.base.MethodCache
import com.autolua.engine.base.Releasable
import com.autolua.engine.common.Observable
import com.autolua.engine.common.ObservableImpOnMainThread
import com.autolua.engine.common.Observer
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.common.Utils
import com.autolua.engine.core.composite.CodeProvider
import com.autolua.engine.core.composite.ResourceProvider
import com.autolua.engine.extension.display.Display
import com.autolua.engine.extension.input.InputManager
import com.autolua.engine.extension.node.UiAutomator
import kotlinx.parcelize.Parcelize
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Scanner

object Client {
  @Parcelize
  data class LocalService(val name:String,val interfaceName:String,
                          val serviceId:UInt? = null,
                          val className:String? = null):Parcelable

  class Builder(context:Context):AutoLuaEngine.Builder{
    private val packagePath = context.packageCodePath
    private val environments = mutableListOf<AutoLuaEngine.Environment<*>>()
    private val localServices = mutableListOf<LocalService>()
    private val remoteServices = mutableListOf<AutoLuaEngine.RemoteServerConfigure>()
    private val serviceManager = ServiceManagerImp()
    private val codeProviders = CodeProvider()
    private val resourceProviders = ResourceProvider()
    private val observer = MyObserver()
    init{
      serviceManager.registerService(InvokePackage.CODE_PROVIDER,codeProviders,AutoLuaEngine.CodeProvider::class.java)
      serviceManager.registerService(InvokePackage.RESOURCE_PROVIDER,resourceProviders,AutoLuaEngine.ResourceProvider::class.java)
      serviceManager.registerService(InvokePackage.OBSERVER,observer,Observer::class.java)
    }
    override fun addRemoteService(remoteServerConfigure: AutoLuaEngine.RemoteServerConfigure): AutoLuaEngine.Builder {
      remoteServices.add(remoteServerConfigure)
      return this
    }

    override fun addLocalService(localService: AutoLuaEngine.LocalService<*>): AutoLuaEngine.Builder {
      val obj = if(localService.type == AutoLuaEngine.LocalService.Type.CLASS){
        LocalService(localService.name,localService.mInterface!!.name,className = localService.mClass!!.name)
      }else{
        val serviceId = serviceManager.registerService(localService.service!!,localService.mInterface!!)
        LocalService(localService.name,localService.mInterface!!.name,serviceId = serviceId)
      }
      localServices.add(obj)
      return this
    }

    override fun addLocalService(
      name: String,
      service: Any,
      thisInterface: Class<*>
    ): AutoLuaEngine.Builder {
      val serviceId = serviceManager.registerService(service,thisInterface)
      localServices.add(LocalService(name,thisInterface.name,serviceId = serviceId))
      return this
    }

    override fun addLocalService(
      name: String,
      serviceClass: Class<*>,
      thisInterface: Class<*>
    ): AutoLuaEngine.Builder {
      localServices.add(LocalService(name,thisInterface.name,className = serviceClass.name))
      return this
    }

    override fun addEnvironment(key: String, value: Any): AutoLuaEngine.Builder {
      environments.add(AutoLuaEngine.Environment(key,value))
      return this
    }

    override fun addCodeProvider(codeProvider: AutoLuaEngine.CodeProvider): AutoLuaEngine.Builder {
      codeProviders.addProvider(codeProvider)
      return this
    }

    override fun addResourceProvider(resourceProvider: AutoLuaEngine.ResourceProvider): AutoLuaEngine.Builder {
      resourceProviders.addProvider(resourceProvider)
      return this
    }

    override fun setDisplay(display: Display): AutoLuaEngine.Builder {
      TODO("Not yet implemented")
    }

    override fun setInputManager(inputManager: InputManager): AutoLuaEngine.Builder {
      TODO("Not yet implemented")
    }

    override fun setUiAutomator(uiAutomator: UiAutomator): AutoLuaEngine.Builder {
      TODO("Not yet implemented")
    }

    override fun isRoot(isRoot: Boolean): AutoLuaEngine.Builder {
      TODO("Not yet implemented")
    }


    override fun build():AutoLuaEngine?{
      val process =
        RootProcessStarter()
          .setPackagePath(packagePath)
          .setStartClass(Server::class.java)
          .start()
          ?: return null
      val transporter = TransportImp(process.errorStream,process.outputStream)
      transporter.setOnOtherMessage {
        log(it)
      }
      val parcel = Parcel.obtain()
      parcel.writeValue(environments)
      parcel.writeValue(localServices)
      parcel.writeValue(remoteServices)
      val data = parcel.marshall()
      parcel.recycle()
      transporter.rawWrite(data)
      val result = transporter.rawRead() ?: return null
      if(result.toString(Charsets.UTF_8) != "ok"){
        return null
      }
      return Proxy.newProxyInstance(AutoLuaEngine::class.java.classLoader,
        arrayOf(AutoLuaEngine::class.java),
        ClientInvocationHandler(process,transporter,
          serviceManager,observer)
      ) as AutoLuaEngine
    }
  }

  private class MyObserver:Observer<AutoLuaEngine.State>{
    val observers = ObservableImpOnMainThread<AutoLuaEngine.State>()
    @Volatile
    var state = AutoLuaEngine.State.IDLE
    @Volatile
    var debugState = AutoLuaEngine.State.IDLE
    @Volatile
    var workerState = AutoLuaEngine.State.IDLE

    private fun changeAndNotifyState(newState:AutoLuaEngine.State,target:AutoLuaEngine.Target){
      Log.d(TAG,"$target Change state to $newState")
      when(target){
        AutoLuaEngine.Target.ENGINE -> {
          if(state == newState) return
          state = newState
        }
        AutoLuaEngine.Target.WORKER ->{
          if(workerState == newState) return
          workerState = newState
        }
        AutoLuaEngine.Target.DEBUGGER ->{
          if(debugState == newState) return
          debugState = newState
        }
      }
      if(target == AutoLuaEngine.Target.WORKER && newState == AutoLuaEngine.State.IDLE){
        Log.d(TAG,"Release worker service")
      }
      observers.notifyObservers(newState,target.value)
    }

    override fun onUpdate(data: AutoLuaEngine.State, flags: Int) {
      changeAndNotifyState(data,AutoLuaEngine.Target.fromInt(flags))
    }
  }

  private class ClientInvocationHandler(private val process:Process,
                                        private val transporter: Transporter,
                                        private val serviceManager: ServiceManager,
    private val observer:MyObserver) : InvocationHandler {
    private val dataCallbackManager = DataCallbackManager<Parcel>()
    private val proxyMethods = mutableMapOf<String,(Array<out Any>?)->Any?>()
    private val releasableName:String
    private val stdOut = Thread {
      try{
        val scanner = Scanner(process.inputStream)
        while(scanner.hasNextLine()){
          log(scanner.nextLine())
        }
      }catch (e:Exception){
        Log.e(TAG,"read stdout error",e)
      }
    }
    private val lastLastingIds = serviceManager.allIds().toSet()

    init {
      transporter.setOnMessage {
        val parcel = Parcel.obtain()
        try{
          parcel.unmarshall(it,0,it.size)
          parcel.setDataPosition(0)
          val type = parcel.readByte().toInt()
          Log.d(TAG,"receive type $type")
          if(type == 1){
            onHandlerRequest(parcel)
          }else{
            dataCallbackManager.notify(parcel.readInt(),parcel)
          }
        }finally {
          parcel.recycle()
        }
      }
      onInitProxyMethod()
      transporter.start()
      stdOut.start()
      releasableName = Releasable::class.java.methods[0].name
    }

    private fun onInitProxyMethod() {
      proxyMethods["getState"]= {
        val target = it?.get(0) as AutoLuaEngine.Target
        when(target) {
          AutoLuaEngine.Target.ENGINE -> observer.state
          AutoLuaEngine.Target.WORKER -> observer.workerState
          AutoLuaEngine.Target.DEBUGGER -> observer.debugState
        }
      }
    }
    private val methodCache = MethodCache()
    private fun findMethod(servicePage: ServiceManager.ServicePage, method: String, args: Array<out Any>?): Method {
      val clazz = servicePage.interfaceClass
      return methodCache.findMethod(clazz,method) ?: throw NoSuchMethodException("Method not found")
    }


    private fun onHandlerRequest(parcel: Parcel){
      val invokePackage = parcel.readValue(InvokePackage::class.java.classLoader) as InvokePackage?
        ?: return
      val serviceId = invokePackage.serviceId
      val callId = invokePackage.callId
      val methodName = invokePackage.method
      Log.d(TAG,"service $serviceId invoke method $methodName")
      val args = invokePackage.args
      val response = Parcel.obtain()
      response.writeByte(2)
      response.writeInt(callId)
      try{
        val service = serviceManager.findService(serviceId) ?: throw IllegalArgumentException("Service not found")
        var result: Any?
        if(methodName == releasableName && args == null){
          if(!lastLastingIds.contains(serviceId)){
            serviceManager.unregisterService(serviceId)
          }
          result = if(Releasable::class.java.isAssignableFrom(service.interfaceClass)){
            service.interfaceClass.getMethod(releasableName).invoke(service.instance)
          }else{
            null
          }
        }else{
          val method = findMethod(service,methodName,args)
          result = if(args == null) method.invoke(service.instance) else method.invoke(service.instance,*args)
          if(method.returnType.isInterface){
            result = serviceManager.registerService(result,method.returnType).toLong()
          }
        }
        response.writeNoException()
        response.writeValue(result)
      }catch (e:Exception){
        e.printStackTrace()
        response.writeException(e)
      }
      Utils.log(TAG,"data size ${response.dataSize()}  data position ${response.dataPosition()}")
      val data = response.marshall()
      transporter.send(data)
      response.recycle()
    }
    private fun hasMethod(clazz: Class<*>, method: Method):Boolean{
      return try{
        clazz.getMethod(method.name,*method.parameterTypes)
        true
      }catch (e:NoSuchMethodException){
        false
      }
    }

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
      if(hasMethod(Observable::class.java,method)){
        return if(args == null) method.invoke(observer.observers) else method.invoke(observer.observers,*args)
      }
      val proxyMethod = proxyMethods[method.name]
      if(proxyMethod != null){
        return proxyMethod(args)
      }
      val parcel = Parcel.obtain()
      parcel.writeByte(1)
      val id = dataCallbackManager.create()
      val invokePackage = InvokePackage(0u,id,method.name,args)
      parcel.writeValue(invokePackage)
      val data = parcel.marshall()
      parcel.recycle()
      transporter.send(data)
      if(method.name == "destroy"){
        return onDestroy()
      }
      return null
    }


    private fun onDestroy(){
      stdOut.interrupt()
      transporter.stop()
      val releaseThread = Thread {
        try{
          while (true){
            val data = transporter.rawRead() ?: break
            if(data.toString(Charsets.UTF_8) == "exit"){
              break
            }
          }
        }catch (e:Exception){
          Log.d(TAG,"force exit process")
        }
        process.destroy()
        dataCallbackManager.clear()
        serviceManager.clear()
      }
      releaseThread.start()
      releaseThread.join(3000)
      if (releaseThread.isAlive) {
        releaseThread.interrupt()
      }
    }
  }
  private const val TAG = "AutoLuaEngineClient"
  private const val DEBUG_LOG_HEADER = "debug:"
  private const val INFO_LOG_HEADER = "info:"
  private const val WARN_LOG_HEADER = "warn:"
  private const val ERROR_LOG_HEADER = "error:"
  private fun log(message:String){
    when {
      message.startsWith(DEBUG_LOG_HEADER) -> {
        val log = message.substring(DEBUG_LOG_HEADER.length)
        Log.d(TAG, log)
      }
      message.startsWith(INFO_LOG_HEADER) -> {
        val log = message.substring(INFO_LOG_HEADER.length)
        Log.i(TAG,log)
      }
      message.startsWith(WARN_LOG_HEADER) -> {
        val log = message.substring(WARN_LOG_HEADER.length)
        Log.w(TAG,log)
      }
      message.startsWith(ERROR_LOG_HEADER) -> {
        val log = message.substring(ERROR_LOG_HEADER.length)
        Log.e(TAG,log)
      }
      else ->{
        Log.d(TAG,message)
      }
    }
  }
  private fun log(message:ByteArray){
    log(message.toString(Charsets.UTF_8))
  }

}