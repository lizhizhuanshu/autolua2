package com.autolua.engine.base

import com.autolua.engine.common.Utils
import java.lang.reflect.Method

class KotlinFunctionWrapper(private val obj:Function<*>): LuaObjectAdapter{
  private val method = getInvokeMethod(obj)
  override fun index(L: LuaContext): Int {
    TODO("Not yet implemented")
  }

  override fun newIndex(L: LuaContext): Int {
    TODO("Not yet implemented")
  }

  override fun call(L: LuaContext, name: String): Int {
    TODO("Not yet implemented")
  }

  override fun invoke(L: LuaContext): Int {
    val types = method.parameterTypes
    val args = arrayOfNulls<Any>(types.size)
    for (i in types.indices){
      args[i] = L.toValue(i+2,types[i])
    }
    val result = method.invoke(obj,*args)
    val resultType = method.returnType
    Utils.log("KotlinFunction","invoke result:$result type:$resultType")
    L.pushValue(result,resultType)
    return 1
  }

  override fun release(L: LuaContext) {

  }

  companion object{
    private fun isRightInvoke(method:Method):Boolean{
      val types = method.parameterTypes
      if(types.isNotEmpty()){
        return types[0] != java.lang.Object::class.java
      }
      return true
    }
    private fun getInvokeMethod(obj:Function<*>):java.lang.reflect.Method{
      val methods = obj.javaClass.methods
      for (method in methods){
        if (method.name == "invoke" && isRightInvoke(method)){
          return method
        }
      }
      throw NoSuchMethodException("Method not found:invoke")
    }
  }

}