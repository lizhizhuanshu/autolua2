package com.autolua.autolua2.debug

import android.util.Log
import com.autolua.engine.core.root.DataCallbackManager
import com.autolua.engine.common.Observable
import com.autolua.engine.common.ObservableImp
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpObjectAggregator
import java.net.InetSocketAddress
import io.netty.handler.codec.http.*
import io.netty.util.AttributeKey
import java.util.concurrent.atomic.AtomicReference
import com.autolua.engine.proto.Authorization
import com.autolua.autolua2.protobuf.RequestResource
import com.autolua.autolua2.protobuf.RequestResource.GetResourceRequest
import com.immomo.luanative.hotreload.HotReloadServer


class DebugServerImp(private var group: EventLoopGroup ): DebugServer,
  Observable<DebugServer.State> by ObservableImp<DebugServer.State>() {
  companion object{
    private const val TAG = "DebugServerImp"
    private const val HEADER_SIZE = 5
    private const val MIN_HEADER_SIZE = HEADER_SIZE
    private const val MAX_FRAME_SIZE = 1024 * 1024*10
    private const val AUTH_MESSAGE_REQUEST = 100
    private const val AUTH_MESSAGE_RESPONSE = 101
    private const val GET_RESOURCE_REQUEST = 102
    private const val GET_RESOURCE_RESPONSE = 103
    private const val AUTH_MESSAGE_MAX_SIZE = 1024
    private val MY_TYPE_KEY = AttributeKey.newInstance<SessionType>("sessionType")
  }

  private var auth:String = ""

  private var onPostProject: ((project:String, data:ByteBuf) -> Unit)? = null

  private var handleUIDebugKitMessage: ((type:Int, data:ByteArray) -> Unit)? = null

  @Volatile
  private var kitAutoLuaDebugCtx:ChannelHandlerContext? = null
  @Volatile
  private var workerAutoLuaDebugCtx:ChannelHandlerContext? = null
  @Volatile
  private var uiDebugCtx:ChannelHandlerContext? = null


  private fun createChannelActiveHandler():ChannelInboundHandlerAdapter{
    return object : ChannelInboundHandlerAdapter(){
      override fun channelActive(ctx: ChannelHandlerContext) {
        val inSocket = ctx.channel().remoteAddress() as InetSocketAddress
        val clientIP = inSocket.address.hostAddress
        val clientPort = inSocket.port
        Log.i("ChannelActiveHandler", "新的连接：$clientIP : $clientPort")
      }
      override fun channelInactive(ctx: ChannelHandlerContext) {
        val inSocket = ctx.channel().remoteAddress() as InetSocketAddress
        val clientIP = inSocket.address.hostAddress
        val clientPort = inSocket.port
        Log.i("ChannelActiveHandler", "连接断开：$clientIP : $clientPort")
        val type = ctx.channel().attr(MY_TYPE_KEY).get() ?: return
        when(type){
          SessionType.UNKNOWN -> {}
          SessionType.HTTP -> {}
          SessionType.KIT_AUTO_LUA_DEBUG -> kitAutoLuaDebugCtx = null
          SessionType.UI_DEBUG -> {
            uiDebugCtx = null
            uiDebugKitListener?.invoke(false)
          }
          SessionType.WORKER_AUTO_LUA_DEBUG -> workerAutoLuaDebugCtx = null
        }
      }
    }
  }


  private enum class SessionType{
    UNKNOWN,
    HTTP,
    KIT_AUTO_LUA_DEBUG,
    UI_DEBUG,
    WORKER_AUTO_LUA_DEBUG
  }

  private fun compareString(bytes:ByteArray, str:String):Boolean{
    for(i in str.indices){
      if(bytes[i] != str[i].code.toByte())return false
    }
    return true
  }

  private fun isHttpSession(`in`:ByteBuf):Boolean{
    `in`.markReaderIndex()
    val bytes = ByteArray(5)
    `in`.readBytes(bytes)
    try{
      return compareString(bytes,"GET /") || compareString(bytes,"POST ")
    }finally {
      `in`.resetReaderIndex()
    }
  }


  private fun createLengthDecoder():ByteToMessageDecoder{
    return LengthFieldBasedFrameDecoder(MAX_FRAME_SIZE,
      0,
      4,
      -4,
      4)
  }

  private fun createKitAutoLuaDebugHandler():ChannelInboundHandlerAdapter{
    return object : ChannelInboundHandlerAdapter(){
      override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        Log.d(TAG,"receive message from kit auto lua debug size:${(msg as ByteBuf).readableBytes()}")
        if( msg.readableBytes() > 0){
          msg.markReaderIndex()
          val size = msg.readInt()
          val type = msg.readByte().toInt()
          Log.d(TAG,"type:$type size:$size")
          if(type == GET_RESOURCE_RESPONSE) {
            onGetResourceResponse(msg)
          }else{
            msg.resetReaderIndex()
            workerAutoLuaDebugCtx?.writeAndFlush(msg)
          }
        }
      }
    }
  }

  private fun createWorkerAutoLuaDebugHandler():ChannelInboundHandlerAdapter{
    return object : ChannelInboundHandlerAdapter(){
      override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        Log.d(TAG,"receive message from worker auto lua debug size:${(msg as ByteBuf).readableBytes()}")
        if( msg.readableBytes() > 0){
          kitAutoLuaDebugCtx?.writeAndFlush(msg)
        }
      }
    }
  }

  private fun onGetResourceResponse( msg: ByteBuf){
    val data = RequestResource.GetResourceResponse.parseFrom(ByteBufUtil.getBytes(msg))
    dataCallbackManager.notify(data.id,data.data.toByteArray())
  }

  private fun createUIDebugHandler():ChannelInboundHandlerAdapter{
    return object : ChannelInboundHandlerAdapter(){
      override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if(msg is ByteBuf){
          msg.markReaderIndex()
          val type = msg.readByte().toInt()
          if(type == GET_RESOURCE_RESPONSE) {
            onGetResourceResponse(msg)
          }else{
            msg.resetReaderIndex()
            sendMessageToUIDebugWorker(msg)
          }
        }
      }
    }
  }


  private fun createHttpHandler():SimpleChannelInboundHandler<FullHttpRequest>{
    return object : SimpleChannelInboundHandler<FullHttpRequest>(){
      override fun channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest) {

        Log.d(TAG,"on request uri:${msg.uri()}")
        val method = msg.method()
        if(method.name() == "POST"){
          val headers = msg.headers()
          if(auth.isNotEmpty() && auth != headers.get("Authorization")){
            val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.UNAUTHORIZED)
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
            ctx.writeAndFlush(response)
            return
          }
          val callback = onPostProject
          var response:FullHttpResponse? = null

          if(callback != null){
            val project = msg.uri().substring(1)
            callback(project,msg.content())
            response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.OK)
          }
          if(response == null){
            response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.NOT_FOUND)
          }
          response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")
          response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
          ctx.writeAndFlush(response)
          return
        }

        if(method.name() == "GET"){
          val uri = msg.uri()
          val path:String
          val type:RequestResource.ResourceType
          if(uri.startsWith("/ui/")){
            path = uri.substring(4)
            type = RequestResource.ResourceType.UI_CODE
          }else if(uri.startsWith("/res/")){
            path = uri.substring(5)
            type = RequestResource.ResourceType.RESOURCE
          }else{
            path = uri.substring(1)
            type = RequestResource.ResourceType.UNKNOWN
          }
          Log.d(TAG,"on request resource path:$path")
          requestUIResource(type,path) {id, data ->
            dataCallbackManager.remove(id)
            Log.d(TAG,"response data size:${data?.size}")
              val response:FullHttpResponse
              if (data != null) {
                response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                response.content().writeBytes(data)
                Log.d(TAG, "response data size:${response.content().readableBytes()}")
              } else {
                response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
              }
              response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")
              response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
              ctx.writeAndFlush(response)
          }
        }else{
          val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1,HttpResponseStatus.METHOD_NOT_ALLOWED)
          response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream")
          response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
          ctx.writeAndFlush(response)
        }
      }
    }
  }

  private fun sendAuthResponse(ctx:ChannelHandlerContext,success:Boolean){
    val response = Authorization.AuthorizationResponse.newBuilder()
      .setCode(if(success) 0 else 1)
      .build()
    val bytes = response.toByteArray()
    val buf = ctx.alloc().buffer(HEADER_SIZE + bytes.size)
    buf.writeInt(bytes.size+HEADER_SIZE)
    buf.writeByte(AUTH_MESSAGE_RESPONSE)
    buf.writeBytes(bytes)
    ctx.writeAndFlush(buf)
  }


  private fun createSocketChooser():ByteToMessageDecoder{
    return object : ByteToMessageDecoder(){
      override fun decode(ctx: ChannelHandlerContext, `in`: ByteBuf, out: MutableList<Any>) {
        Log.i(TAG,"receive new message size = ${`in`.readableBytes()}")
        if(`in`.readableBytes() < MIN_HEADER_SIZE)return
        if(isHttpSession(`in`)){
          Log.d(TAG,"http session")
          ctx.channel().attr(MY_TYPE_KEY).set(SessionType.HTTP)
          ctx.pipeline().remove(this)
          ctx.pipeline().addLast(HttpRequestDecoder())
          ctx.pipeline().addLast(HttpResponseEncoder())
          ctx.pipeline().addLast(HttpObjectAggregator(MAX_FRAME_SIZE))
          ctx.pipeline().addLast(createHttpHandler())
          ctx.pipeline().fireChannelRead(`in`.retain())
          return
        }
        `in`.markReaderIndex()
        val packageSize = `in`.readInt()
        val messageType = `in`.readByte().toUByte()
        Log.i(TAG,"messageType = $messageType size = $packageSize")
        if(messageType != AUTH_MESSAGE_REQUEST.toUByte() || packageSize > AUTH_MESSAGE_MAX_SIZE){
          ctx.close()
          return
        }
        val bodySize = packageSize - HEADER_SIZE
        if(`in`.readableBytes() < bodySize){
          `in`.resetReaderIndex()
          return
        }
        val bytes:ByteArray
        val offset:Int
        if(`in`.hasArray()){
          bytes = `in`.array()
          offset = `in`.arrayOffset() + `in`.readerIndex()
        }else{
          bytes = ByteBufUtil.getBytes(`in`,`in`.readerIndex(),bodySize,false)
          offset = 0
        }

        val authorizationRequest = Authorization.AuthorizationRequest.getDefaultInstance().parserForType.parseFrom(bytes,offset,bodySize)
        if(authorizationRequest.auth != auth){
          Log.d(TAG,"auth failed auth:${authorizationRequest.auth} auth:${auth}")
          ctx.close()
          return
        }
        Log.d(TAG,"auth success")
        `in`.skipBytes(packageSize - HEADER_SIZE)
        val type = when(authorizationRequest.sessionType!!){
          Authorization.SessionType.NONE -> SessionType.UNKNOWN
          Authorization.SessionType.KIT_AUTO_LUA_DEBUG -> SessionType.KIT_AUTO_LUA_DEBUG
          Authorization.SessionType.WORKER_AUTO_LUA_DEBUG -> SessionType.WORKER_AUTO_LUA_DEBUG
          Authorization.SessionType.UI_DEBUG -> SessionType.UI_DEBUG
          Authorization.SessionType.UNRECOGNIZED -> SessionType.UNKNOWN
        }
        ctx.pipeline().remove(this)
        ctx.channel().attr(MY_TYPE_KEY).set(type)
        when(type){
          SessionType.UNKNOWN -> {
            ctx.close()
            return
          }
          SessionType.HTTP -> TODO()
          SessionType.KIT_AUTO_LUA_DEBUG -> {
            kitAutoLuaDebugCtx = ctx
            ctx.pipeline().addLast(LengthFieldBasedFrameDecoder(MAX_FRAME_SIZE,
              0,
              4,
              -4,
              0)).addLast(createKitAutoLuaDebugHandler())
            sendAuthResponse(ctx,true)
          }
          SessionType.UI_DEBUG -> {
            uiDebugCtx = ctx
            ctx.pipeline().addLast(createLengthDecoder()).addLast(createUIDebugHandler())
            uiDebugKitListener?.invoke(true)
          }
          SessionType.WORKER_AUTO_LUA_DEBUG -> {
            workerAutoLuaDebugCtx = ctx
            ctx.pipeline().addLast(createWorkerAutoLuaDebugHandler())
            sendAuthResponse(ctx,true)
          }
        }
        ctx.pipeline().fireChannelRead(`in`.retain())
      }
    }
  }

  private fun createMainInitializer():ChannelInitializer<SocketChannel>{
    return object : ChannelInitializer<SocketChannel>(){
      override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast("active",createChannelActiveHandler())
        pipeline.addLast("chooser",createSocketChooser())
      }
    }
  }

  private fun changeState(state: DebugServer.State){
    this.state.set(state)
    notifyObservers(state)
  }


  private val state = AtomicReference(DebugServer.State.IDLE)

  override fun getState(): DebugServer.State {
    return state.get()
  }

  @Volatile
  private var channel:Channel? = null

  private fun startServer(port:Int,callback: (success: Boolean) -> Unit){
    Log.d(TAG,"start server port:$port")
    val b = ServerBootstrap()
    b.group(group)
      .channel(NioServerSocketChannel::class.java)
      .childOption(ChannelOption.TCP_NODELAY,true)
      .childOption(ChannelOption.SO_REUSEADDR, true)
      .childOption(ChannelOption.SO_KEEPALIVE,true)
      .childHandler(createMainInitializer())
    val channelFuture = b.bind(port)
    channelFuture.addListener {
      if(it.isSuccess){
        val channel = channelFuture.channel()
        channel.closeFuture().addListener {
          changeState(DebugServer.State.IDLE)
          HotReloadServer.getInstance().setTransporter(null)
        }
        this.channel = channel
        changeState(DebugServer.State.RUNNING)
        callback(true)
      }else{
        changeState(DebugServer.State.IDLE)
        callback(false)
      }
    }
  }


  override fun start(port: Int,auth:String, callback: (success: Boolean) -> Unit) {
    if(state.compareAndSet(DebugServer.State.IDLE,DebugServer.State.STARTING)){
      notifyObservers(DebugServer.State.STARTING)
      this.auth = auth
      startServer(port,callback)
    }else{
      callback(false)
    }
  }

  override fun stop() {
    if(state.compareAndSet(DebugServer.State.RUNNING,DebugServer.State.STOPPING)){
      notifyObservers(DebugServer.State.STOPPING)
      kitAutoLuaDebugCtx = null
      workerAutoLuaDebugCtx = null
      uiDebugCtx = null
      channel?.close()
    }
  }

  override fun sendMessageToUIDebugKit(type: Int, data: ByteArray) {
    val ctx = uiDebugCtx
    if(ctx == null){
      Log.e(TAG,"uiDebugCtx is null")
      return
    }
    val buf = ctx.alloc().buffer(HEADER_SIZE + data.size)
    buf.writeInt(data.size+HEADER_SIZE)
    buf.writeByte(type)
    if(data.isNotEmpty()){
      buf.writeBytes(data)
    }
    ctx.writeAndFlush(buf)
  }

  override fun setUIDebugKitMessageHandler(handler: (type: Int, data: ByteArray) -> Unit) {
    handleUIDebugKitMessage = handler
  }

  private val dataCallbackManager =
    DataCallbackManager<ByteArray>()

  private fun sendGetRequestRequest(id:Int,type:RequestResource.ResourceType, path:String,ctx:ChannelHandlerContext){
    val request = GetResourceRequest.newBuilder()
    request.type = type
    request.path = path
    request.id = id
    val bytes = request.build().toByteArray()
    val buf = ctx.alloc().buffer(HEADER_SIZE + bytes.size)
    buf.writeInt(bytes.size+HEADER_SIZE)
    buf.writeByte(GET_RESOURCE_REQUEST)
    buf.writeBytes(bytes)
    ctx.writeAndFlush(buf)
  }
  override fun requestUICode(path: String): ByteArray? {
    val ctx = uiDebugCtx?:kitAutoLuaDebugCtx?:return null
    val id = dataCallbackManager.create()
    sendGetRequestRequest(id,RequestResource.ResourceType.UI_CODE,path,ctx)
    return dataCallbackManager.waitAndRemote(id,3000)
  }

  private fun requestUIResource(type:RequestResource.ResourceType,path:String,callback :(id:Int,message:ByteArray?)->Unit):Unit {
    val ctx = uiDebugCtx?:kitAutoLuaDebugCtx?:return
    val id = dataCallbackManager.create(callback)
    sendGetRequestRequest(id,type,path,ctx)
  }


  override fun setPostProjectHandler(callback: (project: String, data: ByteBuf) -> Unit) {
    onPostProject = callback
  }


  @Volatile
  private var uiDebugKitListener :((Boolean)->Unit)? = null
  override fun setUIDebugKitListener(listener: (connection: Boolean) -> Unit) {
    uiDebugKitListener = listener
  }


  override fun startUIDebug() {
    sendAuthResponse(uiDebugCtx?:return,true)
  }


  private fun sendMessageToUIDebugWorker(msg:ByteBuf){
    try{
      val type = msg.readByte().toInt()
      val data = ByteArray(msg.readableBytes())
      msg.readBytes(data)
      Log.d(TAG,"send message to ui type:$type , data:${data.size}")
      handleUIDebugKitMessage?.invoke(type,data)
    }finally {
      msg.release()
    }
  }


}
