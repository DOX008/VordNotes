plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "com.mhss.app.ai.presentation"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    android {
        packaging {
            resources {
                // Samma stil: en rad per mönster med {…}-gruppering
                excludes.add("/META-INF/{AL2.0,LGPL2.1}")
                excludes.add("/META-INF/{LICENSE,LICENSE.txt,LICENSE.md}")
                excludes.add("/META-INF/{NOTICE,NOTICE.txt,NOTICE.md}")
                excludes.add("/META-INF/{DEPENDENCIES,DEPENDENCY,DEPENDENCY.txt}")
                excludes.add("/META-INF/{INDEX.LIST,INDEX}")
                // (valfritt men vanligt)
                excludes.add("/META-INF/{CHANGES,CHANGELOG,CHANGELOG.md}")
                excludes.add("/META-INF/{README,README.txt,README.md}")
            }
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
        compose = true
    }
    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {

    implementation(project(":ai:domain"))
    implementation(project(":notes:domain"))
    implementation(project(":tasks:domain"))
    implementation(project(":calendar:domain"))
    implementation(project(":core:util"))

    implementation(project(":core:ui"))
    implementation(project(":core:preferences"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.koin.bom))
    implementation(libs.bundles.koin)
    implementation(libs.koin.android)
    ksp(libs.koin.ksp.compiler)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.squircle.shape)

    implementation("com.github.yalantis:ucrop:2.2.8")
    implementation("androidx.compose.foundation:foundation")
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
}