package com.autolua.engine.common


object Utils {
  inline fun <reified T : Enum<T>, reified U : Enum<U>> convertEnum(source: T): U = enumValueOf(source.name)
}