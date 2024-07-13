package com.autolua.engine.core.root

interface ServiceManager {
  fun registerService(service:Any,serviceInterface:Class<*>):UInt
  fun registerService(id:UInt,service:Any,serviceInterface:Class<*>)
  fun unregisterService(serviceId:UInt):Any?
  fun findService(serviceId:UInt): ServicePage?
  fun allIds():Array<UInt>
  fun clear()
  data class ServicePage(val interfaceClass: Class<*>, val instance: Any)
}