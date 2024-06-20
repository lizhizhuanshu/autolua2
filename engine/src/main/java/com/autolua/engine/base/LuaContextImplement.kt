package com.autolua.engine.base

import android.util.Log
import com.autolua.engine.base.LuaContext.CodeMode
import com.autolua.engine.base.LuaContext.ValueType
import com.autolua.engine.base.LuaContext.ValueType.Companion.valueOf


class LuaContextImplement(private val objectCache:ObjectCache) : LuaContext {
  private var nativeLua: Long = 0

  fun getNativeLua(): Long {
    return nativeLua
  }


  private external fun createLuaContext(): Long

  init {
    nativeLua = createLuaContext()
  }


  //适配器相关的操作

  fun hasMethod(id: Long, name: String): Boolean {
    val adapter = objectCache.get(id) as LuaObjectAdapter
    return adapter.hasMethod(name)
  }

  fun callMethod(id: Long, name: String): Int {
    val adapter = objectCache.get(id) as LuaObjectAdapter
    return adapter.call(this, name)
  }

  fun release(id: Long) {
    val adapter = objectCache.remove(id)
    if (adapter != null && adapter is LuaObjectAdapter)
        adapter.release(this)
    val str = adapter?.javaClass?.name ?: "null"
    Log.e(TAG, "release lua object adapter failed $str")
  }

  override fun destroy() {
    if(nativeLua>0){
      destroy(nativeLua)
      nativeLua = 0
    }
  }


  override fun toPointer(index: Int): Long {
    return toPointer(nativeLua, index)
  }

  override fun toLong(index: Int): Long {
    return toInteger(nativeLua, index)
  }

  override fun toDouble(index: Int): Double {
    return toNumber(nativeLua, index)
  }

  override fun toString(index: Int): String? {
    return toString(nativeLua, index)
  }


  override fun toBytes(index: Int): ByteArray? {
    return toBytes(nativeLua, index)
  }


  override fun toBoolean(index: Int): Boolean {
    return toBoolean(nativeLua, index)
  }

  override fun toLuaObjectAdapter(index: Int): LuaObjectAdapter? {
    val id = toLuaObjectAdapter(nativeLua, index)
    if (id>0)
      return objectCache.get(id) as LuaObjectAdapter
    return null
  }


  override fun push(v: Long) {
    push(nativeLua, v)
  }


  override fun push(v: Double) {
    push(nativeLua, v)
  }


  override fun push(v: Boolean) {
    push(nativeLua, v)
  }

  override fun push(v: LuaObjectAdapter?) {
    val id = objectCache.put(v!!)
    if(id<=0){
      objectCache.remove(id)
      throw LuaError("push lua object adapter failed")
    }
    pushLuaObjectAdapter(
      nativeLua, id)
  }


  override fun push(v: ByteArray?) {
    push(nativeLua, v)
  }


  override fun push(v: String?) {
    if (v != null) push(nativeLua, v.toByteArray())
    else pushNil()
  }


  override fun pushNil() {
    pushNil(nativeLua)
  }

  override fun pushValue(index: Int) {
    pushValue(nativeLua, index)
  }


  override fun loadBuffer(code: ByteArray?, chunkName: String?, mode: CodeMode?) {
    loadBuffer(nativeLua, code, chunkName, mode!!.value)
  }

  override fun pcall(nArgs: Int, nResults: Int, errFunc: Int): Int {
    return pcall(nativeLua, nArgs, nResults, errFunc)
  }


  override fun loadFile(filePath: String?, mode: CodeMode?) {
    loadFile(nativeLua, filePath, mode!!.value)
  }


  override fun type(index: Int): ValueType {
    return valueOf(type(nativeLua, index))
  }


  override fun isInteger(index: Int): Boolean {
    return isInteger(nativeLua, index)
  }


  override fun createTable(arraySize: Int, dictionarySize: Int) {
    createTable(nativeLua, arraySize, dictionarySize)
  }


  override fun pop(n: Int) {
    pop(nativeLua, n)
  }


  override var top: Int
    get() = getTop(nativeLua)
    set(index) {
      setTop(nativeLua, index)
    }


  override fun setTable(tableIndex: Int) {
    setTable(nativeLua, tableIndex)
  }

  override fun getGlobal(key: String?): Int {
    return getGlobal(nativeLua, key)
  }

  override fun setGlobal(key: String?) {
    setGlobal(nativeLua, key)
  }

  override fun rawGet(tableIndex: Int): Int {
    return rawGet(nativeLua, tableIndex)
  }

  override fun rawSet(tableIndex: Int) {
    rawSet(nativeLua, tableIndex)
  }

  override fun getTable(tableIndex: Int): Int {
    return getTable(nativeLua, tableIndex)
  }

  companion object {
    private const val TAG = "LuaContext"

    init {
      System.loadLibrary("engine_base")
    }


    external fun toPointer(nativeLua: Long, index: Int): Long
    @Throws(LuaTypeError::class)
    external fun toInteger(nativeLua: Long, index: Int): Long
    @Throws(LuaTypeError::class)
    external fun toNumber(nativeLua: Long, index: Int): Double
    @Throws(LuaTypeError::class)
    external fun toBytes(nativeLua: Long, index: Int): ByteArray?
    @Throws(LuaTypeError::class)
    external fun toString(nativeLua: Long, index: Int): String?
    external fun toBoolean(nativeLua: Long, index: Int): Boolean
    external fun push(nativeLua: Long, v: Long)
    external fun push(nativeLua: Long, v: Double)
    external fun push(nativeLua: Long, v: ByteArray?)
    external fun push(nativeLua: Long, v: Boolean)
    external fun pushNil(nativeLua: Long)


    external fun pushValue(nativeLua: Long, index: Int)
    @JvmStatic
    external fun pushLuaObjectAdapter(
      nativeLua: Long,
      adapterId: Long)

    external fun toLuaObjectAdapter(nativeLua: Long, index: Int): Long


    external fun loadBuffer(nativeLua: Long, code: ByteArray?, chunkName: String?, codeType: Int)
    external fun loadFile(nativeLua: Long, fileName: String?, codeType: Int)
    external fun pcall(nativeLua: Long, nArgs: Int, nResults: Int, errFunc: Int): Int

    external fun type(nativeLua: Long, index: Int): Int
    external fun isInteger(nativeLua: Long, index: Int): Boolean

    external fun getTop(nativeLua: Long): Int
    external fun setTop(nativeLua: Long, index: Int)
    external fun setTable(nativeLua: Long, tableIndex: Int)
    external fun getTable(nativeLua: Long, tableIndex: Int): Int
    external fun rawSet(nativeLua: Long, tableIndex: Int)
    external fun rawGet(nativeLua: Long, tableIndex: Int): Int
    @JvmStatic
    external fun setGlobal(nativeLua: Long, key: String?)
    external fun getGlobal(nativeLua: Long, key: String?): Int
    external fun pop(nativeLua: Long, n: Int)
    external fun createTable(nativeLua: Long, arraySize: Int, dictionarySize: Int)

    external fun destroy(nativeLua: Long)
  }
}
