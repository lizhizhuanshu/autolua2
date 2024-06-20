package com.autolua.engine.base

import android.util.Log


class JavaObjectWrapper(
  private val aClass: Class<*>,
  private val obj: Any,
  private val methodCache: MethodCache
) : LuaObjectAdapter {
  override fun hasMethod(name: String): Boolean {
    return methodCache.findMethod(aClass, name) != null
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
        val type = types[i]
        if (type == Int::class.javaPrimitiveType) {
          args[i] = L.toLong(i + 2).toInt()
        } else if (type == Long::class.javaPrimitiveType) {
          args[i] = L.toLong(i + 2)
        } else if (type == Float::class.javaPrimitiveType) {
          args[i] = L.toDouble(i + 2).toFloat()
        } else if (type == Double::class.javaPrimitiveType) {
          args[i] = L.toDouble(i + 2)
        } else if (type == String::class.java) {
          args[i] = L.toString(i + 2)
        } else if (type == Boolean::class.javaPrimitiveType) {
          args[i] = L.toBoolean(i + 2)
        } else if (type == ByteArray::class.java) {
          args[i] = L.toBytes(i + 2)
        } else {
          throw IllegalArgumentException("Unsupported type:$type")
        }
      }
    }
    val result = method.invoke(obj, *args) ?: return 0
    val resultType = method.returnType
    if (resultType == Int::class.javaPrimitiveType) {
      L.push((result as Int).toLong())
    } else if (resultType == Long::class.javaPrimitiveType) {
      L.push(result as Long)
    } else if (resultType == Float::class.javaPrimitiveType) {
      L.push((result as Float).toDouble())
    } else if (resultType == Double::class.javaPrimitiveType) {
      L.push(result as Double)
    } else if (resultType == Boolean::class.javaPrimitiveType) {
      L.push(result as Boolean)
    } else if (resultType == String::class.java) {
      L.push(result as String)
    } else if (resultType == ByteArray::class.java) {
      L.push(result as ByteArray)
    } else {
      throw IllegalArgumentException("Unsupported result type:$resultType")
    }
    return 1
  }

  @Throws(Throwable::class)
  override fun release(L: LuaContext) {
    Log.e("JavaObjectWrapper", "release")
    if (obj is Releasable) {
      obj.onRelease()
    }
  }
}
