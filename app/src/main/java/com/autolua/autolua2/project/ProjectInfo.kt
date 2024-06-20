package com.autolua.autolua2.project

interface ProjectInfo {
  val name:String
  data class VersionInfo(val name:String, val description: String)
  val versions:Set<VersionInfo>
  fun getLastActionVersion():String
  override fun equals(other: Any?): Boolean
  override fun hashCode(): Int
}
