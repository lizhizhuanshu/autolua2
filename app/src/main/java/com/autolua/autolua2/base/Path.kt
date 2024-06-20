package com.autolua.autolua2.base

object Path {
  private fun transport(dir:String,uri:String):String{
    if(uri.startsWith("http://") || uri.startsWith("https://")) return uri
    if(uri.startsWith("file:///")) return uri
    if(uri.startsWith("file://")) return dir + uri.substring("file://".length)
    if(uri.startsWith("/")) return uri
    return dir + uri
  }
  fun ui(uri:String):String{
    return transport(Configure.uiDir,uri)
  }

  fun backend(uri:String):String{
    return transport(Configure.backendDir,uri)
  }

}