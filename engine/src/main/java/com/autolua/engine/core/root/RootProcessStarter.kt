package com.autolua.engine.core.root

import android.util.Log
import java.io.File

class RootProcessStarter {
  private fun startRootProcess(): Process? {
    try{
      return Runtime.getRuntime().exec("su\n")
    }catch (e:Exception){
      Log.e("RootProcessStarter", "Start root process failed", e)
    }
    return null
  }
  private var packagePath:String = ""
  private var processName:String = ""
  private var targetClassName:String = ""

  fun setPackagePath(path:String): RootProcessStarter {
    packagePath = path
    return this
  }

  fun setProcessName(name:String): RootProcessStarter {
    processName = name
    return this
  }

  fun setStartClass(clazz: Class<*>){
    targetClassName = clazz.name
  }

  private fun getLibraryRootPath():String {
    val root = packagePath.subSequence(0, packagePath.lastIndexOf("/"))
    return "$root/lib/"
  }

  private fun getLibraryPath():String {
    val root = getLibraryRootPath()
    val file = File(root)
    val files = file.listFiles()
    if (files == null || files.isEmpty()) {
      throw RuntimeException("No library found")
    }
    for(f in files){
      if(f.name.indexOf("64")>0){
        return f.absolutePath
      }
    }
    return files[0].absolutePath
  }



  private fun buildCommandLine(): String {
    val command = StringBuilder()
    val libPath = getLibraryPath()
    command.append("export LD_LIBRARY_PATH=\"$libPath:${System.getProperty("java.library.path")}\"\n")
    command.append("export CLASSPATH=$packagePath\n")
    if(libPath.contains("64")) {
      command.append("/system/bin/app_process64 /system/bin ")
    }else{
      command.append("/system/bin/app_process /system/bin ")
    }
    if(processName.isNotEmpty()){
      command.append("--nice-name=$processName ")
    }
    if(targetClassName.isEmpty()){
      throw RuntimeException("target class is empty")
    }
    command.append("$targetClassName \n")
    return command.toString()
  }

  fun start(): Process? {
    val process = startRootProcess()
    if (process != null) {
      val command = buildCommandLine()
      Log.d("RootProcessStarter", "Command: $command")
      process.outputStream.write(command.toByteArray())
      process.outputStream.flush()
    }
    return process
  }

}