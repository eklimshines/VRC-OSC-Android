plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bhaptics.vrc.oscquery"
    compileSdk = 33

    defaultConfig {
        minSdk = 24

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation ("com.google.code.gson:gson:2.10.1")
    implementation ("com.google.android.gms:play-services-nearby:18.0.2")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.2")
    implementation ("com.squareup.okhttp3:okhttp:4.9.1")
    implementation ("org.nanohttpd:nanohttpd:2.3.1")
    implementation("junit:junit:4.13.2")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

afterEvaluate {
    android.libraryVariants.all {
        val variant = this
        val variantName = name.capitalize()

        tasks.register<Copy>("copy${variantName}AarToOutputFolder") {
            from(packageLibraryProvider)
            into(project.rootProject.layout.projectDirectory.dir("outputs"))
            rename { fileName ->
                fileName.replace("-release", "-$variantName")
            }
        }

        assembleProvider.get().finalizedBy("copy${variantName}AarToOutputFolder")
    }
}