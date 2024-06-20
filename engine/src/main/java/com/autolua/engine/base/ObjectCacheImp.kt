package com.autolua.engine.base

import android.util.Log
import android.util.LongSparseArray
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ObjectCacheImp: ObjectCache {
  private val cache = LongSparseArray<Any>()
  private val readWriteLock = ReentrantReadWriteLock()
  private var nextId:Long = 1

  private fun nextId():Long{
    return nextId++
  }

  private fun rawPut(ptr:Long,obj:Any):Boolean{
    readWriteLock.write{
      if (cache.get(ptr) != null){
        return false
      }
      cache.put(ptr,obj)
      return true
    }
  }

  override fun put(obj: Any): Long {
    while (true){
      val id = nextId()
      if (rawPut(id,obj)){
        Log.e("ObjectCacheImp","put obj $obj to cache with id $id")
        return id
      }
    }
  }

  override fun get(ptr: Long): Any? {
    readWriteLock.read{
      return cache.get(ptr)
    }
  }

  override fun remove(ptr: Long): Any? {
    Log.e("ObjectCacheImp","remove obj from cache with id $ptr")
    readWriteLock.write {
      val r = cache.get(ptr)
      if(r!=null){
        cache.remove(ptr)
      }
      return r
    }
  }

  override fun clear() {
    readWriteLock.write {
      cache.clear()
    }
  }
}