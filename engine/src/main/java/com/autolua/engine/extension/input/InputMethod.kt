package com.autolua.engine.extension.input

interface InputMethod {
  fun isShowing(): Boolean
  fun input(text:String,position:Int):Boolean
  fun clear():Boolean
}