package com.autolua.engine.core.root

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.autolua.engine.base.LuaContext
import com.autolua.engine.common.Observable
import com.autolua.engine.common.ObservableImp
import com.autolua.engine.core.composite.CodeProvider
import com.autolua.engine.core.composite.ResourceProvider
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineOnLocal
import com.autolua.engine.core.Callback
import com.autolua.engine.core.Utils
import com.autolua.engine.proto.Interaction
import com.autolua.engine.proto.Interaction.ExecuteCode
import com.autolua.engine.proto.Interaction.GetCodeRequest
import com.autolua.engine.proto.Interaction.GetCodeResponse
import com.autolua.engine.proto.Interaction.MessageType
import com.autolua.engine.proto.Interaction.NotificationSource
import com.autolua.engine.proto.Interaction.NotifyState
import com.autolua.engine.proto.Interaction.SetRootDirCommand
import com.google.gson.Gson
import com.google.protobuf.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference


class Proxy constructor(private val packageCodePath:String) :AutoLuaEngine,
  Observable<AutoLuaEngine.State> by ObservableImp(){


  private val codeProvider = CodeProvider()
  private val resourceProvider = ResourceProvider()
  @Volatile
  private var state = AutoLuaEngine.State.IDLE
  @Volatile
  private var debugState = AutoLuaEngine.State.IDLE
  @Volatile
  private var workerState = AutoLuaEngine.State.IDLE

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
      rpcService.releaseService()
    }
    notifyObservers(newState,target.value)
  }

  private fun engineIdle(){
    if(workerState != AutoLuaEngine.State.IDLE)
      changeAndNotifyState(AutoLuaEngine.State.IDLE,AutoLuaEngine.Target.WORKER)
    if(debugState != AutoLuaEngine.State.IDLE)
      changeAndNotifyState(AutoLuaEngine.State.IDLE,AutoLuaEngine.Target.DEBUGGER)
    changeAndNotifyState(AutoLuaEngine.State.IDLE,AutoLuaEngine.Target.ENGINE)
  }




  private val serviceInfo = mutableListOf<AutoLuaEngine.RemoteServerConfigure>()

  private val environment = mutableListOf<AutoLuaEngine.Environment<*>>()
  private val localServices = mutableListOf<LocalServiceInfo>()
  private val rpcService = ServiceManager()
  @Volatile
  private var debuggerConfigure : AutoLuaEngine.RemoteServerConfigure? = null
  @Volatile
  private var transporter:Transporter? = null
  @Volatile
  private var process:Process? = null
  @Volatile
  private var thread:Thread? = null

  constructor(context: Context) : this(context.packageCodePath) {
  }



  private fun onStart():Boolean {
    Log.e("AutoLuaEngineByRoot","Starting root process")
    val starter = RootProcessStarter()
    starter.setStartClass(Bootstrap::class.java)
    starter.setPackagePath(packageCodePath)
    val process = starter.start()
    if(process == null){
      Log.e("AutoLuaEngineByRoot","Failed to start root process")
      return false
    }
    val transporter = Transporter(process)
    val gson = Gson()
    val env = gson.toJson(environment)
    val localServiceInfo = gson.toJson(localServices)
    val fatherRPCService = gson.toJson(rpcService.serviceList())
    val otherHostInfo = gson.toJson(serviceInfo)
    transporter.rawWriteLine(env)
    transporter.rawWriteLine(localServiceInfo)
    transporter.rawWriteLine(fatherRPCService)
    transporter.rawWriteLine(otherHostInfo)
    while (true) {
      val line = transporter.rawReadLine()
      if (line == "ok" || ("\nok" in line) || ("ok\n" in line)){
        break
      }else if(line == "abort"){
        transporter.destroy()
        process.destroy()
        return false
      }else{
        Log.d("AutoLuaEngineByRoot",line)
      }
    }
    transporter.onStandOut { message ->
      if(message.startsWith(DEBUG_LOG_HEADER)){
        Log.d("AutoLuaEngineFromRoot", message.substring(DEBUG_LOG_HEADER.length))
      }else if(message.startsWith(INFO_LOG_HEADER)){
        Log.i("AutoLuaEngineFromRoot", message.substring(INFO_LOG_HEADER.length))
      }else if(message.startsWith(WARN_LOG_HEADER)) {
        Log.w("AutoLuaEngineFromRoot", message.substring(WARN_LOG_HEADER.length))
      }else if(message.startsWith(ERROR_LOG_HEADER)){
        Log.e("AutoLuaEngineFromRoot", message.substring(ERROR_LOG_HEADER.length))
      }else {
        Log.d("AutoLuaEngineFromRoot", message)
      }
    }
    transporter.onMessage { type, data ->
      Log.d("AutoLuaEngineFromRoot","Receive message type $type")
      when(type){
        MessageType.kNotifyState -> {
          val req = data as NotifyState
          val state = AutoLuaEngine.State.fromInt(req.state)
          val target = when(req.other){
            NotificationSource.kEngine -> AutoLuaEngine.Target.ENGINE
            NotificationSource.kWorker -> AutoLuaEngine.Target.WORKER
            NotificationSource.kDebugger -> AutoLuaEngine.Target.DEBUGGER
            else -> AutoLuaEngine.Target.ENGINE
          }

          changeAndNotifyState(state,target)
        }
        MessageType.kGetCode -> {
          val req = data as GetCodeRequest
          val path = req.path
          var result:AutoLuaEngine.CodeProvider.Code? = null
          if(req.fromType == Interaction.CodeFromType.kFile){
            result = codeProvider.getFile(path)
          }else if(req.fromType == Interaction.CodeFromType.kModule){
            result = codeProvider.getModule(path)
          }
          val builder = GetCodeResponse.newBuilder()
          builder.id = req.id
          if(result != null){
            if(result.type == LuaContext.CodeMode.TEXT)
              builder.codeType = Interaction.CodeType.kText
            else if(result.type == LuaContext.CodeMode.BINARY)
              builder.codeType = Interaction.CodeType.kBinary
            else
              builder.codeType = Interaction.CodeType.kTextOrBinary
            builder.data = ByteString.copyFrom(result.code)
          }
          val response = builder.build()
          transporter.send(MessageType.kGetCodeResponse.number.toUByte(),response.toByteArray())
        }
        MessageType.kGetResource -> {
          val req = data as Interaction.GetResourceRequest
          val path = req.path
          val result = resourceProvider.getResource(path)
          val builder = Interaction.GetResourceResponse.newBuilder()
          builder.id = req.id
          if(result != null){
            builder.data = ByteString.copyFrom(result)
          }
          val response = builder.build()
          transporter.send(MessageType.kGetResourceResponse.number.toUByte(),response.toByteArray())
        }
        MessageType.kRpc -> {
          val req = data as Interaction.RpcRequest
          val result = rpcService.invoke(req.service,req.method,req.data.toByteArray())
          val builder = Interaction.RpcResponse.newBuilder()
          builder.id = req.id
          builder.data = ByteString.copyFrom(result.toByteArray())
          val response = builder.build()
          transporter.send(MessageType.kRpcResponse.number.toUByte(),response.toByteArray())
        }
        else -> {
          Log.e("AutoLuaEngineByRoot","Unknown message type $type")
        }
      }
    }
    this.transporter = transporter
    this.process = process
    transporter.start()
    if(debuggerConfigure!=null) {
      sendStartDebuggerCommand()
    }
    return true
  }

  private fun sendStartDebuggerCommand(){
    val command = Interaction.StartDebuggerCommand.newBuilder()
    debuggerConfigure?.let {
      command.setPort(it.port)
      val info = Gson().toJson(debuggerConfigure)
      command.setInfo(info)
    }
    transporter?.send(MessageType.kStartDebuggerCommand.number.toUByte(),command.build().toByteArray())
  }



  private val starting = AtomicReference(false)
  override fun setRootDir(rootDir: String) {
    val builder = SetRootDirCommand.newBuilder()
    builder.rootDir = rootDir
    val command = builder.build()
    transporter?.send(MessageType.kSetRootDirCommand.number.toUByte(),command.toByteArray())
  }

  override fun start(callback: Callback?) {
    if(starting.compareAndSet(false,true)){
      if(state == AutoLuaEngine.State.IDLE){
        changeAndNotifyState(AutoLuaEngine.State.STARTING,AutoLuaEngine.Target.ENGINE)
        thread = Thread{
          if(onStart()){
            callback?.invoke(AutoLuaEngine.ResultCode.SUCCESS)
          }else{
            callback?.invoke(AutoLuaEngine.ResultCode.SU_FAIL)
          }
          starting.set(false)
        }
        thread?.start()
      }else{
        starting.set(false)
        callback?.invoke(AutoLuaEngine.ResultCode.RUNNING)
      }
    }
  }


  private fun onStop(transporter: Transporter, process: Process){
    val closed = AtomicReference(false)
    val exitThread = Thread{
      transporter.send(MessageType.kStopEngineCommand.number.toUByte(), null)
      try{
        Thread.sleep(TimeUnit.SECONDS.toMillis(1))
        process.outputStream.close()
        process.inputStream.close()
        process.errorStream.close()
        process.waitFor()
        transporter.destroy()
        process.destroy()
        closed.set(true)
        engineIdle()
      }catch (e:InterruptedException) {
        Log.d(TAG,"Engine stop interrupted")
      }
      Log.d(TAG,"Engine stopped")
    }
    val ensureExitThread = Thread{
      exitThread.start()
      exitThread.join(TimeUnit.SECONDS.toMillis(5))
      if (!closed.get()){
        process.outputStream.write("exit\n".toByteArray())
        exitThread.join(TimeUnit.SECONDS.toMillis(5))
        Log.d(TAG,"force to stop engine")
        exitThread.interrupt()
        process.destroy()
        transporter.destroy()
        engineIdle()
      }
      stopping.set(false)
    }
    ensureExitThread.start()
  }

  private val stopping = AtomicReference(false)
  override fun stop() {
    if(stopping.compareAndSet(false,true) && state != AutoLuaEngine.State.IDLE){
      onStop(transporter!!,process!!)
      transporter = null
      process = null
    }
  }

  override fun getState(target: AutoLuaEngine.Target): AutoLuaEngine.State {
    return when(target){
      AutoLuaEngine.Target.ENGINE -> state
      AutoLuaEngine.Target.WORKER -> workerState
      AutoLuaEngine.Target.DEBUGGER -> debugState
    }
  }

  private fun luaCodeModeToMessageCodeModeType(mode:LuaContext.CodeMode):Interaction.CodeType {
    return when(mode){
      LuaContext.CodeMode.TEXT -> Interaction.CodeType.kText
      LuaContext.CodeMode.BINARY -> Interaction.CodeType.kBinary
      LuaContext.CodeMode.TEXT_OR_BINARY -> Interaction.CodeType.kTextOrBinary
    }
  }

  override fun execute(script: String, flags: Int): Int {
    return execute(script.toByteArray(), LuaContext.CodeMode.TEXT_OR_BINARY, flags)
  }

  override fun execute(script: ByteArray, codeMode: LuaContext.CodeMode, flags: Int): Int {
    val builder = ExecuteCode.newBuilder()
    builder.code = ByteString.copyFrom(script)
    builder.codeType = luaCodeModeToMessageCodeModeType(codeMode)
    val request = builder.build()
    transporter?.send(MessageType.kExecuteCode.number.toUByte(),request.toByteArray())
    return 0
  }

  override fun interrupt() {
    transporter?.send(MessageType.kInterrupt.number.toUByte(),null)
  }



  override fun startDebugger(debuggerConfigure: AutoLuaEngine.DebuggerConfigure) {
    if(debugState == AutoLuaEngine.State.IDLE){
      val configure = Utils.debuggerConfigure2RemoteServerConfigure(debuggerConfigure)
      this.debuggerConfigure = configure
      sendStartDebuggerCommand()
    }
  }

  override fun stopDebugger() {
    transporter?.send(MessageType.kStopDebuggerCommand.number.toUByte(),null)
  }



  override fun attach(messageObserver: AutoLuaEngine.MessageObserver) {
    TODO("Not yet implemented")
  }


  override fun detach(messageObserver: AutoLuaEngine.MessageObserver) {
    TODO("Not yet implemented")
  }

  override fun addRemoteService(remoteServerConfigure: AutoLuaEngine.RemoteServerConfigure) {
    serviceInfo.add(remoteServerConfigure)
  }

  @RequiresApi(Build.VERSION_CODES.N)
  override fun removeRemoteService(name: String) {
    serviceInfo.removeIf { it.name == name }
  }

  override fun setEnvironment(environment: List<AutoLuaEngine.Environment<*>>) {
    this.environment.clear()
    this.environment.addAll(environment)
  }

  data class LocalServiceInfo(val name:String,val className:String,val interfaceName:String)

  override fun setLocalServices(services: List<AutoLuaEngine.LocalService<*>>) {
    localServices.clear()
    rpcService.clear()
    for (service in services) {
      if(service.service != null){
        rpcService.register(service.name,service.mInterface as Class<*> , service.service!!)
      }else{
        localServices.add(LocalServiceInfo(service.name,service.mClass!!.name,service.mInterface!!.name))
      }
    }
  }

  override fun addCodeProvider(codeProvider: AutoLuaEngine.CodeProvider) {
    return this.codeProvider.addProvider(codeProvider)
  }

  override fun clearCodeProvider() {
    return this.codeProvider.clear()
  }

  override fun addResourceProvider(resourceProvider: AutoLuaEngine.ResourceProvider) {
    return this.resourceProvider.addProvider(resourceProvider)
  }

  override fun clearResourceProvider() {
    return this.resourceProvider.clear()
  }

  override fun removeCodeProvider(codeProvider: AutoLuaEngine.CodeProvider) {
    return this.codeProvider.removeProvider(codeProvider)
  }

  override fun removeResourceProvider(resourceProvider: AutoLuaEngine.ResourceProvider) {
    return this.resourceProvider.removeProvider(resourceProvider)
  }

  companion object {
    private const val TAG = "AutoLuaEngine Proxy"
    private const val DEBUG_LOG_HEADER = "debug:"
    private const val INFO_LOG_HEADER = "info:"
    private const val WARN_LOG_HEADER = "warn:"
    private const val ERROR_LOG_HEADER = "error:"
  }

}