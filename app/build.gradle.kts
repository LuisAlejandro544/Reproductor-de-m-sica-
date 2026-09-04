import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  ndkVersion = "26.1.10909125"

  defaultConfig {
    applicationId = "com.aistudio.ritmo.kmqzvd"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    externalNativeBuild {
      cmake {
        arguments("-DANDROID_STL=c++_shared")
        cppFlags("-std=c++20")
      }
    }
    ndk {
      abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  sourceSets {
    getByName("main") {
      jniLibs.srcDirs("src/main/jniLibs")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    prefab = true
  }
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.forkEvery = 1
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.google.oboe)
  implementation(libs.coil.compose)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.timber)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.leakcanary.android)
  implementation(libs.anr.watchdog)
  debugImplementation(libs.pluto)
  releaseImplementation(libs.pluto.no.op)
  debugImplementation(libs.pluto.rooms.db)
  releaseImplementation(libs.pluto.rooms.db.no.op)
  debugImplementation(libs.hyperion.core)
  releaseImplementation(libs.hyperion.core.no.op)
  debugImplementation(libs.hyperion.attr)
  debugImplementation(libs.hyperion.build.config)
  debugImplementation(libs.hyperion.disk)
  debugImplementation(libs.hyperion.shared.preferences)
  debugImplementation(libs.hyperion.crash)
  debugImplementation(libs.hyperion.measurement)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

// Tarea de compilación e integración del módulo nativo en Rust
val compileRust = tasks.register<Exec>("compileRust") {
  description = "Compila el crate nativo en Rust (ritmo_rust) y vincula librerías en jniLibs"
  workingDir = file("src/main/rust")
  commandLine(
    "sh", "-c",
    """
    if command -v cargo >/dev/null 2>&1; then
      echo "=== Compilando Crate Rust ritmo_rust ==="
      cargo build --release
      mkdir -p ../jniLibs/arm64-v8a ../jniLibs/armeabi-v7a ../jniLibs/x86_64
      # Copiar artefactos generados para empaquetado directo en el APK
      if [ -f target/aarch64-linux-android/release/libritmo_rust.so ]; then
        cp target/aarch64-linux-android/release/libritmo_rust.so ../jniLibs/arm64-v8a/
      fi
      if [ -f target/armv7-linux-androideabi/release/libritmo_rust.so ]; then
        cp target/armv7-linux-androideabi/release/libritmo_rust.so ../jniLibs/armeabi-v7a/
      fi
      if [ -f target/x86_64-linux-android/release/libritmo_rust.so ]; then
        cp target/x86_64-linux-android/release/libritmo_rust.so ../jniLibs/x86_64/
      fi
      if [ -f target/release/libritmo_rust.so ]; then
        cp target/release/libritmo_rust.so ../jniLibs/arm64-v8a/ 2>/dev/null || true
        cp target/release/libritmo_rust.so ../jniLibs/x86_64/ 2>/dev/null || true
      fi
    else
      echo "Cargo no detectado en el entorno local, utilizando artefactos nativos enlazados"
    fi
    """.trimIndent()
  )
  isIgnoreExitValue = true
}

tasks.named("preBuild") {
  dependsOn(compileRust)
}

