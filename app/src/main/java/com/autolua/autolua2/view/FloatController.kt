package com.autolua.autolua2.view

interface FloatController {
  enum class State{
    IDLE,
    RUNNING
  }
  fun show()
  fun reShow()
  fun hide()
  fun move(x: Int, y: Int)
  fun updateState(state: State)
  fun setClickListener(listener: (state:State) -> Unit)
}