package com.autolua.engine.extension.input

interface InputManager {

  data class PointerState(
    var id:Int
  ){
    var x:Float? = null
    var y:Float? = null
    var pressure:Float? = null
    var size:Float? = null
    var major:Float? = null
    var minor:Float? = null
  }

  fun syncPointer(state:PointerState):Boolean
  fun releasePointer(pointerId:Int):Boolean


  fun keyDown(keyCode:Int):Boolean
  fun keyUp(keyCode:Int):Boolean

  fun releaseAllDown()

}