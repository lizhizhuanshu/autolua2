package com.autolua.autolua2.project

import android.content.Context
import android.net.Uri

interface ProjectManager {
  interface ProjectObserver{
    fun onProjectChanged()
  }
  fun attach(observer:ProjectObserver):List<ProjectInfo>
  fun detach(observer:ProjectObserver)
  fun getAllProjects():List<ProjectInfo>
  fun removeProject(projectName:String, version:String?)
  fun addProject(uri: Uri)
  fun addProject(projectName:String,data:ByteArray)
  fun updateLastActionVersion(projectName: String, version: String)
  fun startProjectActivity(context: Context, projectName: String, version: String)
}