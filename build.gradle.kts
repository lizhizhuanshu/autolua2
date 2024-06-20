// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.android) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.kapt) apply false
}

buildscript {
  dependencies {
    classpath(libs.protobuf.gradle.plugin.v094)
  }
}
