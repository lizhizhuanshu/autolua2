package com.autolua.engine.common


interface Observer<T> {
  fun onUpdate(data: T,flags: Int = 0)
}

