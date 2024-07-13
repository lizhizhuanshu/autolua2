package com.autolua.autolua2.view

interface FloatController {
  enum class State{
    IDLE,
    RUNNING
  }
  fun isShowing():Boolean
  fun show()
  fun reShow()
  fun hide()
  fun setPosition(x: Int, y: Int)
  fun updateState(state: State)
  fun setClickListener(listener: (state:State) -> Unit)
}