package com.autolua.engine.extension.display

import java.nio.ByteBuffer

class EmptyDisplay:Display {
  override fun getBaseWidth(): Int {
    return 0
  }

  override fun getBaseHeight(): Int {
    return 0
  }

  override fun getBaseDirection(): Int {
    return 0
  }

  override fun getRotation(): Int {
    return 0
  }

  override fun getBaseDensity(): Int {
    return 0
  }

  override fun destroy() {
    return
  }

  override fun getDirection(): Int {
    return 1
  }

  override fun isChangeDirection(): Boolean {
    return false
  }

  override fun update() {
  }

  override fun getDisplayBuffer(): ByteBuffer {
    return ByteBuffer.allocate(0)
  }

  override fun getWidth(): Int {
    return 0
  }

  override fun getHeight(): Int {
    return 0
  }

  override fun getRowStride(): Int {
    return 0
  }

  override fun getPixelStride(): Int {
    return 0
  }

  override fun initialize(width: Int, height: Int): Boolean {
    return true
  }
}