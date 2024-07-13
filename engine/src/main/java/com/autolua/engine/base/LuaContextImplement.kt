package com.autolua.engine.base

import android.util.Log
import com.autolua.engine.base.LuaContext.CodeMode
import com.autolua.engine.base.LuaContext.ValueType
import com.autolua.engine.base.LuaContext.ValueType.Companion.valueOf
import com.autolua.engine.common.Utils
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType


class LuaContextImplement(private val objectCache:ObjectCache,
                          private val methodCache: MethodCache = MethodCache())
  : LuaContext {
  private var nativeLua: Long = 0

  fun getNativeLua(): Long {
    return nativeLua
  }


  private external fun createLuaContext(): Long

  init {
    nativeLua = createLuaContext()
  }


  //适配器相关的操作

  fun indexMethod(id: Long): Int {
    val adapter = objectCache.get(id) as LuaObjectAdapter
    return adapter.index(this)
  }

  fun invokeMethod(id: Long): Int {
    val adapter = objectCache.get(id) as LuaObjectAdapter
    return adapter.invoke(this)
  }

  fun newIndexMethod(id: Long): Int {
    val adapter = objectCache.get(id) as LuaObjectAdapter
    return adapter.newIndex(this)
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
    Utils.log(TAG, "release lua object adapter failed $str")
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

  override fun toValue(index: Int, valueClass: Class<*>): Any? {
    return when(valueClass){
      Long::class.javaPrimitiveType -> toLong(index)
      Double::class.javaPrimitiveType -> toDouble(index)
      String::class.java -> toString(index)
      ByteArray::class.java ->  toBytes(index)
      Boolean::class.javaPrimitiveType -> toBoolean(index)
      Int::class.javaPrimitiveType ->  toLong(index).toInt()
      Float::class.javaPrimitiveType ->  toDouble(index).toFloat()
      Unit::class.java -> null
      else -> {
        val indexType = type(index)
        if(valueClass.isEnum){
          val n = toLong(index)
          val values = valueClass.enumConstants as Array<Enum<*>>
          if(n<0 || n>=values.size) throw LuaTypeError("can not convert to ${valueClass.name}")
          values[n.toInt()]
        }else if (indexType == ValueType.NONE || indexType == ValueType.NIL) null
        else if(indexType == ValueType.USERDATA){
          val adapter = toLuaObjectAdapter(index)
            ?: throw LuaTypeError("can not convert to ${valueClass.name}")
          val obj = if(JavaObjectWrapper::class.java.isInstance(adapter))
            (adapter as JavaObjectWrapper).obj
          else
            adapter
          if(valueClass.isInstance(obj))
            return obj
          else
            throw LuaTypeError("can not convert to ${valueClass.name}")
        }
        else if(indexType == ValueType.TABLE) {
          table2JavaObj(index,valueClass)
        }
        else throw LuaTypeError("can not convert to ${valueClass.name}")
      }
    }
  }

  private fun table2JavaObj(tableIndex:Int, type: Class<*>):Any{
    if(type.isArray){
      val childType = type.componentType!!
      val size = len(nativeLua, tableIndex)
      val array = java.lang.reflect.Array.newInstance(childType,size.toInt())
      for(i in 1..size){
        push(i)
        getTable(tableIndex)
        val value = toValue(getTop(nativeLua),childType)
        java.lang.reflect.Array.set(array,i.toInt()-1,value)
        pop(1)
      }
      return array
    }else{
      val constructor = type.getDeclaredConstructor()
      constructor.isAccessible = true
      val obj = constructor.newInstance()
      pushNil()
      while (next(nativeLua,tableIndex)){
        if(type(-2) == ValueType.STRING){
          val key = toString(-2)!!
          val field = type.getField(key)
          field.isAccessible = true
          val value = toValue(getTop(nativeLua),field.type)
          field.set(obj,value)
        }
        pop(1)
      }
      return obj
    }
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

  override fun push(v: Function<*>) {
    push(KotlinFunctionWrapper(v))
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


  private fun pushArray(array:Any,arrayClass:Class<*>){
    val size = java.lang.reflect.Array.getLength(array)
    createTable(size,0)
    val childType = arrayClass.componentType!!
    for(i in 0 until size){
      push(i+1L)
      pushValue(java.lang.reflect.Array.get(array,i),childType)
      setTable(-3)
    }
  }

  private fun pushData(data:Any,dataClass:Class<*>) {
    if(dataClass.isArray){
      pushArray(data,dataClass)
    }else{
      val fields = dataClass.declaredFields
      createTable(0,fields.size)
      for(field in fields){
        field.isAccessible = true
        push(field.name)
        pushValue(field.get(data),field.type)
        setTable(-3)
      }
    }
  }

  private fun pushEnumClass(enumClass:Class<*>){
    val values = enumClass.enumConstants as Array<Enum<*>>
    createTable(0,values.size)
    for(value in values){
      push(value.name)
      push(value.ordinal.toLong())
      setTable(-3)
    }
  }

  override fun pushValue(value: Any?, valueClass: Class<*>) {
    if (value == null) {
      pushNil()
      return
    }
    var valueType = valueClass
    if (valueClass == java.lang.Object::class.java){
      valueType = value.javaClass
    }
    if(valueType.isPrimitive){
      when(valueType){
        Long::class.javaPrimitiveType -> push(value as Long)
        Double::class.javaPrimitiveType -> push(value as Double)
        Boolean::class.javaPrimitiveType -> push(value as Boolean)
        Int::class.javaPrimitiveType -> push((value as Int).toLong())
        Float::class.javaPrimitiveType -> push((value as Float).toDouble())
        Short::class.javaPrimitiveType -> push((value as Short).toLong())
        else -> throw LuaTypeError("can not convert to ${valueType.name}")
      }
      Log.d(TAG, "pushPrimitiveValue: ${value.javaClass.name}  , ${valueType.name}  ")
      return
    }
    when (valueType) {
      java.lang.Integer::class.java -> push((value as Int).toLong())
      java.lang.Long::class.java -> push(value as Long)
      java.lang.Double::class.java -> push(value as Double)
      java.lang.Boolean::class.java -> push(value as Boolean)
      java.lang.Float::class.java -> push((value as Float).toDouble())
      java.lang.Short::class.java -> push((value as Short).toLong())
      String::class.java -> push(value as String)
      ByteArray::class.java -> push(value as ByteArray)
      Class::class.java ->{
        val nValue = value as Class<*>
        if(nValue.isEnum){
          pushEnumClass(nValue)
        }else{
          push(JavaObjectWrapper(nValue, value, methodCache))
        }
      }
      else -> {
        if(LuaObjectAdapter::class.java.isInstance(value)){
          push(value as LuaObjectAdapter)
        }else if(valueType.isInterface){
          push(JavaObjectWrapper(valueType, value, methodCache))
        }else if(valueType.methods.isEmpty()){
          pushData(value,valueType)
        }else if(valueType.isArray) {
          pushArray(value, valueType)
        }else{
          push(JavaObjectWrapper(valueType, value, methodCache))
        }
      }
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  override fun pushTable(value: Any) {
    val clazz = value::class
    createTable(0, clazz.memberProperties.size)
    for(property in clazz.memberProperties){
      val name = property.name
      val tValue = property.getter.call(value)
      push(name)
      pushValue(tValue,property.returnType.javaType as Class<*>)
      setTable(-3)
    }
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

    external fun next(nativeLua: Long, index: Int): Boolean
    external fun len(nativeLua: Long, index: Int): Long

  }
}
