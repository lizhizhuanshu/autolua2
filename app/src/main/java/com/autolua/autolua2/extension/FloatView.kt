package com.autolua.autolua2.extension

import com.autolua.engine.base.Releasable

interface FloatView :Releasable{
  fun show()
  fun hide()
  fun setPosition(x: Int, y: Int)
  fun setSize(width: Int, height: Int)
  fun setContent(url: String)

  fun isShowing():Boolean
  fun setAlpha(alpha:Float)
}