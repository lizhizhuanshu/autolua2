package com.autolua.engine.base

import java.io.Serializable

open class LuaError : RuntimeException, Serializable {
  constructor() : super()

  constructor(message: String?) : super(message)

  constructor(throwable: Throwable?) : super(throwable)

  constructor(message: String?, throwable: Throwable?) : super(message, throwable)
}
