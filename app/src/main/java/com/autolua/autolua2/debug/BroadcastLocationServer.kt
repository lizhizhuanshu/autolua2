package com.autolua.autolua2.debug

import android.util.Log
import com.autolua.engine.common.Listenable
import java.util.concurrent.atomic.AtomicReference
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel

open class BroadcastLocationServer(private val group: EventLoopGroup):
  Listenable<BroadcastLocationServer.State>() {
  fun start(port: Int, callback: ((success: Boolean) -> Unit)? = null) {
    if(!state.compareAndSet(State.IDLE, State.STARTING)){
      if (callback != null) callback(false)
      return
    }
    notify(State.STARTING)
    val b = Bootstrap()
    b.group(group)
    b.channel(NioDatagramChannel::class.java)
    b.handler(object : ChannelInitializer<NioDatagramChannel>() {
      override fun initChannel(ch: NioDatagramChannel) {
        ch.pipeline().addLast(BroadcastLocationServerHandler())
      }
    })
    val channelFuture = b.bind(port)
    channelFuture.addListener {
      if(it.isSuccess){
        Log.d(TAG,"BroadcastLocationServer started at port $port")
        val channel = channelFuture.channel()
        this.channel = channel
        channel.closeFuture().addListener {
          Log.d(TAG,"BroadcastLocationServer stopped")
          changedState(State.IDLE)
        }
        changedState(State.RUNNING)
        if (callback != null) callback(true)
      }else{
        changedState(State.IDLE)
        if (callback != null) callback(false)
      }
    }
  }

  fun stop(){
    if(!state.compareAndSet(State.RUNNING, State.STOPPING)){
      return
    }
    Log.d(TAG,"BroadcastLocationServer stopping")
    notify(State.STOPPING)
    channel?.close()
  }

  fun getState(): State{
    return state.get()
  }

  private fun changedState(state: State){
    this.state.set(state)
    notify(state)
  }

  @Volatile
  private var channel: Channel? = null

  private val state = AtomicReference(State.IDLE)

  private class BroadcastLocationServerHandler():
    SimpleChannelInboundHandler<DatagramPacket>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket) {
      val content = msg.content() // 获取数据内容
      val data = ByteArray(content.readableBytes())
      content.readBytes(data)
      val str = String(data)
      Log.d(TAG,"BroadcastLocationServer receive message: $str from ${msg.sender()}")
      if(str == REQUEST_MESSAGE){
        val response = RESPONSE_MESSAGE.toByteArray()
        val buf = ctx.alloc().buffer(response.size)
        buf.writeBytes(response)
        ctx.writeAndFlush(DatagramPacket(buf, msg.sender()))
      }
    }
  }

  companion object{
    private const val TAG = "BroadcastLocationServer"
    private const val REQUEST_MESSAGE = "AutoLuaEngine,Where are you?"
    private const val RESPONSE_MESSAGE = "AutoLuaKit,I am here!"
  }

  enum class State{
    IDLE,
    STARTING,
    RUNNING,
    STOPPING
  }
}