package com.autolua.engine.core.root

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

@Parcelize
data class InvokePackage(val serviceId:UInt,val callId:Int, val method:String,val args:Array< out @RawValue Any>?) :
  Parcelable {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as InvokePackage
    if (serviceId != other.serviceId) return false
    if (method != other.method) return false
    if (callId != other.callId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = serviceId.hashCode()
    result = 31 * result + method.hashCode()
    result = 31 * result + callId
    return result
  }

  companion object{
    const val OBSERVER = 1u
    const val CODE_PROVIDER = 2u
    const val RESOURCE_PROVIDER = 3u
  }
}
