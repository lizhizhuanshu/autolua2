package com.autolua.engine

import android.os.Parcel
import android.os.Parcelable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autolua.engine.core.AutoLuaEngine



import org.junit.Test


import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.javaType
import kotlin.reflect.jvm.reflect

@RunWith(AndroidJUnit4::class)
class TestParcel {

  @Test
  fun testParcel() {
    val parcel = Parcel.obtain()
    val debuggerConfigure = AutoLuaEngine.DebuggerConfigure(8080)
    debuggerConfigure.host = "localhost"
    parcel.writeValue(debuggerConfigure)
    parcel.setDataPosition(0)
    val debuggerConfigure2 = parcel.readValue(AutoLuaEngine.DebuggerConfigure::class.java.classLoader) as AutoLuaEngine.DebuggerConfigure
    assertEquals(debuggerConfigure.port, debuggerConfigure2.port)
    assertEquals(debuggerConfigure.host, debuggerConfigure2.host)
  }
  @Test
  fun testEnvironment(){
    val parcel = Parcel.obtain()
    val environment = AutoLuaEngine.Environment("test","other")
    parcel.writeValue(environment)
    parcel.setDataPosition(0)
    val environment2 = parcel.readValue(AutoLuaEngine.Environment::class.java.classLoader) as AutoLuaEngine.Environment<*>
    assertEquals(environment.key, environment2.key)
    assertEquals(environment.value, environment2.value)
    parcel.setDataPosition(0)
    val environment3 = AutoLuaEngine.Environment("test",1)
    parcel.writeValue(environment3)
    parcel.setDataPosition(0)
    val environment4 = parcel.readValue(AutoLuaEngine.Environment::class.java.classLoader) as AutoLuaEngine.Environment<*>
    assertEquals(environment3.key, environment4.key)
    assertEquals(environment3.value, environment4.value)
  }

  @Test
  fun testValue(){
    val v:Any? = 6
    val parcel = Parcel.obtain()
    parcel.writeValue(v)
    parcel.setDataPosition(0)
    val v2 = parcel.readValue(Int::class.java.classLoader) as Int
    assertEquals(v, v2)
  }


  private fun getLambdaParameterTypes(lambda: Function<*>) {
    val kClass = lambda::class.java
    for(method in kClass.methods){
      if(method.name == "invoke"){
        val parameters = method.parameters
        for(parameter in parameters){
          println(parameter.type)
        }
      }
    }
  }

  @Test
  fun testLambda() {
    val lambda = { a: Int, b: Int -> a + b }
    val types = getLambdaParameterTypes(lambda)


  }
}