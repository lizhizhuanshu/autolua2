package com.autolua.engine.core


import android.view.KeyEvent
import android.view.WindowManager
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
import com.autolua.engine.extension.display.EmptyDisplay
import com.autolua.engine.extension.input.EmptyInputManager
import com.autolua.engine.extension.input.InputManager
import com.autolua.engine.extension.node.UiAutomator
import com.autolua.engine.extension.node.UiSelector
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType


/**
 * luaContext 初始化步骤
 * 1. luaContext create
 * 2. push environment
 * 3. push local services
 * 4. push remote services proxy
 */
class AutoLuaEngineOnLocal (private val codeProvider: com.autolua.engine.core.composite.CodeProvider = com.autolua.engine.core.composite.CodeProvider(),
                            private val resourceProvider: com.autolua.engine.core.composite.ResourceProvider = com.autolua.engine.core.composite.ResourceProvider(),
  private val environment: List<Environment<*>> = listOf(),
  private val localServices: List<LocalService<*>> = listOf(),
  private val remoteServices: List<RemoteServerConfigure> = listOf(),
  display:Display = EmptyDisplay(),
  inputManager: InputManager = EmptyInputManager(),
  isRoot:Boolean = false,
  private val uiAutomator: UiAutomator? = null)
  : AutoLuaEngine,CodeProvider by codeProvider,
    ResourceProvider by resourceProvider,
    Observable<State> by ObservableImp()
{

  private val messageObserver = com.autolua.engine.core.composite.MessageObserver()
  private var nativePtr:Long
  /***
   *  engine 相关的方法
   */

  private external fun createNative(display:Display,inputManager: InputManager,isRoot:Boolean):Long
  private external fun releaseNative(ptr:Long)
  private external fun getState(ptr:Long,target:Int):Int
  init {
    nativePtr = createNative(display,inputManager,isRoot)
    for (remoteService in remoteServices){
      addRemoteServiceNative(nativePtr,remoteService)
    }
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
    com.autolua.engine.common.Utils.log(TAG,"onStateChanged  ${AutoLuaEngine.Target.fromInt(target)}  ${State.fromInt(state)}")
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

  override fun getState(target:AutoLuaEngine.Target): State {
    return State.fromInt(getState(nativePtr, target.value))
  }

  override fun destroy() {
    synchronized(this){
      if(nativePtr == 0L)
        return
      val ptr = nativePtr
      nativePtr = 0L
      releaseNative(ptr)
    }
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
  private external fun removeRemoteServiceNative(ptr:Long,name:String)

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
    try{
      return rawNewLuaContext()
    }catch (e:Exception){
      com.autolua.engine.common.Utils.log(TAG,"Failed to create lua context $e")
      return 0
    }
  }
  private object KeyCode {
    val HOME = KeyEvent.KEYCODE_HOME
    val BACK = KeyEvent.KEYCODE_BACK
    val MENU = KeyEvent.KEYCODE_MENU
    val VOLUME_UP = KeyEvent.KEYCODE_VOLUME_UP
    val VOLUME_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN
    val POWER = KeyEvent.KEYCODE_POWER
  }

  private object LayoutParamsFlag {
    val NOT_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    val NOT_TOUCHABLE = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    val NOT_TOUCH_MODAL = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
  }

  private fun rawNewLuaContext():Long{
    val luaContext = LuaContextImplement(objectCache)
    contextCache.put(luaContext.getNativeLua() ,luaContext)
    pushEnvironment(luaContext)
    pushLocalServices(luaContext)
    if(uiAutomator != null){
      luaContext.push {
        UiSelector()
      }
      luaContext.setGlobal("UiSelector")
      luaContext.push {
        uiAutomator.getRootInActiveWindow()
      }
      luaContext.setGlobal("getRootInActiveWindow")

      luaContext.push{it:String->
        uiAutomator.setText(it)
      }
      luaContext.setGlobal("setText")
    }
    luaContext.pushTable(KeyCode)
    luaContext.setGlobal("KeyCode")
    luaContext.pushTable(LayoutParamsFlag)
    luaContext.setGlobal("LayoutParamsFlag")


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

  class Builder :AutoLuaEngine.Builder{
    private val remoteServices = mutableListOf<RemoteServerConfigure>()
    override fun addRemoteService(remoteServerConfigure: RemoteServerConfigure): AutoLuaEngine.Builder {
      remoteServices.add(remoteServerConfigure)
      return this
    }

    private val localServices = mutableListOf<LocalService<*>>()
    override fun addLocalService(localService: LocalService<*>): AutoLuaEngine.Builder {
      localServices.add(localService)
      return this
    }

    override fun addLocalService(
      name: String,
      service: Any,
      thisInterface: Class<*>
    ): AutoLuaEngine.Builder {
      localServices.add(LocalService(name,service,thisInterface))
      return this
    }

    override fun addLocalService(
      name: String,
      serviceClass: Class<*>,
      thisInterface: Class<*>
    ): AutoLuaEngine.Builder {
      val localService = LocalService<Any>(name, LocalService.Type.CLASS)
      localService.mClass = serviceClass as Class<Any>
      localServices.add(localService)
      return this
    }

    private val environment = mutableListOf<Environment<*>>()
    override fun addEnvironment(key: String, value: Any): AutoLuaEngine.Builder {
      environment.add(Environment(key,value))
      return this
    }

    private val codeProvider = com.autolua.engine.core.composite.CodeProvider()
    override fun addCodeProvider(codeProvider: CodeProvider): AutoLuaEngine.Builder {
      this.codeProvider.addProvider(codeProvider)
      return this
    }

    private val resourceProvider = com.autolua.engine.core.composite.ResourceProvider()
    override fun addResourceProvider(resourceProvider: ResourceProvider): AutoLuaEngine.Builder {
      this.resourceProvider.addProvider(resourceProvider)
      return this
    }

    private var display: Display? = null
    override fun setDisplay(display: Display): AutoLuaEngine.Builder {
      this.display = display
      return this
    }

    private var inputManager: InputManager? = null
    override fun setInputManager(inputManager: InputManager): AutoLuaEngine.Builder {
      this.inputManager = inputManager
      return this
    }
    private var uiAutomator: UiAutomator? = null
    override fun setUiAutomator(uiAutomator: UiAutomator): AutoLuaEngine.Builder {
      this.uiAutomator = uiAutomator
      return this
    }

    private var isRoot = false
    override fun isRoot(isRoot: Boolean): AutoLuaEngine.Builder {
      this.isRoot = isRoot
      return this
    }

    override fun build(): AutoLuaEngine {
      return AutoLuaEngineOnLocal(codeProvider,resourceProvider,
        environment,localServices,
        remoteServices,
        display ?: EmptyDisplay(),
        inputManager ?: EmptyInputManager(),
        isRoot,uiAutomator)
    }

  }
}