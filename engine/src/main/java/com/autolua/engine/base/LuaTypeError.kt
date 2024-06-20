package com.autolua.engine.base


class LuaTypeError : LuaError {
  constructor() : super()

  constructor(msg: String?) : super(msg)
}