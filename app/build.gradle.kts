import com.google.protobuf.gradle.proto
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
  id ("com.google.protobuf")
}

android {
  namespace = "com.autolua.autolua2"
  compileSdk = 34


  packagingOptions {
    exclude("META-INF/INDEX.LIST")
    exclude("META-INF/*******")
  }

  defaultConfig {
    applicationId = "com.autolua.autolua2"
    minSdk = 22
    targetSdk = 34
    versionCode = 1
    versionName = "0.0.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets {
    named("main"){
      proto {
        srcDir("src/main/proto")
      }
    }
  }


  val configFile = file("config.properties")
  if(configFile.exists()){
    val configProperties = Properties()
    configFile.inputStream().use {
      configProperties.load(it)
      signingConfigs {
        create("common"){
          storeFile = file(configProperties.getProperty("storeFile"))
          storePassword = configProperties.getProperty("storePassword")
          keyAlias = configProperties.getProperty("keyAlias")
          keyPassword = configProperties.getProperty("keyPassword")
        }
      }
    }
    buildTypes {
      release {
        signingConfig = signingConfigs.getByName("common")
      }
    }

  }


  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
  buildFeatures {
    viewBinding = true
    aidl = true
  }

  applicationVariants.all {
    val variant = this
    outputs.all {
      val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
      output.outputFileName = "autolua2_${variant.versionName}.apk"
    }
  }

}

protobuf {
  protobuf {
    protoc {
      artifact = if (osdetector.os == "osx") {
        "com.google.protobuf:protoc:3.25.3:osx-x86_64"
      } else {
        "com.google.protobuf:protoc:3.25.3"
      }
    }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        create("java") {
          option("lite")
        }
      }
    }
  }
}

dependencies {
  implementation(project(":engine"))
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.activity)
  implementation(libs.protobuf.javalite)
  implementation(libs.androidx.lifecycle.livedata.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.core)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.navigation.fragment.ktx)
  implementation(libs.androidx.navigation.ui.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.gson)
  implementation(libs.netty.all)

  implementation(files("../libs/HotReload_Adapter-release.aar"))
//  releaseImplementation(files("../libs/HotReload_Empty-release.aar"))
  implementation(files("../libs/mlncore-release.aar"))
  implementation(files("../libs/mlnservice-release.aar"))
  implementation(files("../libs/annotation.jar"))
  kapt(files("../libs/processor.jar"))
  implementation(libs.firebase.crashlytics.buildtools)
  implementation(libs.androidx.baselibrary)
  implementation(libs.auto.common)
  implementation(libs.javapoet)
  implementation(libs.glide.glide)
  annotationProcessor(libs.glide.compiler)
  api (libs.androidsvg)
  implementation (libs.kotlin.reflect)
  
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}