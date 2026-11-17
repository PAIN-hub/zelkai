plugins {
    id("com.android.library")
}

android {
    namespace = "com.arm.aichat"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_APP=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_BUILD_TESTS=OFF"
                arguments += "-DLLAMA_BUILD_TOOLS=OFF"
                arguments += "-DLLAMA_BUILD_EXAMPLES=OFF"
                arguments += "-DLLAMA_BUILD_SERVER=OFF"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DLLAMA_CURL=OFF"

                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=OFF"
                arguments += "-DGGML_CPU_ALL_VARIANTS=OFF"
                arguments += "-DGGML_CPU_KLEIDIAI=OFF"
                arguments += "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16"
                arguments += "-DGGML_LLAMAFILE=OFF"
                arguments += "-DGGML_OPENMP=OFF"
                arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                cFlags("-O3", "-DNDEBUG")
                cppFlags("-O3", "-DNDEBUG")
            }
        }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                    cFlags("-O3", "-DNDEBUG")
                    cppFlags("-O3", "-DNDEBUG")
                }
            }
        }
        release {
            isMinifyEnabled = false
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                    cFlags("-O3", "-DNDEBUG")
                    cppFlags("-O3", "-DNDEBUG")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
