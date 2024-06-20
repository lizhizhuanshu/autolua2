package com.autolua.engine.core.root

import android.util.Log
import com.autolua.engine.base.Releasable
import com.autolua.engine.core.AutoLuaEngine
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.json.JSONArray
import org.json.JSONException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class ServiceManager {
  private val serviceMap: MutableMap<String, ServicePage> = HashMap()
  private val methodCache: MutableMap<String, Method> = HashMap()

  fun invoke(service:String,method:String,data:ByteArray):String {
    try {
      val page = serviceMap[service] ?: return "[1,\"Service not found\"]"
      val key = StringBuilder()
      key.append(service).append(":").append(method)
      var m = methodCache[key.toString()]
      if (null == m) {
        m = findMethod(page.interfaceClass, method)
        if (null == m) {
          return "[1,\"Method not found\"]"
        }
        methodCache[key.toString()] = m
      }

      var args: Array<Any?>? = null
      val types = m.parameterTypes
      if (types.isNotEmpty()) {
        args = arrayOfNulls(types.size)
//        Log.d("ServiceManager", "invoke: ${data.toString(Charsets.UTF_8)} ")
        val jsonArray = JsonParser.parseString(data.toString(Charsets.UTF_8)).asJsonArray
        for ((i, element) in jsonArray.withIndex()) {
          args[i] = Gson().fromJson(element, types[i])
        }
      }
      val result = m.invoke(page.instance, *(args as Array<Any>))?: return "[0,null]"
      return Gson().toJson(arrayOf(0, result))
    } catch (e: JSONException) {
      e.printStackTrace()
      return "[1,\"JSON error\"]"
    } catch (e: IllegalAccessException) {
      e.printStackTrace()
      return "[1,\"Invoke error\"]"
    } catch (e: InvocationTargetException) {
      e.printStackTrace()
      return "[1,\"Invoke error\"]"
    }
  }



  fun register(name: String, interfaceClass: Class<*>, instance: Any) {
    val page = ServicePage(name, interfaceClass, instance)
    serviceMap[name] = page
  }

  fun serviceList():List<AutoLuaEngine.RPCServiceInfo>{
    val list = mutableListOf<AutoLuaEngine.RPCServiceInfo>()
    for((name,page) in serviceMap){
      val methods = mutableListOf<String>()
      for(m in page.interfaceClass.methods){
        methods.add(m.name)
      }
      list.add(AutoLuaEngine.RPCServiceInfo(name,methods))
    }
    return list
  }
  private fun releaseOneService(releasable: Releasable){
    try{
      releasable.onRelease()
    }catch (e:Exception){
      e.printStackTrace()
    }
  }

  fun releaseService(){
    for ((_,value) in serviceMap){
      if(value.instance is Releasable){
        releaseOneService(value.instance as Releasable)
      }
    }
  }


  fun clear(){
    releaseService()
    serviceMap.clear()
    methodCache.clear()
  }

  private data class ServicePage (
    var name: String ,
    var interfaceClass: Class<*>,
    var instance: Any
  )

  companion object {
    private fun findMethod(clazz: Class<*>, name: String): Method? {
      for (m in clazz.methods) {
        if (m.name == name) {
          return m
        }
      }
      return null
    }
  }
}
