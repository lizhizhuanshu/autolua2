package com.autolua.autolua2.project

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.collection.arraySetOf
import com.autolua.autolua2.activity.ProjectActivity
import com.autolua.autolua2.base.Configure
import com.google.gson.Gson
import com.immomo.mls.InitData
import com.immomo.mls.MLSBundleUtils
import java.io.File
import java.io.InputStream
import java.util.HashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream


class ProjectManagerImp private constructor() :ProjectManager {
  private val observers = CopyOnWriteArraySet<ProjectManager.ProjectObserver>()
  private val projects = ConcurrentSkipListMap<String,ProjectInfoImp>()
  private val tasks = LinkedBlockingQueue<Task>()
  private val useInfo:MutableList<ProjectUseInfo> = arrayListOf()
  private var thread:Thread? = null
  private lateinit var context: Context

  private data class Task(val type:Type) {
    enum class Type{
      kAdd,
      kRemove,
      kUpdate
    }
    var projectName:String? = null
    var version:String? = null
    var uri:Uri? = null
    var callback:Callback? = null
    var data:ByteArray? = null
  }

  interface Callback {
    fun onResult(error:String? = null)
  }

  companion object {
    private const val TAG = "ProjectManagerImp"
    val instance: ProjectManagerImp by lazy { ProjectManagerImp() }
  }

  private fun checkDir(rootDir:String){
    val rootFile = File(rootDir)
    if(!rootFile.exists()){
      rootFile.mkdirs()
    }
    cacheDir = "$rootDir/cache"
    val cacheFile = File(cacheDir)
    if(!cacheFile.exists()){
      cacheFile.mkdirs()
    }
    projectDir = "$rootDir/projects"
    val projectFile = File(projectDir)
    if(!projectFile.exists()){
      projectFile.mkdirs()
    }
    configurePath = "$rootDir/configure.json"
    val configure = File(configurePath)
    if(!configure.exists()){
      configure.createNewFile()
    }
  }

  private fun loadConfigure():MutableList<ProjectUseInfo>{
    val configure = File(configurePath)
    val data = configure.readText()
    if(data.isEmpty()){
      return arrayListOf()
    }
    val gson = Gson()
    try {
      val info = gson.fromJson(data,Array<ProjectUseInfo>::class.java)
      return info.toMutableList()
    }catch (e:Exception){
      configure.delete()
      configure.createNewFile()
      return arrayListOf()
    }
  }

  private data class ProjectUseInfo(val name:String, var version:String?)

  private fun initProjectInfo(configure:MutableList<ProjectUseInfo>):Boolean{
    val errorProjects = arrayListOf<ProjectUseInfo>()
    for(info in configure){
      val projectDir = File(projectDir + "/" + info.name)
      if(projectDir.exists() && projectDir.isDirectory){
        val versions = arraySetOf<ProjectInfo.VersionInfo>()
        val versionFiles = projectDir.listFiles()
        if(versionFiles != null){
          for(versionFile in versionFiles){
            if(versionFile.isDirectory){
              val projectInfo = checkProjectInfo(versionFile)
              val versionName = projectInfo?.version?:versionFile.name
              val description = projectInfo?.description?:"no description"
              val versionInfo = ProjectInfo.VersionInfo(versionName,description)
              versions.add(versionInfo)
            }else{
              errorProjects.add(info)
            }
          }
        }
        if(versions.isNotEmpty()){
          val projectInfo = ProjectInfoImp(info.name,versions)
          projects[info.name] = projectInfo
        }else{
          info.version = null
          errorProjects.add(info)
        }

      }else{
        info.version = null
        errorProjects.add(info)
      }
    }

    if(errorProjects.isNotEmpty()){
      for (info in errorProjects){
        var dir = File(projectDir + "/" + info.name)
        if(info.version != null){
          dir = File( dir, info.version!!)
          configure.find { it.name == info.name }?.version = null
        }else{
          configure.remove(info)
        }
        if(dir.exists()){
          dir.deleteRecursively()
        }

      }
      return true
    }
    return false
  }


  private fun saveConfigure(configure:List<ProjectUseInfo>){
    val gson = Gson()
    val data = gson.toJson(configure)
    val configureFile = File(configurePath)
    configureFile.writeText(data)
  }

  private fun notifyProjectChanged(){
    val observers = observers.toTypedArray()
    for(observer in observers){
      observer.onProjectChanged()
    }
  }

  fun stop(){
    thread?.interrupt()
  }

  private lateinit var cacheDir:String
  private lateinit var projectDir:String
  private lateinit var configurePath:String
  private fun rawInit(context: Context,rootDir:String){
    this.context = context
    checkDir(rootDir)
    val configure = loadConfigure()
    val isChange = initProjectInfo(configure)
    if (isChange) {
      saveConfigure(configure)
    }
    useInfo.addAll(configure)
    notifyProjectChanged()
  }

  private fun unzipProjectToTarget(ins:InputStream, targetDir:File){
    val zipIns = ZipInputStream(ins)
    while (true){
      val entry = zipIns.nextEntry ?: break
      val entryName = entry.name
      val entryFile = File(targetDir,entryName)
      if(entry.isDirectory){
        entryFile.mkdirs()
      }else{
        entryFile.parentFile?.mkdirs()
        val out = entryFile.outputStream()
        val buffer = ByteArray(1024)
        while (true){
          val len = zipIns.read(buffer)
          if(len <= 0){
            break
          }
          out.write(buffer,0,len)
        }
        out.close()
      }
    }
  }

  private data class OneProjectInfo(val name:String,
                                    var version:String?,
                                    val description:String?,
                                    val uiEntry:String?,
                                    val preloadEntry:String?,
                                    val mainEntry:String?)

  private fun checkProjectInfo(projectDir:File):OneProjectInfo?{
    val info = File(projectDir,"package.json")
    if(!info.exists()){
      return null
    }
    val data = info.readText()
    val gson = Gson()
    return try {
      return gson.fromJson(data,OneProjectInfo::class.java)
    }catch (e:Exception){
      null
    }
  }

  private fun moveToProjectDir(cacheDir:File, projectInfo:OneProjectInfo){
    val project = File(this.projectDir,projectInfo.name)
    if(!project.exists()){
      project.mkdirs()
    }
    val version = File(project,projectInfo.version!!)
    if(version.exists()){
      version.deleteRecursively()
    }
    cacheDir.renameTo(version)
  }

  private fun ensureProjectCacheDir(projectName:String) : File{
    val projectCacheDir = File(this.cacheDir + "/" + projectName)
    if(projectCacheDir.exists()){
      projectCacheDir.deleteRecursively()
    }
    projectCacheDir.mkdirs()
    return projectCacheDir
  }

  private fun updateUseInfo(projectInfo:OneProjectInfo){
    val useInfo = useInfo.find { it.name == projectInfo.name }
    if(useInfo != null){
      useInfo.version = projectInfo.version
    }else{
      this.useInfo.add(ProjectUseInfo(projectInfo.name,projectInfo.version))
    }
    saveConfigure(this.useInfo)
  }



  private fun updateProjectInfo(configure:OneProjectInfo){
    val projectInfo = this.projects[configure.name]
    val versionInfo = ProjectInfo.VersionInfo(configure.version!!,configure.description ?: "")
    if(projectInfo != null){
      projectInfo.versions.add(versionInfo)
      if(projectInfo._lastActionVersion == null){
        projectInfo._lastActionVersion = configure.version
      }
    }else{
      val versions = mutableSetOf(versionInfo)
      val newProjectInfo = ProjectInfoImp(configure.name,versions)
      newProjectInfo._lastActionVersion = configure.version
      this.projects[configure.name] = newProjectInfo
    }
  }

  private fun handleAddTask(task:Task){
    Log.e("ProjectManagerImp","handleAddTask")
    val projectCacheName = if(task.data != null) task.projectName!! else task.uri?.lastPathSegment?:"unknown"
    val ins = task.uri?.let { context.contentResolver.openInputStream(it) }?: task.data?.inputStream()?: return
    val projectCacheDir = ensureProjectCacheDir(projectCacheName)
    unzipProjectToTarget(ins,projectCacheDir)
    ins.close()
    val configure = checkProjectInfo(projectCacheDir)
    if(configure == null){
      projectCacheDir.deleteRecursively()
      task.callback?.onResult("invalid project")
      return
    }
    configure.version = configure.version ?: "v1.0"
    moveToProjectDir(projectCacheDir,configure)
    updateUseInfo(configure)
    updateProjectInfo(configure)
    notifyProjectChanged()
  }

  private fun removeProjectFile(name: String,version: String?){
    var dir = File(projectDir,name)
    if(version != null)
      dir = File(dir,version)
    if (dir.exists()){
      dir.deleteRecursively()
    }
  }

  private fun handleRemoveTask(task:Task){
    val project = projects[task.projectName] ?: return
    var version = task.version
    if(project.versions.size <=1){
      version = null
    }
    removeProjectFile(task.projectName!!,version)
    if (version != null) {
      project.versions.remove(project.versions.find { it.name == version })
      if (project._lastActionVersion == version) {
        project._lastActionVersion = ProjectInfoImp.newestVersion(project.versions.toList())
      }
      val oneUseInfo = useInfo.find { it.name == task.projectName && it.version == version }
      if (oneUseInfo != null) {
        oneUseInfo.version = project._lastActionVersion
      }
    } else {
      projects.remove(task.projectName)
      useInfo.remove(useInfo.find{it.name ==task.projectName})
    }
    saveConfigure(useInfo)
    notifyProjectChanged()
  }

  private fun handleUpdateTask(task:Task){
    val project = projects[task.projectName] ?: return
    val version = task.version ?: return
    project.versions.find { it.name == version } ?: return
    project._lastActionVersion = version
    useInfo.find { it.name == task.projectName }?.let {
      it.version = version
    }
    saveConfigure(useInfo)
    notifyProjectChanged()
  }

  private fun loopHandleTask(){
    while (true){
      try {
        val task = tasks.take()
        when(task.type){
          Task.Type.kAdd -> handleAddTask(task)
          Task.Type.kRemove -> handleRemoveTask(task)
          Task.Type.kUpdate -> handleUpdateTask(task)
        }
      }catch (e:InterruptedException){
        break
      }
    }
  }


  private val running = AtomicBoolean(false)

  fun init(context: Context,rootDir: String){
    if(running.compareAndSet(false,true)){
      thread = Thread {
        try{
          Log.d(TAG,"init")
          rawInit(context,rootDir)
          loopHandleTask()
        }finally {
          Log.d(TAG,"exit")
          running.set(false)
        }
      }
      thread?.start()
    }
  }

  override fun attach(observer: ProjectManager.ProjectObserver): List<ProjectInfo> {
    observers.add(observer)
    return getAllProjects()
  }

  override fun detach(observer: ProjectManager.ProjectObserver) {
    observers.remove(observer)
  }

  override fun getAllProjects(): List<ProjectInfo> {
    val result = arrayListOf<ProjectInfo>()
    for(useInfo in useInfo){
      val project = projects[useInfo.name] ?: continue
      result.add(project)
    }
    return result
  }

  override fun removeProject(projectName: String, version: String?) {
    val task  = Task(Task.Type.kRemove)
    task.version = version
    task.projectName = projectName
    tasks.add(task)
  }

  override fun addProject(uri: Uri) {
    val task = Task(Task.Type.kAdd)
    task.uri = uri
    tasks.add(task)
  }

  override fun addProject(projectName: String, data: ByteArray) {
    val task = Task(Task.Type.kAdd)
    task.projectName = projectName
    task.data = data
    tasks.add(task)
  }

  override fun updateLastActionVersion(projectName: String, version: String) {
    val task = Task(Task.Type.kUpdate)
    task.projectName = projectName
    task.version = version
    tasks.add(task)
  }

  override fun startProjectActivity(context: Context, projectName: String, version: String) {
    val project = projects[projectName] ?: return
    val versionInfo = project.versions.find { it.name == version } ?: return
    val sep = File.separator
    Configure.rootDir = projectDir + sep + projectName + sep + versionInfo.name +sep
    val uiDir = Configure.uiDir
    val intent = Intent(context, ProjectActivity::class.java)
    val initData = InitData("${uiDir}main.lua")
    initData.rootPath = uiDir
    initData.preloadScripts = arrayOf("${uiDir}init.lua")
    val map = HashMap<String,String>()
    map["ROOT_DIR"] = uiDir
    map["RESOURCE_DIR"]= Configure.resDir
    initData.extras = map
    intent.putExtras( MLSBundleUtils.createBundle(initData))
    intent.putExtra("LUA_UI_INIT_DATA",initData)
    val backendDir = Configure.backendDir
    intent.putExtra("LUA_MAIN_DIR",Configure.rootDir)
    context.startActivity(intent)
  }

  private class ProjectInfoImp(override val name: String,
                               override val versions: MutableSet<ProjectInfo.VersionInfo>) : ProjectInfo {

    var _lastActionVersion: String? = null
    override fun getLastActionVersion(): String {
      return _lastActionVersion ?: newestVersion(versions.toList())
    }

    override fun equals(other: Any?): Boolean {
      if (other is ProjectInfo) {
        return name == other.name
      }
      return false
    }

    override fun hashCode(): Int {
      return name.hashCode()
    }

    companion object {
      fun newestVersion(versions:List<ProjectInfo.VersionInfo>):String{
        return versions.maxByOrNull { it.name }?.name ?: ""
      }
    }
  }

}