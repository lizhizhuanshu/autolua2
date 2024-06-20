package com.autolua.autolua2.base

object Configure {
  private const val defaultRootDir = "file:///android_asset/"
  @Volatile
  var rootDir = defaultRootDir
    set(value) = if(value == "") field = defaultRootDir else field = value

  val uiDir:String
    get() = rootDir + "ui/"
  val backendDir:String
    get() = rootDir + "src/"

  val resDir:String
    get() = rootDir + "res/"

}