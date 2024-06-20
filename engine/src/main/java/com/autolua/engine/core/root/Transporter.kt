package com.autolua.engine.core.root

import android.util.Log
import com.autolua.engine.proto.Interaction.GetCodeRequest
import com.autolua.engine.proto.Interaction.GetResourceRequest
import com.autolua.engine.proto.Interaction.MessageType
import com.autolua.engine.proto.Interaction.NotifyState
import com.autolua.engine.proto.Interaction.RpcRequest
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.util.Scanner
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class Transporter(process: Process) {
  private val ins = process.inputStream
  private val outs = process.outputStream
  private val messageIns = process.errorStream
  private var messageCallback: (type: MessageType, data: Any?) -> Unit = { _, _ -> }
  private var standOutCallback: (message: String) -> Unit = { _ -> }
  private var readThread:Thread? = null
  private var methodThread:Thread? = null
  private val isStarted = AtomicBoolean(false)
  private val queue:LinkedBlockingQueue<ByteBuffer> = LinkedBlockingQueue()
  private var writeThread:Thread? = null

  fun start() {
    if(isStarted.compareAndSet(false,true)){
      readThread = Thread {
        try{
          val reader = Scanner(ins)
          while(reader.hasNextLine()){
            val line = reader.nextLine()
            onStandOut(line)
          }
        }catch (e:InterruptedException){
          Log.d("Transporter", "method thread interrupted")
        }catch (e:InterruptedIOException) {
          Log.d("Transporter", "method thread interrupted")
        }
      }

      writeThread = Thread {
        try{
          while(true){
            val data = queue.take()
            outs.write(data.array(),0,data.limit())
            outs.flush()
          }
        }catch (e:IOException) {
          Log.d("Transporter", "write thread interrupted")
        }
        catch (e:InterruptedException){
          Log.d("Transporter", "method thread interrupted")
        }catch (e:InterruptedIOException) {
          Log.d("Transporter", "method thread interrupted")
        }
      }

      methodThread = Thread{
        try {
          loop()
        }catch (e:Exception){
          Log.d("Transporter", "method thread interrupted")
        }
      }

      readThread?.start()
      writeThread?.start()
      methodThread?.start()
    }
  }

  fun stop() {
    if(isStarted.compareAndSet(true,false)){
      readThread?.interrupt()
      writeThread?.interrupt()
      methodThread?.interrupt()
    }
  }

  fun destroy() {
    stop()
    readThread?.join()
    writeThread?.join()
    methodThread?.join()
  }

  fun onMessage(callback: (type: MessageType, data:Any?) -> Unit) {
    messageCallback = callback
  }

  fun onStandOut(callback: (message: String) -> Unit) {
    standOutCallback = callback
  }


  fun rawWrite(data: ByteArray) {
    outs.write(data)
    outs.flush()
  }

  fun rawWrite(command: String) {
    rawWrite(command.toByteArray())
  }

  fun rawWriteLine(command: String) {
    rawWrite(command + "\n")
  }

  fun rawReadLine():String {
    val buffer = ByteBuffer.allocate(1024)
    buffer.order(ByteOrder.BIG_ENDIAN)
    while(true){
      val read = ins.read(buffer.array(), buffer.position(), buffer.remaining())
      if(read < 0){
        break
      }
      buffer.position(buffer.position() + read)
      if(buffer.position() > 0 && buffer.get(buffer.position() - 1) == '\n'.code.toByte()){
        break
      }
    }
    buffer.flip()
    return String(buffer.array(),0,buffer.limit()-1,Charsets.UTF_8)
  }

  fun send(type: UByte, data: ByteArray?) {
//    Log.d("Transporter","send type $type data ${data?.size}")
    val buffer = ByteBuffer.allocate(5 + (data?.size ?: 0))
    buffer.order(ByteOrder.BIG_ENDIAN)
    if(data != null) {
      buffer.putInt(data.size)
      buffer.put(type.toByte())
      buffer.put(data)
    }else{
      buffer.putInt(0)
      buffer.put(type.toByte())
    }
    buffer.flip()
    queue.put(buffer)
  }

  companion object {
    private const val HEADER_STR = "<AutoLua>"
    private fun hasHeader(data: ByteBuffer): Boolean {
      if(data.position() < HEADER_STR.length) {
        return false
      }
      for(i in HEADER_STR.indices) {
        if(data.get(i) != HEADER_STR[i].code.toByte()) {
          return false
        }
      }
      return true
    }

    private fun wholePackage(data: ByteBuffer): Boolean {
      if(data.position() < HEADER_STR.length+5) {
        return false
      }
      val size = data.getInt(HEADER_STR.length)
      return data.position() >= size + HEADER_STR.length + 5
    }
  }

  private fun onStandOut(message:String) {
    standOutCallback(message)
  }

  private fun onHandleMessage(type: UByte, data: ByteBuffer) {
    val mType = MessageType.entries[type.toInt()]
    val message: Any? = when(mType) {
      MessageType.kGetCode-> GetCodeRequest.parseFrom(data)
      MessageType.kNotifyState -> NotifyState.parseFrom(data)
      MessageType.kGetResource -> GetResourceRequest.parseFrom(data)
      MessageType.kRpc -> RpcRequest.parseFrom(data)
      MessageType.kExecuteCode -> null
      MessageType.kUnknown -> null
      MessageType.kInterrupt -> null
      MessageType.kPause -> null
      MessageType.kResume -> null
      MessageType.kGetCodeResponse -> null
      MessageType.kGetResourceResponse -> null
      MessageType.kRpcResponse -> null
      MessageType.UNRECOGNIZED -> null
      MessageType.kLog -> TODO()
      MessageType.kStartDebuggerCommand -> TODO()
      MessageType.kStopDebuggerCommand -> TODO()
      MessageType.kStopEngineCommand -> TODO()
      MessageType.kSetRootDirCommand -> TODO()
      MessageType.kScreenShotRequest -> TODO()
      MessageType.kScreenShotResponse -> TODO()
    }
    messageCallback(mType,message)
  }



  private fun onHandlerData(data: ByteBuffer) {
    if(hasHeader(data)){
      if(!wholePackage(data)){
        return
      }
      data.flip()
      data.position(HEADER_STR.length)
      val size = data.getInt()
      val type = data.get()
      val oldLimit = data.limit()
      data.limit(data.position() + size)
      onHandleMessage(type.toUByte(),data)
      data.position(data.limit())
      data.limit(oldLimit)
      data.compact()
      if(data.position() > 0){
        onHandlerData(data)
      }
    }else {
      val nextLine = '\n'.code.toByte()
      var index = -1
      for (i in 0 until data.position()) {
        if (data.get(i) == nextLine) {
          index = i
        }
      }
      if(index >=0) {
        data.flip()
        onStandOut(String(data.array(), 0, index + 1, Charsets.UTF_8))
        data.position(index+1)
        data.compact()
        if (data.position() > 0) {
          onHandlerData(data)
        }
      }
    }


  }



  private var readBuffer: ByteBuffer = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN)

  private fun loop(){
    var buffer = readBuffer
    buffer.clear()
    Log.e("Transporter","start loop")
    while(true){
      val read = messageIns.read(buffer.array(), buffer.position(), buffer.remaining())
      if(read < 0){
        break
      }
      Log.e("Transporter","read size $read")
      buffer.position(buffer.position() + read)
      if (buffer.remaining() == 0) {
        val newBuffer = ByteBuffer.allocate(buffer.capacity() * 2)
        newBuffer.order(ByteOrder.BIG_ENDIAN)
        buffer.flip()
        newBuffer.put(buffer)
        buffer = newBuffer
      }
      onHandlerData(buffer)
    }
  }

}