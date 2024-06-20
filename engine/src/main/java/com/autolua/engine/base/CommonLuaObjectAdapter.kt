package com.autolua.engine.base

open class CommonLuaObjectAdapter : LuaObjectAdapter {
  private val aClass: Class<*> = this.javaClass
  override fun hasMethod(name: String): Boolean {
    try {
      aClass.getMethod(name, LuaContext::class.java)
      return true
    } catch (e: NoSuchMethodException) {
      return false
    }
  }

  @Throws(Throwable::class)
  override fun call(L: LuaContext, name: String): Int {
    val method = aClass.getMethod(name, LuaContext::class.java)
    return method.invoke(this, L) as Int
  }

  @Throws(Throwable::class)
  override fun release(L: LuaContext) {
  }



}
