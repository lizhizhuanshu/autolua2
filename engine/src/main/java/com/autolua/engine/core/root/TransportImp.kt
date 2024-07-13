package com.autolua.engine.core.root

import android.os.HandlerThread
import android.util.Log
import com.autolua.engine.core.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.max
import kotlin.math.min

class TransportImp(private val ios:InputStream,
                   private val ots:OutputStream,private val isServer:Boolean=false): Transporter
{
  private val queue: LinkedBlockingQueue<ByteBuffer> = LinkedBlockingQueue()

  private fun isAutoLuaHeader(origin:Int,data:ByteBuffer):Boolean{
    if(origin + MESSAGE_HEADER.length >= data.position()){
      return false
    }
    for(i in MESSAGE_HEADER.indices){
      if(data[origin+i] != MESSAGE_HEADER[i].code.toByte()){
        return false
      }
    }
    return true
  }

  enum class DecodeState{
    UNKNOWN,
    MESSAGE
  }



  private var decodeState = DecodeState.UNKNOWN
  private var lastReadIndex = 0

  private fun handleOtherMessage(it:ByteBuffer,endIndex:Int){
    val data = ByteArray(endIndex+1)
    it.flip()
    it.get(data)
    onOtherMessage?.invoke(data)
    it.compact()
    lastReadIndex = 0
  }

  private fun onHandlerData(it:ByteBuffer):ByteArray?{
    if(it.position() >= MESSAGE_HEADER_LENGTH){
      lastReadIndex = max(0,lastReadIndex - MESSAGE_HEADER_LENGTH)
    }
    while (lastReadIndex < it.position()){
      when(decodeState){
        DecodeState.UNKNOWN ->{
          for(i in lastReadIndex until it.position()){
            if(it.get(i) == '\n'.code.toByte()){
              handleOtherMessage(it,i)
              break
            }else if(isAutoLuaHeader(i,it)){
              decodeState = DecodeState.MESSAGE
              if(lastReadIndex>0){
                handleOtherMessage(it,i-1)
              }
              break
            }else{
              lastReadIndex++
            }
          }
        }
        DecodeState.MESSAGE ->{
          if(it.position() < MESSAGE_HEADER_LENGTH){
            return null
          }
          val size = it.getInt(MESSAGE_HEADER.length)
//          Utils.log("Transporter","size ${size+ MESSAGE_HEADER_LENGTH}")
          if(it.position() < size + MESSAGE_HEADER_LENGTH){
            return null
          }

          val data = ByteArray(size)
          it.flip()
          it.position(MESSAGE_HEADER_LENGTH)
          it.get(data)
          it.compact()
          decodeState = DecodeState.UNKNOWN
          lastReadIndex = 0
          return data
        }
      }
    }
    return null
  }
  private var readThread = Thread(this::onRead)
  private var writeThread = Thread(this::onWrite)
  override fun start() {
    readThread.start()
    writeThread.start()
  }
  override fun stop() {
    readThread.interrupt()
    writeThread.interrupt()
  }



  private fun onWrite(){
    try {
      while (true) {
        val data = queue.take()
//        Utils.log("Transporter","write size ${data.limit()}")
        ots.write(data.array(), 0, data.limit())
        ots.flush()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private var readBuffer = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN)
  private fun rawReadOneMessage():ByteArray?{
    while(true){
      val nowRemaining = readBuffer.remaining()
      val nowPosition = readBuffer.position()
      val remain = if(decodeState == DecodeState.UNKNOWN)
        3
      else{
        if(nowPosition< MESSAGE_HEADER_LENGTH)
          MESSAGE_HEADER_LENGTH-nowPosition
        else{
          val size = readBuffer.getInt(MESSAGE_HEADER.length)
          size+MESSAGE_HEADER_LENGTH-nowPosition
        }
      }
//      Utils.log("Transporter","read remain ${min(remain,nowRemaining)}")
      val read = ios.read(readBuffer.array(), readBuffer.position(), min(remain,nowRemaining))
      if(read < 0){
        break
      }
      readBuffer.position(readBuffer.position() + read)
//      Utils.log("Transporter","read size $read , position ${readBuffer.position()}")
      if (readBuffer.remaining() == 0) {
        val newBuffer = ByteBuffer.allocate(readBuffer.capacity() * 2)
        newBuffer.order(ByteOrder.BIG_ENDIAN)
        readBuffer.flip()
        newBuffer.put(readBuffer)
        readBuffer = newBuffer
      }
      val data = onHandlerData(readBuffer)
      if(data != null){
//        Utils.log("Transporter","read data size = ${data.size}")
        return data
      }

    }
    return null
  }
  private fun readOneMessage():ByteArray?{
    try{
      return rawReadOneMessage()
    }catch (e:Exception){
      e.printStackTrace()
      return null
    }
  }

  private val threadPool = Executors.newFixedThreadPool(2)
  private fun onRead(){
    while(true){
      val data = readOneMessage()
      if(data != null){
        threadPool.submit {
          onMessage?.invoke(data)
        }
      }else{
        break
      }
    }
  }


  companion object{
    private const val MESSAGE_HEADER = "<AutoLua>"
    private const val MESSAGE_HEADER_LENGTH = MESSAGE_HEADER.length+4
  }

  private fun packageData(data:ByteArray):ByteBuffer{
    val buffer = ByteBuffer.allocate(data.size + MESSAGE_HEADER_LENGTH)
    buffer.order(ByteOrder.BIG_ENDIAN)
    buffer.put(MESSAGE_HEADER.toByteArray())
    buffer.putInt(data.size)
    buffer.put(data)
    buffer.flip()
    return buffer
  }
  override fun send(data: ByteArray) {
    val buffer = packageData(data)
    queue.put(buffer)
  }

  @Volatile
  private var onMessage:((data:ByteArray)->Unit)? = null
  override fun setOnMessage(callback: (data: ByteArray) -> Unit) {
    onMessage = callback
  }

  private var onOtherMessage:((data:ByteArray)->Unit)? = null
  override fun setOnOtherMessage(callback: (data: ByteArray) -> Unit) {
    onOtherMessage = callback
  }

  override fun rawWrite(data: ByteArray) {
    val buffer = packageData(data)
    ots.write(buffer.array(), 0, buffer.limit())
    ots.flush()
  }

  override fun rawRead(): ByteArray? {
    return readOneMessage()
  }

}