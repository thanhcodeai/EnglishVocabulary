plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.englishvocabulary"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.englishvocabulary"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    bundle { language { enableSplit = false } }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            val testHome = layout.buildDirectory.dir("test-home").get().asFile
            testHome.mkdirs()
            it.systemProperty("user.home", testHome.absolutePath)
            it.maxHeapSize = "2g"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // MVVM: ViewModel và LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Unit Test
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.16.1")

    // Instrumentation Test
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}