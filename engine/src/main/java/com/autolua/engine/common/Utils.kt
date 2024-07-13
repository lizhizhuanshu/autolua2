package com.autolua.engine.common


object Utils {
  inline fun <reified T : Enum<T>, reified U : Enum<U>> convertEnum(source: T): U = enumValueOf(source.name)
  fun log(tag: String, message: String) {
    if(inRootProcess) {
      println("$tag: $message")
    }else{
      android.util.Log.d(tag, message)
    }
  }
  var inRootProcess = false
}