package com.autolua.engine.base


interface LuaContext {

  fun destroy()

  fun toPointer(index: Int): Long

  @Throws(LuaTypeError::class)
  fun toLong(index: Int): Long

  @Throws(LuaTypeError::class)
  fun toDouble(index: Int): Double

  @Throws(LuaTypeError::class)
  fun toString(index: Int): String?

  @Throws(LuaTypeError::class)
  fun toBytes(index: Int): ByteArray?
  fun toBoolean(index: Int): Boolean
  fun toValue(index:Int,valueClass:Class<*>):Any?

  fun toLuaObjectAdapter(index: Int): LuaObjectAdapter?

  fun push(v: Long)
  fun push(v: Double)
  fun push(v: String?)
  fun push(v: ByteArray?)
  fun push(v: Boolean)

  fun push(v: LuaObjectAdapter?)
  fun push(v:Function<*>)

  fun pushNil()
  fun pushValue(index: Int)
  fun pushValue(value: Any?, valueClass: Class<*>)
  fun pushTable(value:Any)

  fun getTable(tableIndex: Int): Int
  fun setTable(tableIndex: Int)
  fun getGlobal(key: String?): Int
  fun setGlobal(key: String?)
  fun rawGet(tableIndex: Int): Int
  fun rawSet(tableIndex: Int)

  var top: Int
  fun pop(n: Int)
  fun type(index: Int): ValueType?
  fun isInteger(index: Int): Boolean
  fun loadFile(filePath: String?, mode: CodeMode?)
  fun loadBuffer(code: ByteArray?, chunkName: String?, mode: CodeMode?)
  fun pcall(nArgs: Int, nResults: Int, errFunc: Int): Int
  fun createTable(arraySize: Int, dictionarySize: Int)
  enum class CodeMode(val value: Int) {
    TEXT_OR_BINARY(0), TEXT(1), BINARY(2)
  }

  enum class ValueType(val code: Int) {
    NONE(-1),
    NIL(0),
    BOOLEAN(1),
    LIGHT_USERDATA(2),
    NUMBER(3),
    STRING(4),
    TABLE(5),
    FUNCTION(6),
    USERDATA(7),
    THREAD(8);

    companion object {
      @JvmStatic
      fun valueOf(code: Int): ValueType {
        when (code) {
          -1 -> return NONE
          0 -> return NIL
          1 -> return BOOLEAN
          2 -> return LIGHT_USERDATA
          3 -> return NUMBER
          4 -> return STRING
          5 -> return TABLE
          6 -> return FUNCTION
          7 -> return USERDATA
          8 -> return THREAD
        }
        throw RuntimeException(String.format("The code '%d' can't to type 'VALUE_TYPE'", code))
      }
    }
  }

  companion object {
    const val MAX_STACK: Int = 1000000
    const val REGISTRY_INDEX: Int = -1001000
    const val REGISTRY_INDEX_MAIN_THREAD: Int = 1
    const val REGISTRY_INDEX_GLOBALS: Int = 2
    const val MULTI_RESULT: Int = -1
  }
}
