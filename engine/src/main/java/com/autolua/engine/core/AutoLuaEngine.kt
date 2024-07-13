package com.autolua.engine.core

import android.os.Parcelable
import androidx.versionedparcelable.ParcelField
import com.autolua.engine.base.LuaContext
import com.autolua.engine.common.Observable
import com.autolua.engine.extension.display.Display
import com.autolua.engine.extension.input.InputManager
import com.autolua.engine.extension.node.UiAutomator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

interface AutoLuaEngine: Observable<AutoLuaEngine.State> {
  /**
   * engine 相关的方法
   */
  enum class ResultCode(val value: Int){
    SUCCESS(0),
    RUNNING(1),
    SU_FAIL(2),
    OTHER_ERROR(3);
    companion object {
      fun fromInt(value: Int) = entries.first { it.value == value }
    }
  }

  enum class Target(val value: Int){
    ENGINE(0),
    WORKER(1),
    DEBUGGER(2);
    companion object {
      fun fromInt(value: Int) = entries.first { it.value == value }
    }
  }

  fun setRootDir(rootDir:String)
  fun start(callback: Callback? = null)
  fun stop()
  fun getState(target:Target = Target.ENGINE): State
  fun destroy()


  /**
   * worker 相关的方法
   */
  fun execute(script:String,flags: Int = FLAG_NOT_RETAIN_LUA):Int
  fun execute(script:ByteArray,
              codeMode:LuaContext.CodeMode = LuaContext.CodeMode.TEXT_OR_BINARY,
              flags:Int = FLAG_NOT_RETAIN_LUA
  ):Int
  fun interrupt()


  /**
   * debugger 相关的方法
   */
  fun startDebugger(debuggerConfigure: DebuggerConfigure)
  fun stopDebugger()

  @Parcelize
  data class DebuggerConfigure(val port:Int,
                               var auth:String? = null,
                               var host:String? = null ,
                               var rpcServices:List<RPCServiceInfo> = listOf()) : Parcelable

  @Parcelize
  data class RPCServiceInfo(val name:String,
      val methods:MutableList<String> = mutableListOf()) : Parcelable
  @Parcelize
  data class RemoteServerConfigure(val name:String,
                                   val port:Int,
                                   var services:Int=0,
                                   var auth:String?=null,
                                   var host:String?=null,
                                   val rpcServices:MutableList<RPCServiceInfo> = mutableListOf()):Parcelable{
    companion object{
      const val CODE_PROVIDER =      0b00000001
      const val RESOURCE_PROVIDER =  0b00000010
      const val OBSERVER =           0b00000100
      const val CONTROLLER =         0b00001000
    }
  }

  data class LocalService<T>(val name:String, val type:Type=Type.OBJECT){
    var service:T? = null
    var mInterface:Class<*>? = null
    var mClass:Class<T>? = null
    constructor(name:String,service:T,thisInterface:Class<*>) :this(name,Type.OBJECT){
      this.service = service
      this.mInterface = thisInterface
    }

    enum class Type(val value:Int){
      OBJECT(0),
      CLASS(1);
      companion object {
        fun fromInt(value: Int) = entries.first { it.value == value }
      }
    }
  }

  @Parcelize
  data class Environment<T : Any>(val key:String,
                                  val value:@RawValue T,
                                  val type: Type = getType(value)):Parcelable{
    @Parcelize
    enum class Type(val value:Int) : Parcelable {
      STRING(0),
      LONG(1),
      DOUBLE(2),
      BOOLEAN(3),
      BYTE_ARRAY(4),
      INT(5),
      FLOAT(6);
      companion object {
        fun fromInt(value: Int) = entries.first { it.value == value }
      }
    }
    companion object{
      private fun getType(value:Any):Type{
        return when(value){
          is String -> Type.STRING
          is Long -> Type.LONG
          is Double -> Type.DOUBLE
          is Boolean -> Type.BOOLEAN
          is ByteArray -> Type.BYTE_ARRAY
          is Int -> Type.INT
          is Float -> Type.FLOAT
          else -> throw IllegalArgumentException("Unsupported type")
        }
      }
    }
  }

  @Parcelize
  enum class State(val value:Int):Parcelable{
    IDLE(0),
    STARTING(1),
    RUNNING(2),
    STOPPING(3);
//    PAUSING(4),
//    PAUSED(5);
    companion object {
      fun fromInt(value: Int) = entries.first { it.value == value }
    }
  }


  interface MessageObserver {
    fun onWarning(message:String)
    fun onError(message:String)
  }


  interface ResourceProvider {
    fun getResource(url:String) :ByteArray?
  }

  interface CodeProvider {
    data class Code(val type: LuaContext.CodeMode, val code:ByteArray) {
      val nType = type.value
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Code

        if (type != other.type) return false
        if (!code.contentEquals(other.code)) return false
        return true
      }

      override fun hashCode(): Int {
        var result = type.value
        result = 31 * result + code.contentHashCode()
        return result
      }
    }

    fun getModule(url:String) : Code?
    fun getFile(url:String) : Code?
  }

  companion object{
    const val FLAG_NONE = 0
    const val FLAG_NEW_LUA = 0b00000001
    const val FLAG_NOT_RETAIN_LUA = 0b00000010
  }

  interface Builder {
    fun addRemoteService(remoteServerConfigure: RemoteServerConfigure):Builder
    fun addLocalService(localService: LocalService<*>):Builder
    fun addLocalService(name:String,service:Any,thisInterface:Class<*>):Builder
    fun addLocalService(name:String,serviceClass:Class<*>,thisInterface:Class<*>):Builder
    fun addEnvironment(key:String,value:Any):Builder
    fun addCodeProvider(codeProvider: CodeProvider):Builder
    fun addResourceProvider(resourceProvider: ResourceProvider):Builder
    fun setDisplay(display: Display):Builder
    fun setInputManager(inputManager: InputManager):Builder
    fun setUiAutomator(uiAutomator: UiAutomator):Builder
    fun isRoot(isRoot:Boolean):Builder
    fun build():AutoLuaEngine?
  }
}

typealias Callback = (AutoLuaEngine.ResultCode)->Unit