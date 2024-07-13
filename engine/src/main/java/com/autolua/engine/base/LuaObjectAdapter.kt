package com.autolua.engine.base


interface LuaObjectAdapter {
  @Throws(Throwable::class)
  fun index(L:LuaContext): Int
  fun newIndex(L:LuaContext): Int
  @Throws(Throwable::class)
  fun call(L: LuaContext, name: String): Int
  fun invoke(L:LuaContext):Int
  @Throws(Throwable::class)
  fun release(L: LuaContext)
}
