package com.autolua.engine.core

import android.util.Log
import androidx.collection.LongSparseArray
import com.autolua.engine.base.JavaObjectWrapper
import com.autolua.engine.base.LuaContext
import com.autolua.engine.base.LuaContextImplement
import com.autolua.engine.base.LuaObjectAdapter
import com.autolua.engine.base.MethodCache
import com.autolua.engine.base.ObjectCacheImp
import com.autolua.engine.common.Observable
import com.autolua.engine.common.ObservableImp
import com.autolua.engine.core.AutoLuaEngine.*
import com.autolua.engine.extension.display.Display
import com.autolua.engine.extension.display.DisplayImplement


/**
 * luaContext 初始化步骤
 * 1. luaContext create
 * 2. push environment
 * 3. push local services
 * 4. push remote services proxy
 */
class AutoLuaEngineOnLocal (private val codeProvider: com.autolua.engine.core.composite.CodeProvider = com.autolua.engine.core.composite.CodeProvider(),
                            private val resourceProvider: com.autolua.engine.core.composite.ResourceProvider = com.autolua.engine.core.composite.ResourceProvider())
  : AutoLuaEngine,AutoCloseable,CodeProvider by codeProvider,
                          ResourceProvider by resourceProvider,
  Observable<State> by ObservableImp() {

  private val messageObserver = com.autolua.engine.core.composite.MessageObserver()
  private var nativePtr:Long
  private val display:Display = DisplayImplement()
  /***
   *  engine 相关的方法
   */

  private external fun createNative(display:Display):Long
  private external fun releaseNative(ptr:Long)
  private external fun getState(ptr:Long,target:Int):Int
  init {
    nativePtr = createNative(display)
  }
  companion object {
    init {
      System.loadLibrary("engine")
    }
    private const val TAG = "AutoLuaEngineOnLocal"
    external fun changeLogChannel(channel: Int)
//    fun debuggerConfigure2RemoteServerConfigure(configure: DebuggerConfigure):RemoteServerConfigure{
//      val remoteServerConfigure = RemoteServerConfigure("debugger",configure.port)
//      remoteServerConfigure.auth = configure.auth
//      remoteServerConfigure.host = configure.host
//      remoteServerConfigure.services = RemoteServerConfigure.OBSERVER or
//              RemoteServerConfigure.CODE_PROVIDER or
//              RemoteServerConfigure.RESOURCE_PROVIDER or
//              RemoteServerConfigure.CONTROLLER
//      remoteServerConfigure.rpcServices.addAll(configure.rpcServices)
//      return remoteServerConfigure
//    }
  }
  private fun onStateChanged(state:Int,target:Int){
    Log.d(TAG,"onStateChanged  ${AutoLuaEngine.Target.fromInt(target)}  ${State.fromInt(state)}")
    notifyObservers(State.fromInt(state),target)
  }
  private external fun start(ptr:Long):Int
  private external fun setRootDir(ptr:Long,rootDir:String)
  override fun setRootDir(rootDir: String) {
    setRootDir(nativePtr,rootDir)
  }

  override fun start(callback: Callback?) {
    if(start(nativePtr) == 0){
      callback?.invoke(ResultCode.SUCCESS)
    }else{
      callback?.invoke(ResultCode.RUNNING)
    }
  }
  private external fun stopNative(ptr:Long)
  override fun stop() {
    stopNative(nativePtr)
  }
  private external fun waitForStop(ptr:Long)
  fun waitForStop(){
    waitForStop(nativePtr)
  }

  override fun close() {
    synchronized(this){
      if(nativePtr == 0L)
        return
      val ptr = nativePtr
      nativePtr = 0L
      releaseNative(ptr)
    }
  }

  override fun getState(target:AutoLuaEngine.Target): State {
    return State.fromInt(getState(nativePtr, target.value))
  }

  override fun attach(messageObserver: MessageObserver) {
    this.messageObserver.attach(messageObserver)
  }
  override fun detach(messageObserver: MessageObserver) {
    this.messageObserver.detach(messageObserver)
  }
  private fun onWarning(message:String){
    messageObserver.onWarning(message)
  }
  private fun onError(message:String){
    messageObserver.onError(message)
  }

  private external fun startFatherService(ptr:Long,configure: RemoteServerConfigure)
  fun startFatherService(configure: RemoteServerConfigure){
    startFatherService(nativePtr,configure)
  }

  /**
   * lua 环境配置
   */
  private external fun addRemoteServiceNative(ptr:Long,remoteServerConfigure:RemoteServerConfigure)
  override fun addRemoteService(remoteServerConfigure: RemoteServerConfigure) {
    addRemoteServiceNative(nativePtr,remoteServerConfigure)
  }
  private external fun removeRemoteServiceNative(ptr:Long,name:String)
  override fun removeRemoteService(name: String) {
    removeRemoteServiceNative(nativePtr,name)
  }
  private val environment = mutableListOf<Environment<*>>()
  override fun setEnvironment(environment: List<Environment<*>>) {
    synchronized(environment){
      this.environment.clear()
      this.environment.addAll(environment)
    }
  }
  private val localServices = mutableListOf<LocalService<*>>()
  override fun setLocalServices(services: List<LocalService<*>>) {
    synchronized(localServices){
      this.localServices.clear()
      this.localServices.addAll(services)
    }
  }
  override fun addCodeProvider(codeProvider: CodeProvider) {
    this.codeProvider.addProvider(codeProvider)
  }

  override fun clearCodeProvider() {
    this.codeProvider.clear()
  }

  override fun removeCodeProvider(codeProvider: CodeProvider) {
    this.codeProvider.removeProvider(codeProvider)
  }
  override fun addResourceProvider(resourceProvider: ResourceProvider) {
    this.resourceProvider.addProvider(resourceProvider)
  }

  override fun clearResourceProvider() {
    this.resourceProvider.clear()
  }

  override fun removeResourceProvider(resourceProvider:ResourceProvider) {
    this.resourceProvider.removeProvider(resourceProvider)
  }

  /**
   * push environment to lua context
   */
  private fun pushEnvironment(luaContext: LuaContext){
    synchronized(environment){
      environment.forEach {
        when(it.type){
          Environment.Type.STRING ->{
            luaContext.push(it.value as String)
            luaContext.setGlobal(it.key)
          }
          Environment.Type.LONG -> {
            luaContext.push(it.value as Long)
            luaContext.setGlobal(it.key)
          }
          Environment.Type.DOUBLE -> {
            luaContext.push(it.value as Double)
            luaContext.setGlobal(it.key)
          }
          Environment.Type.BOOLEAN -> {
            luaContext.push(it.value as Boolean)
            luaContext.setGlobal(it.key)
          }
          Environment.Type.BYTE_ARRAY -> {
            luaContext.push(it.value as ByteArray)
            luaContext.setGlobal(it.key)
          }
          Environment.Type.INT -> {
            luaContext.push((it.value as Int).toLong())
            luaContext.setGlobal(it.key)
          }
          Environment.Type.FLOAT -> {
            luaContext.push((it.value as Float).toDouble())
            luaContext.setGlobal(it.key)
          }
        }
      }
    }
  }
  /**
   * push local services to lua context
   */
  private fun newLocalServiceObj(L:LuaContext,clazz:Class<*>):Any?{
    try{
      val c = clazz.getConstructor(L::class.java)
      return c.newInstance(L)
    }catch (e:Exception){
    }
    try{
      val c = clazz.getConstructor()
      return c.newInstance()
    }catch (e:Exception){}
    return null
  }
  private val methodCache = MethodCache()
  private fun wrapLocalService(L:LuaContext, service:LocalService<*>): LuaObjectAdapter? {
    var obj = service.service
    if(service.type == LocalService.Type.CLASS){
      obj = newLocalServiceObj(L, service.mClass!!)
    }
    if(obj != null && obj !is LuaObjectAdapter){
      obj = JavaObjectWrapper(service.mInterface!!,obj,methodCache)
    }
    return obj as LuaObjectAdapter?
  }
  private fun pushLocalServices(luaContext: LuaContext){
    synchronized(localServices){
      localServices.forEach {
        val obj = wrapLocalService(luaContext,it)
        if(obj != null){
          luaContext.push(obj)
          luaContext.setGlobal(it.name)
        }else{
          onError("Failed to wrap local service ${it.name}")
        }
      }
    }
  }

  /**
   * init lua context
   */
  private val objectCache = ObjectCacheImp()
  private val contextCache = LongSparseArray<LuaContext>()
  private fun newLuaContext():Long{
    val luaContext = LuaContextImplement(objectCache)
    contextCache.put(luaContext.getNativeLua() ,luaContext)
    pushEnvironment(luaContext)
    pushLocalServices(luaContext)
    return luaContext.getNativeLua()
  }
  private fun releaseContext(ptr:Long){
    val l = contextCache.get(ptr)
    if(l != null){
      contextCache.remove(ptr)
      l.destroy()
    }
  }

  /***
   * worker 相关的方法
   */
  private external fun executeNative(ptr:Long, script:ByteArray,codeType:Int,flags:Int):Int
  override fun execute(script: String,flags:Int): Int {
    return executeNative(nativePtr,script.toByteArray(),LuaContext.CodeMode.TEXT.value,flags)
  }

  override fun execute(
    script: ByteArray,
    codeMode: LuaContext.CodeMode,
    flags: Int
  ): Int {
    return executeNative(nativePtr,script,codeMode.value,flags)
  }
  private external fun interruptNative(ptr:Long)
  override fun interrupt() {
    interruptNative(nativePtr)
  }


  /***
   * debugger 相关的方法
   */
  /***
   * jni 调用
   */

  private external fun startDebugService(ptr: Long,configure: RemoteServerConfigure)
  override fun startDebugger(debuggerConfigure:DebuggerConfigure) {
    startDebugService(nativePtr, Utils.debuggerConfigure2RemoteServerConfigure(debuggerConfigure))
  }

  private external fun stopDebugService(ptr:Long)
  override fun stopDebugger() {
    stopDebugService(nativePtr)
  }
}