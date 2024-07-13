package com.autolua.engine.core.root

interface Transporter {
  fun start()
  fun stop()
  fun send(data: ByteArray)
  fun setOnMessage(callback: (data: ByteArray) -> Unit)
  fun setOnOtherMessage(callback: (data: ByteArray) -> Unit)
  fun rawWrite(data: ByteArray)
  fun rawRead():ByteArray?
}