package com.autolua.engine.core.composite
import com.autolua.engine.core.AutoLuaEngine
class CodeProvider: AutoLuaEngine.CodeProvider {
  private val providers:MutableList<AutoLuaEngine.CodeProvider> = mutableListOf()
  override fun getModule(url: String): AutoLuaEngine.CodeProvider.Code? {
    for (provider in providers) {
      val code = provider.getModule(url)
      if (code != null) {
        return code
      }
    }
    return null
  }

  override fun getFile(url: String): AutoLuaEngine.CodeProvider.Code? {
    for (provider in providers) {
      val code = provider.getFile(url)
      if (code != null) {
        return code
      }
    }
    return null
  }

  fun addProvider(provider: AutoLuaEngine.CodeProvider) {
    providers.add(provider)
  }

  fun removeProvider(provider: AutoLuaEngine.CodeProvider) {
    providers.remove(provider)
  }

  fun clear(){
    providers.clear()
  }
}