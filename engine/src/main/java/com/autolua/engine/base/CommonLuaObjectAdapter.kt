package com.autolua.engine.base

open class CommonLuaObjectAdapter : LuaObjectAdapter {
  private val aClass: Class<*> = this.javaClass
  override fun index(L: LuaContext): Int {
    try {
      val name = L.toString(2)
      aClass.getMethod(name!!, LuaContext::class.java)
      return 2
    } catch (e: NoSuchMethodException) {
      return 0
    }
  }

  override fun newIndex(L: LuaContext): Int {
    val name = L.toString(2)
    val field = aClass.getField(name!!)
    val value = L.toValue(3, field.type)
    field.set(this, value)
    return 0
  }

  @Throws(Throwable::class)
  override fun call(L: LuaContext, name: String): Int {
    val method = aClass.getMethod(name, LuaContext::class.java)
    return method.invoke(this, L) as Int
  }

  override fun invoke(L: LuaContext): Int {
    TODO("Not yet implemented")
  }


  @Throws(Throwable::class)
  override fun release(L: LuaContext) {
  }



}
