package com.autolua.engine


import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autolua.engine.base.CommonLuaObjectAdapter
import com.autolua.engine.base.JavaObjectWrapper
import com.autolua.engine.base.LuaContext
import com.autolua.engine.base.LuaContextImplement
import com.autolua.engine.base.MethodCache
import com.autolua.engine.base.ObjectCacheImp
import com.autolua.engine.base.Releasable

import org.junit.Test


import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType

@RunWith(AndroidJUnit4::class)
class LuaContextTest {

  private fun createLuaContext(): LuaContext {
    return LuaContextImplement(ObjectCacheImp())
  }
  @Test
  fun pushString() {
    val context = createLuaContext()
    context.push("Hello, World!")
    assertEquals("Hello, World!", context.toString(-1))
  }

  @Test
  fun pushInteger() {
    val luaContext = createLuaContext()
    luaContext.push(42)
    assertEquals(42, luaContext.toLong(-1))
  }

  @Test
  fun pushNumber() {
    val luaContext = createLuaContext()
    luaContext.push(3.14)
    assertEquals(3.14, luaContext.toDouble(-1), 0.0)
  }

  @Test
  fun pushBoolean() {
    val luaContext = createLuaContext()
    luaContext.push(true)
    assertEquals(true, luaContext.toBoolean(-1))
  }

  @Test
  fun pushNil() {
    val luaContext = createLuaContext()
    luaContext.pushNil()
    assertEquals( luaContext.type(-1),LuaContext.ValueType.NIL)
  }

  @Test
  fun pushTable() {
    val luaContext = createLuaContext()
    luaContext.createTable(0, 0)
    luaContext.push("key")
    luaContext.push("value")
    luaContext.setTable(-3)
    luaContext.push("key")
    luaContext.getTable(-2)
    assertEquals("value", luaContext.toString(-1))
  }

  @Test
  fun pushJavaObject(){
    val luaContext = createLuaContext()
    val obj = object : CommonLuaObjectAdapter() {
      fun add(L:LuaContext):Int{
        val a = L.toLong(2)
        val b = L.toLong(3)
        L.push(a+b)
        return 1
      }
      fun sub(L:LuaContext):Int{
        val a = L.toLong(2)
        val b = L.toLong(3)
        L.push(a-b)
        return 1
      }
    }
    luaContext.push(obj)
    luaContext.setGlobal("obj")
    val code = """
      return obj:add(1,2)
    """.trimIndent()
    luaContext.loadBuffer(code.toByteArray(), "test", LuaContext.CodeMode.TEXT)
    luaContext.pcall(0, 1, 0)
    assertEquals(3, luaContext.toLong(-1))
  }

  private fun other(a:Int,b:Int):Boolean{
    return a>b
  }

  @Test
  fun testKotlinClosure(){
    val luaContext = createLuaContext()
    val closure = {a:Int,b:Int->a+b}
    luaContext.push(closure)
    luaContext.setGlobal("closure")
    val code = """
      return closure(1,2)
    """.trimIndent()
    luaContext.loadBuffer(code.toByteArray(), "test", LuaContext.CodeMode.TEXT)
    luaContext.pcall(0, 1, 0)
    assertEquals(3, luaContext.toLong(-1))

    val closure2 = {a:Int,b:Int->
      other(a,b)
    }
    luaContext.push(closure2)
    luaContext.setGlobal("closure2")
    val code2 = """
      return closure2(1,2)
    """.trimIndent()
    luaContext.loadBuffer(code2.toByteArray(), "test", LuaContext.CodeMode.TEXT)
    luaContext.pcall(0, 1, 0)
    assertEquals(false, luaContext.toBoolean(-1))
  }

  @Test
  fun releasable(){
    val luaContext = createLuaContext()
    val released = AtomicBoolean(false)
    val obj = object : Releasable {
      fun add(a:Int,b:Int):Int{
        return a+b
      }

      override fun onRelease() {
        released.set(true)
      }
    }
    luaContext.push(JavaObjectWrapper(obj.javaClass, obj, MethodCache()))
    luaContext.setGlobal("obj")
    val code = """
      return obj:add(1,2)
    """.trimIndent()
    luaContext.loadBuffer(code.toByteArray(), "test", LuaContext.CodeMode.TEXT)
    luaContext.pcall(0, 1, 0)
    assertEquals(3, luaContext.toLong(-1))
    luaContext.destroy()
    assertEquals(true, released.get())
  }

  @Test
  fun typeCheck(){
    val luaContext = createLuaContext()
    luaContext.push("Hello, World!")
    assertEquals(LuaContext.ValueType.STRING, luaContext.type(-1))
    luaContext.push(42)
    assertEquals(LuaContext.ValueType.NUMBER, luaContext.type(-1))
    assertEquals(true, luaContext.isInteger(-1))
    luaContext.push(3.14)
    assertEquals(LuaContext.ValueType.NUMBER, luaContext.type(-1))
    luaContext.push(true)
    assertEquals(LuaContext.ValueType.BOOLEAN, luaContext.type(-1))
    luaContext.pushNil()
    assertEquals(LuaContext.ValueType.NIL, luaContext.type(-1))
  }

  /**
   * 测试大量的Java对象
   */
  @Test
  fun manyJavaObject(){
    val luaContext = createLuaContext()
    val obj = object : CommonLuaObjectAdapter() {
      fun add(L:LuaContext):Int{
        val a = L.toLong(2)
        val b = L.toLong(3)
        L.push(a+b)
        return 1
      }
      fun sub(L:LuaContext):Int{
        val a = L.toLong(2)
        val b = L.toLong(3)
        L.push(a-b)
        return 1
      }

    }
    for(i in 1..10000){
      luaContext.push(obj)
      luaContext.setGlobal("obj$i")
    }
    luaContext.destroy()
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun LuaContext.pushData(obj:Any){
    val clazz = obj::javaClass.call()
    val properties = clazz.fields

    createTable(0, properties.size)
    println(properties.size)

    for (property in properties) {
      val name = property.name
      println("name: $name")
      push(name)
      pushValue(property.get(obj), property.type)
      setTable(-3)
    }
  }

  object Other{
    val name = "other"
    val age = 30
  }

  @Test
  fun testPushData(){
    val luaContext = createLuaContext()
    luaContext.pushData(Other)

    luaContext.setGlobal("person")
    val code = """
      return person.name,person.age
    """.trimIndent()
    luaContext.loadBuffer(code.toByteArray(), "test", LuaContext.CodeMode.TEXT)
    luaContext.pcall(0, 2, 0)
    assertEquals("other", luaContext.toString(-2))
    assertEquals(30, luaContext.toLong(-1))
  }


}