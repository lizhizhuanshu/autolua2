package com.autolua.engine

import com.autolua.engine.proto.Interaction
import com.autolua.engine.proto.Interaction.GetResourceRequest
import com.google.gson.Gson
import com.google.protobuf.ExtensionRegistryLite
import org.junit.Test

import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  data class Person(val name:String,val age:Int,val other:ByteArray){
    enum class Other(val value:Int){
      MA(1),
      FE(2);
    }
    var alias  = mutableListOf("Tom","Jerry")
    val aaa = Other.MA
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as Person

      if (name != other.name) return false
      if (age != other.age) return false
      if (alias != other.alias) return false
      if (aaa != other.aaa) return false
      return true
    }

    override fun hashCode(): Int {
      var result = name.hashCode()
      result = 31 * result + age
      result = 31 * result + other.contentHashCode()
      result = 31 * result + alias.hashCode()
      result = 31 * result + aaa.hashCode()
      return result
    }
  }

  @Test
  fun gson(){
    val gson = Gson()
    val person = Person("Tom",18,byteArrayOf(1,2,3))
    val json = gson.toJson(person)
    println(json)
  }

  @Test
  fun byteBuffer(){
    val buffer = ByteBuffer.allocate(1024)
    val request = GetResourceRequest.newBuilder().setPath("test").build()
    val date = request.toByteArray()
    buffer.order(ByteOrder.BIG_ENDIAN)
    buffer.putInt(date.size)
    buffer.put(date)
    buffer.putInt(0)
    buffer.flip()
    val size = buffer.getInt()
    buffer.limit(buffer.limit()-4)
    val newRequest = GetResourceRequest.parseFrom(buffer)


  }

  @Test
  fun protobuf(){
  }
}