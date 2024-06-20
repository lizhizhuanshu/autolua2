package com.autolua.engine.core.composite

import com.autolua.engine.core.AutoLuaEngine

class ResourceProvider: AutoLuaEngine.ResourceProvider{
  private val providers:MutableList<AutoLuaEngine.ResourceProvider> = mutableListOf()
  override fun getResource(url: String): ByteArray? {
    for (provider in providers) {
      val code = provider.getResource(url)
      if (code != null) {
        return code
      }
    }
    return null
  }

  fun addProvider(provider: AutoLuaEngine.ResourceProvider) {
    providers.add(provider)
  }

  fun removeProvider(provider: AutoLuaEngine.ResourceProvider) {
    providers.remove(provider)
  }

  fun clear(){
    providers.clear()
  }
}