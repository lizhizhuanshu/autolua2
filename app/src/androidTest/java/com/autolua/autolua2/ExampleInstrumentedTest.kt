package com.autolua.autolua2

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.KMutableProperty
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaType

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
  data class Person(var name: String = "", var age: Int = 0)

  fun <T : Any> updateObjectFromJson(obj: T, json: String) {
    val gson = Gson()
    val jsonObject = JsonParser.parseString(json).asJsonObject
    val kClass = obj::class
    for (property in kClass.declaredMemberProperties) {
      property.isAccessible = true
      val jsonElement = jsonObject.get(property.name)
      if (jsonElement != null && jsonElement != JsonNull.INSTANCE) {
        if(property is KMutableProperty<*>){
          property.setter.call(obj,gson.fromJson(jsonElement,property.returnType.javaType))
        }
      }
    }
  }
  @Test
  fun useAppContext() {

    val json = """
        {
            "name": "John"
        }
    """.trimIndent()

    val person = Person("Alice", 25)

    updateObjectFromJson(person, json)

    println(person)  // Output: Person(name=John, age=25)
  }

  @Test
  fun testGsonAny(){
    var obj:Any? = Person("Alice", 25)
    obj = null
    val gson = Gson()
    val json = gson.toJson(obj)
    println(json) // Output: {"name":"Alice","age":25}
  }


}