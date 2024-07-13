package com.autolua.engine.extension.node

interface UiAutomator {
  fun init():Boolean
  fun getRootInActiveWindow():UiObject
  fun setText(text:String):Boolean
  fun destroy()
}