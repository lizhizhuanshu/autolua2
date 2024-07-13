package com.autolua.engine.extension.input

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import com.autolua.engine.base.LuaError
import com.autolua.engine.common.Utils
import com.autolua.engine.conceal.InputManagerWrap

class InputManagerImp private constructor(): InputManager{
  private val touchDevice:InputDevice = InputManagerWrap.getTouchDevice()
  private val keyDevice:InputDevice = InputManagerWrap.getKeyboardDevice()
  private val pointersState = PointersState()
  private val pointerProperties = arrayOfNulls<PointerProperties>(PointersState.MAX_POINTERS)
  private val pointerCoords = arrayOfNulls<PointerCoords>(PointersState.MAX_POINTERS)
  private var lastTouchDown: Long = 0
  private fun initPointers() {
    for (i in 0 until PointersState.MAX_POINTERS) {
      val props = PointerProperties()
      props.toolType = MotionEvent.TOOL_TYPE_FINGER
      val coords = PointerCoords()
      coords.orientation = 0f
      coords.size = 1f
      pointerProperties[i] = props
      pointerCoords[i] = coords
    }
  }

  companion object{
    val instance by lazy { InputManagerImp() }
  }

  init {
    initPointers()
    Utils.log("InputManagerImp","touch device id = ${touchDevice.id}" +
            " source = ${touchDevice.sources}")
  }

  private fun updateTouch(action: Int, pointerIndex: Int, buttons: Int): Boolean {
    var action = action
    val now = SystemClock.uptimeMillis()

    val pointerCount = pointersState.update(pointerProperties, pointerCoords)

    if (pointerCount == 1) {
      if (action == MotionEvent.ACTION_DOWN) {
        lastTouchDown = now
      }
    } else {
      // secondary pointers must use ACTION_POINTER_* ORed with the pointerIndex
      if (action == MotionEvent.ACTION_UP) {
        action =
          MotionEvent.ACTION_POINTER_UP or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
      } else if (action == MotionEvent.ACTION_DOWN) {
        action =
          MotionEvent.ACTION_POINTER_DOWN or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
      } else {
        println("action :$action")
      }
    }
    val event = MotionEvent
      .obtain(
        lastTouchDown,
        now,
        action,
        pointerCount,
        pointerProperties,
        pointerCoords,
        0,
        buttons,
        1f,
        1f,
        touchDevice.id,
        0,
        touchDevice.sources,
        0
      )
    Utils.log("InputManagerImp","injectInputEvent action = $action pointerIndex = $pointerIndex buttons = $buttons pointerCount = $pointerCount x = ${pointerCoords[0]?.x} y = ${pointerCoords[0]?.y}  major = ${pointerCoords[0]?.touchMajor} minor = ${pointerCoords[0]?.touchMinor} pressure = ${pointerCoords[0]?.pressure} size = ${pointerCoords[0]?.size}")
    return InputManagerWrap.injectInputEvent(event, 1)
  }


  override fun syncPointer(state: InputManager.PointerState):Boolean {
    var id = state.id
    if(id == -1){
      val pointer = pointersState.newPointer()
      id = pointer.localId
      state.id = id
    }
    val index = pointersState.getPointerIndex(id)
    if (index < 0) {
      throw LuaError("Pointer not found: $id")
    }
    val pointer = pointersState.get(id)!!
    Utils.log("InputManagerImp","syncPointer x = ${state.x} y = ${state.y} pressure = ${state.pressure} size = ${state.size} major = ${state.major} minor = ${state.minor}")
    if (state.x != null) {
      pointer.point.x = state.x!!
    }
    if (state.y != null) {
      pointer.point.y = state.y!!
    }
    if (state.pressure != null) {
      pointer.pressure = state.pressure!!
    }
    if (state.size != null) {
      pointer.size = state.size!!
    }
    if (state.major != null) {
      pointer.major = state.major!!
    }
    if (state.minor != null) {
      pointer.minor = state.minor!!
    }
    val action = if(pointer.isUp) MotionEvent.ACTION_DOWN else MotionEvent.ACTION_MOVE
    if(action == MotionEvent.ACTION_DOWN){
      if(state.x == null || state.y == null){
        throw LuaError("Pointer down must have x and y")
      }
    }
    pointer.isUp = false
    val r = updateTouch(action, index, 0)
    return r
  }

  override fun releasePointer(pointerId: Int):Boolean {
    val index = pointersState.getPointerIndex(pointerId)
    Utils.log("InputManagerImp","releasePointer index = $index")
    if (index < 0) {
      return false
    }
    val pointer = pointersState.get(index)!!
    pointer.isUp = true
    val r = updateTouch(MotionEvent.ACTION_UP, index, 0)
    return r
  }



  private val keysDown = mutableMapOf<Int,Long>()

  private fun newKeyEvent(action: Int,now:Long, downTime:Long, keyCode: Int): KeyEvent {

    return KeyEvent(downTime, now, action, keyCode, 0, 0, keyDevice.id, 0, 0, keyDevice.sources)
  }
  override fun keyDown(keyCode: Int):Boolean {
    if (keysDown.contains(keyCode)) {
      return false
    }
    val now = SystemClock.uptimeMillis()
    keysDown[keyCode] = now
    val event = newKeyEvent(KeyEvent.ACTION_DOWN, now, now, keyCode)
    return InputManagerWrap.injectInputEvent(event, 2)
  }

  override fun keyUp(keyCode: Int): Boolean {
    val downTime = keysDown[keyCode] ?: return false
    keysDown.remove(keyCode)
    val now = SystemClock.uptimeMillis()
    val event = newKeyEvent(KeyEvent.ACTION_UP, now, downTime, keyCode)
    return InputManagerWrap.injectInputEvent(event, 2)
  }



  private fun releaseAllTouch() {
    for (i in 0 until pointersState.size) {
      val pointer = pointersState.get(i) ?: continue
      if (pointer.isUp) {
        continue
      }
      pointer.isUp = true
      updateTouch(MotionEvent.ACTION_UP, i, 0)
    }
  }

  private fun releaseAllKeys() {
    for (keyCode in keysDown.keys) {
      keyUp(keyCode)
    }
    keysDown.clear()
  }

  override fun releaseAllDown() {
    releaseAllTouch()
    releaseAllKeys()
  }
}