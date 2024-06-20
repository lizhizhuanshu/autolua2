package com.autolua.engine.base


interface LuaObjectAdapter {
  @Throws(Throwable::class)
  fun hasMethod(name: String): Boolean

  @Throws(Throwable::class)
  fun call(L: LuaContext, name: String): Int

  @Throws(Throwable::class)
  fun release(L: LuaContext)
}
