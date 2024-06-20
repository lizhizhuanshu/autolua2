package com.autolua.engine.base

import java.lang.reflect.Method

class MethodCache {
  private val methodMap: MutableMap<String, Method?> = HashMap()

  fun findMethod(clazz: Class<*>, name: String): Method? {
    val key = clazz.name + ":" + name
    var m = methodMap[key]
    if (null == m) {
      for (method in clazz.methods) {
        if (method.name == name) {
          m = method
          methodMap[key] = m
          break
        }
      }
    }
    return m
  }
}
