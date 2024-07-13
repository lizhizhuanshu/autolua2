package com.autolua.engine.base

import android.util.Log
import com.autolua.engine.common.Utils


class JavaObjectWrapper(
  private val aClass: Class<*>,
  val obj: Any,
  private val methodCache: MethodCache
) : LuaObjectAdapter {
  override fun index(L:LuaContext): Int {
    val name = L.toString(2)
    return if(methodCache.findMethod(aClass, name!!) != null) 2 else 0
  }

  override fun newIndex(L: LuaContext): Int {
    val name = L.toString(2)
    val field = aClass.getField(name!!)
    val value = L.toValue(3, field.type)
    field.set(obj, value)
    return 0
  }

  companion object {
    private val nullArray = arrayOfNulls<Any>(0)
  }

  @Throws(Throwable::class)
  override fun call(L: LuaContext, name: String): Int {
    val method = methodCache.findMethod(aClass, name)
      ?: throw NoSuchMethodException("Method not found:$name")
    var args = nullArray
    val types = method.parameterTypes
    if (types.isNotEmpty()) {
      args = arrayOfNulls(types.size)
      for (i in types.indices) {
        args[i] = L.toValue(i + 2, types[i])
      }
    }
    val result = method.invoke(obj, *args)
    val resultType = method.returnType
    L.pushValue(result, resultType)
    return 1
  }

  override fun invoke(L: LuaContext): Int {
    TODO("Not yet implemented")
  }


  @Throws(Throwable::class)
  override fun release(L: LuaContext) {
    if (Releasable::class.java.isInstance(obj)) {
      (obj as Releasable).onRelease()
    }
  }
}
