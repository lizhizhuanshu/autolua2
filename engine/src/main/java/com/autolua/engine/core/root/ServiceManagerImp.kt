package com.autolua.engine.core.root

class ServiceManagerImp : ServiceManager {
  private val services = mutableMapOf<UInt, ServiceManager.ServicePage>()
  private var nextServiceId:UInt = 0u


  private fun nextServiceId():UInt{
    var id = nextServiceId
    while(id == 0u || services.containsKey(id)){
      id++
    }
    nextServiceId = id+1u
    return id
  }

  override fun registerService(service: Any, serviceInterface: Class<*>): UInt {
    val id = nextServiceId()
    services[id] = ServiceManager.ServicePage(serviceInterface, service)
    return id
  }

  override fun registerService(id: UInt, service: Any, serviceInterface: Class<*>) {
    if(services.containsKey(id)){
      throw IllegalArgumentException("Service already registered")
    }
    services[id] = ServiceManager.ServicePage(serviceInterface, service)
  }

  override fun unregisterService(serviceId: UInt): Any? {
    return services.remove(serviceId)
  }

  override fun findService(serviceId: UInt): ServiceManager.ServicePage? {
    return services[serviceId]
  }

  override fun allIds(): Array<UInt> {
    return services.keys.toTypedArray()
  }

  override fun clear() {
    services.clear()
  }
}