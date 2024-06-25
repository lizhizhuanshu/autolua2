package com.autolua.engine.core

import com.autolua.engine.core.AutoLuaEngine.DebuggerConfigure
import com.autolua.engine.core.AutoLuaEngine.RemoteServerConfigure

object Utils {
  fun debuggerConfigure2RemoteServerConfigure(configure: DebuggerConfigure): RemoteServerConfigure {
    val remoteServerConfigure = RemoteServerConfigure("debugger",configure.port)
    remoteServerConfigure.auth = configure.auth
    remoteServerConfigure.host = configure.host
    remoteServerConfigure.services = RemoteServerConfigure.OBSERVER or
            RemoteServerConfigure.CODE_PROVIDER or
            RemoteServerConfigure.RESOURCE_PROVIDER or
            RemoteServerConfigure.CONTROLLER
    remoteServerConfigure.rpcServices.addAll(configure.rpcServices)
    return remoteServerConfigure
  }
}