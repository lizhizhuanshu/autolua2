package com.autolua.autolua2.mln.ud

import com.autolua.autolua2.extension.imp.UserInterfaceImp
import com.immomo.mls.annotation.LuaBridge
import com.immomo.mls.annotation.LuaClass
import org.luaj.vm2.LuaValue

@LuaClass(gcByLua = false)
class UI {

  private fun luaValue2Any(v:LuaValue): Any? {
    return when(v.type()){
      LuaValue.LUA_TNONE -> null
      LuaValue.LUA_TNIL -> null
      LuaValue.LUA_TBOOLEAN -> v.toBoolean()
      LuaValue.LUA_TNUMBER -> v.toDouble()
      LuaValue.LUA_TSTRING -> v.toJavaString()
      LuaValue.LUA_TTABLE -> {
        val map = mutableMapOf<Any, Any?>()
        val t = v.toLuaTable()
        for (kv in t) {
          map[luaValue2Any(kv.key)!!] = luaValue2Any(kv.value)
        }
        map
      }
      else -> v
    }
  }


  @LuaBridge
  fun putSignal(signal: String, data:LuaValue) {
    UserInterfaceImp.instance.putSignal(signal, luaValue2Any(data))
  }

  companion object {
    const val LUA_CLASS_NAME = "UI"
  }
}