package com.autolua.engine.base

interface ObjectCache {
  fun put(obj:Any):Long
  fun get(ptr:Long):Any?
  fun remove(ptr:Long):Any?
  fun clear()
}
