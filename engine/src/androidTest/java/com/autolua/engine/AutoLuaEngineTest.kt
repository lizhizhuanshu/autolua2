package com.autolua.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autolua.engine.base.LuaContext
import com.autolua.engine.common.Observer
import com.autolua.engine.core.AutoLuaEngine
import com.autolua.engine.core.AutoLuaEngineOnLocal


import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AutoLuaEngineTest {

  private fun AutoLuaEngineOnLocal.syncExecute(code:String ){
    val semaphore = Semaphore(1)
    semaphore.acquire()
    val observer = object: Observer<AutoLuaEngine.State> {
      override fun onUpdate(data: AutoLuaEngine.State, flags: Int) {
        if(AutoLuaEngine.Target.fromInt(flags) == AutoLuaEngine.Target.WORKER &&
          data == AutoLuaEngine.State.IDLE)
          semaphore.release()
      }
    }
    try {
      addObserver(observer)
      execute(code)
      semaphore.acquire()
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw RuntimeException("Thread was interrupted", e)
    } finally {
      semaphore.release()
      removeObserver(observer)
    }
  }

  @Test
  fun start(){
    val engine = AutoLuaEngineOnLocal()
    val started = AtomicBoolean(false)
    engine.addObserver( object: Observer<AutoLuaEngine.State> {
      override fun onUpdate(data: AutoLuaEngine.State, flags: Int) {
        if(flags == AutoLuaEngine.Target.ENGINE.value &&
          data == AutoLuaEngine.State.RUNNING)
          started.set(true)
        Log.e("AutoLuaEngineTest","state:   $data")
      }
    })

    engine.start()
    Thread.sleep(1000)
    assertEquals(true,started.get())
  }

  @Test
  fun simpleExecute(){
    val engine = AutoLuaEngineOnLocal()
    engine.start()
    engine.execute("print('hello world')")
    Thread.sleep(1000)
  }

  @Test
  fun execute(){
    val engine = AutoLuaEngineOnLocal()
    engine.start()
    val started = AtomicBoolean(false)
    engine.addObserver(AutoLuaEngine.Target.WORKER.value) {
      if(it == AutoLuaEngine.State.RUNNING)
        started.set(true)
    };
    engine.syncExecute("print('hello world')")
    assertEquals(true,started.get())
  }



  @Test
  fun sleep(){
    val engine = AutoLuaEngineOnLocal()
    engine.start()
    val interrupted = AtomicBoolean(false)
    val running = AtomicBoolean(false)
    engine.addObserver {it:AutoLuaEngine.State,flags:Int ->
      if(flags != AutoLuaEngine.Target.WORKER.value)
        return@addObserver
      if(it == AutoLuaEngine.State.RUNNING)
        running.set(true)
      if(it == AutoLuaEngine.State.IDLE)
        interrupted.set(true)
      Log.e("AutoLuaEngineTest","state:   $it")
    }
    engine.execute("sleep(10000)")
    Thread.sleep(200)
    engine.interrupt()
    assertEquals(true,running.get())
    assertEquals(false,interrupted.get())
  }

  @Test
  fun interrupt(){
    val engine = AutoLuaEngineOnLocal()
    engine.start()
    val interrupted = AtomicBoolean(false)
    engine.addObserver {it:AutoLuaEngine.State,flags:Int ->
      if(flags != AutoLuaEngine.Target.WORKER.value)
        return@addObserver
      if(it == AutoLuaEngine.State.IDLE)
        interrupted.set(true)
      Log.e("AutoLuaEngineTest","state:   $it")
    }
    engine.execute("sleep(10000)")
    Thread.sleep(100)
    engine.interrupt()
    Thread.sleep(200)
    assertEquals(true,interrupted.get())
  }

  @Test
  fun destroy(){
    val engine = AutoLuaEngineOnLocal()
    engine.start()
    val running = AtomicBoolean(false)
    engine.addObserver {it:AutoLuaEngine.State,flags:Int ->
      if(flags != AutoLuaEngine.Target.WORKER.value)
        return@addObserver
      if(it == AutoLuaEngine.State.RUNNING)
        running.set(true)
      Log.e("AutoLuaEngineTest","state:   $it")
    }
    engine.syncExecute("local a = 1")
    engine.close()

    assertEquals(true,running.get())
  }


  class Person {
    private var name:String = ""
    private var age:Int = 0
    fun getName():String{
      return name
    }
    fun setName(name:String){
      this.name = name
    }
    fun getAge():Int{
      return age
    }
    fun setAge(age:Int){
      this.age = age
    }
  }


  @Test
  fun localService(){
    val engine = AutoLuaEngineOnLocal()
    val services = mutableListOf<AutoLuaEngine.LocalService<*>>()
    val service = AutoLuaEngine.LocalService<Person>("person")
    val person = Person()
    service.service = person
    service.mInterface = Person::class.java
    services.add(service)
    engine.setLocalServices(services)
    engine.start()
    engine.syncExecute("person:setName('zhangsan')")
    assertEquals("zhangsan",person.getName())
    engine.syncExecute("person:setAge(20)")
    assertEquals(20,person.getAge())
  }

  @Test
  fun environment(){
    val engine = AutoLuaEngineOnLocal()
    val environment = mutableListOf<AutoLuaEngine.Environment<*>>()
    environment.add(AutoLuaEngine.Environment("name","zhangsan"))
    environment.add(AutoLuaEngine.Environment("age",20))
    engine.setEnvironment(environment)
    engine.start()
    val person = Person()
    val services = mutableListOf<AutoLuaEngine.LocalService<*>>()
    val service = AutoLuaEngine.LocalService<Person>("person")
    service.service = person
    service.mInterface = Person::class.java
    services.add(service)
    engine.setLocalServices(services)
    engine.syncExecute("person:setName(name)")
    assertEquals("zhangsan",person.getName())
    engine.syncExecute("person:setAge(age)")
    assertEquals(20,person.getAge())
  }

  @Test
  fun codeProvider(){
    val engine = AutoLuaEngineOnLocal()
    val code = """
      local M = {}
      function M.add(a,b)
        return a+b
      end
      function M.sub(a,b)
        return a-b
      end
      return M
    """.trimIndent().toByteArray()
    val codeProvider = object: AutoLuaEngine.CodeProvider {
      override fun getModule(url: String): AutoLuaEngine.CodeProvider.Code? {
        if(url == "test")
          return AutoLuaEngine.CodeProvider.Code(LuaContext.CodeMode.TEXT_OR_BINARY, code)
        return null
      }

      override fun getFile(url: String): AutoLuaEngine.CodeProvider.Code? {
        if(url == "test.lua")
          return AutoLuaEngine.CodeProvider.Code(LuaContext.CodeMode.TEXT_OR_BINARY, code)
        return null
      }
    }
    engine.addCodeProvider(codeProvider)
    engine.start()
    val person = Person()
    val services = mutableListOf<AutoLuaEngine.LocalService<*>>()
    val service = AutoLuaEngine.LocalService<Person>("person")
    service.service = person
    service.mInterface = Person::class.java
    services.add(service)
    engine.setLocalServices(services)
    val eCode = """
      local obj = require('test')
      person:setAge(obj.add(1,3))
    """.trimIndent()
    engine.syncExecute(eCode)
    assertEquals(4,person.getAge())
    person.setAge(0)
    val eCode1 = """
      local obj = loadfile('test.lua')()
      person:setAge(obj.add(1,3))
    """.trimIndent()
    engine.syncExecute(eCode1)
    assertEquals(4,person.getAge())

  }

  @Test
  fun resourceProvider(){
    val engine = AutoLuaEngineOnLocal()
    val resourceProvider = object: AutoLuaEngine.ResourceProvider {
      override fun getResource(url: String): ByteArray? {
        if(url == "test")
          return "lizhi".toByteArray()
        return null
      }
    }
    engine.addResourceProvider(resourceProvider)
    engine.start()
    val person = Person()
    val services = mutableListOf<AutoLuaEngine.LocalService<*>>()
    val service = AutoLuaEngine.LocalService<Person>("person")
    service.service = person
    service.mInterface = Person::class.java
    services.add(service)
    engine.setLocalServices(services)
    val eCode = """
      local name = loadresource('test')
      person:setName(name)
    """.trimIndent()
    person.setName("")
    engine.syncExecute(eCode)
    assertEquals("lizhi",person.getName())
  }







}