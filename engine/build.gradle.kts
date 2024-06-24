import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.tasks.Copy

import java.util.Locale

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.kotlin.android)
  id("de.undercouch.download") version "4.1.2"
}

val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

var osExt = if(osName == "linux") {
  "linux-x86_64"
} else if(osName.startsWith("windows")) {
  "win32"
}else if(osName == "mac os x") {
  "osx-x86_64"
}else{
  throw Exception("Unsupported OS: $osName")
}

val protocVersion = "25.3"
val protocUrl = "https://github.com/protocolbuffers/protobuf/releases/download/v$protocVersion/protoc-$protocVersion-$osExt.zip"
val protoSrcDir =  layout.projectDirectory.dir("src/main/proto")
val protoOutDir = layout.buildDirectory.dir("generated/source/proto")

// 配置下载 `protoc` 的任务
tasks.register<Download>("downloadProtoc") {
  description = "Downloads the protoc binary zip file."
  group = "customTasks"
  val zipFile = layout.buildDirectory.file("protoc.zip")
  src(protocUrl)
  dest(zipFile)
  overwrite(false)
  onlyIfModified(true)
}

// 配置解压 `protoc` 的任务
tasks.register<Copy>("unzipProtoc") {
  dependsOn("downloadProtoc")
  group = "customTasks"
  description = "Unzips the protoc binary."
  val zipFile = layout.buildDirectory.file("protoc.zip")
  val installDir = layout.buildDirectory.dir("protoc")
  filePermissions {
    dirMode = 0x775
  }
  from(zipTree(zipFile))
  into(installDir)
  doFirst {
    // 确保目标解压目录存在
    mkdir(installDir)
  }
  doLast {
    fileTree(installDir).visit {
      if(this.name == "protoc" || this.name == "protoc.exe") {
        // 设置可执行权限
        file.setExecutable(true)
      }
    }
  }
}

tasks.register<Exec>("generateProto") {
  dependsOn("unzipProtoc")
  group = "customTasks"
  description = "Generates Cpp source files from proto files."
  val protoc = layout.buildDirectory.file("protoc/bin/protoc").get().asFile.absolutePath
  val protoFiles = fileTree(protoSrcDir) {
    include("**/*.proto")
  }

  val protoOut = protoOutDir.get().asFile.absolutePath

  doFirst {
    mkdir(protoOutDir)
    mkdir("$protoOut/java")
    mkdir("$protoOut/cpp")
  }


  val protoPath = protoSrcDir
  val protoPathArg = "--proto_path=$protoPath"
  val protoOutArg = "--cpp_out=$protoOut/cpp"
  val javaOutArg = "--java_out=lite:$protoOut/java"
  val protoFilesArg = protoFiles.files.map { it.absolutePath }
  val args = listOf(protoPathArg, protoOutArg,javaOutArg) + protoFilesArg
  println("protoc: $protoc")
  println("args: ${args.toTypedArray().joinToString(" ")}")
  commandLine(protoc, *args.toTypedArray())
}

tasks.named("preBuild") {
  dependsOn("generateProto")
}

android {
  namespace = "com.autolua.engine"
  compileSdk = 34

  sourceSets {
    getByName("main"){
      java.srcDirs("src/main/java","build/generated/source/proto/java")
    }
  }




  defaultConfig {
    minSdk = 22


    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
    externalNativeBuild {
      cmake {
        arguments ("-DANDROID_STL=c++_shared")
        cppFlags ("-frtti"," -fexceptions","-std=c++17")
        cFlags ("-fPIE"," -fexceptions")
        abiFilters ("armeabi-v7a", "arm64-v8a","x86","x86_64")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }


  externalNativeBuild {
    cmake {
      path("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }

  ndkVersion ="25.2.9519653"
}



dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.gson)
  implementation(libs.protobuf.javalite)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}




