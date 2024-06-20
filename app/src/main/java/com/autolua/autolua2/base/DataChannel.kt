package com.autolua.autolua2.base

import android.util.Log
import kotlinx.coroutines.*
import androidx.collection.LongSparseArray
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write
import kotlinx.coroutines.channels.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.read

class DataChannel<T> {
  private val channelArray = LongSparseArray<kotlinx.coroutines.channels.Channel<T?>>()
  private val lock = ReentrantReadWriteLock()
  private val id = AtomicInteger(0)

  class Channel<T>(val id:Int,private val channel:kotlinx.coroutines.channels.Channel<T?>){
    fun wait(time:Long = 0):T?{
      return if(time == 0L) {
        runBlocking {
          channel.receive()
        }
      }else{
        runBlocking {
          withTimeoutOrNull(time){
            Log.d("DataChannel","wait")
            val r = channel.receive()
            Log.d("DataChannel","wait end")
            r
          }
        }
      }
    }

    fun onReceive(callback:(T?)->Unit){
      runBlocking {
        launch{

          channel.consumeEach { data ->
            callback(data)
          }
        }
      }
    }


    fun close(){
      runBlocking {
        channel.close()
      }
    }

  }

  fun create():Channel<T>{
    var r:Channel<T>
    lock.write{
      while (true){
        val i = id.incrementAndGet()
        if(channelArray.get(i.toLong()) == null){
         val channel = Channel<T?>()
          r = Channel(i,channel)
          channelArray.put(i.toLong(),channel)
          break
        }
      }
    }
    Log.d("DataChannel","create ${r.id}")
    return r
  }

  fun sendAndRemove(channelId:Int, data:T?){
    val channel = lock.read { channelArray.get(channelId.toLong()) }
    Log.d("DataChannel","sendAndRemove $channelId $data")
    channel?.let {
      runBlocking {
        it.send(data)
      }
      lock.write { channelArray.remove(channelId.toLong()) }
    }
  }

  fun close(id:Int){

  }

  fun close() {
    lock.write {
      for (i in 0 until channelArray.size()) {
        val channel = channelArray.valueAt(i)
        channel?.close()
      }
      channelArray.clear()
    }
  }
}