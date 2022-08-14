plugins {
    kotlin("multiplatform") version "1.7.10"
}

group = "com.drfeederino"
version = "0.2.0"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }
    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val sdl2 by creating {
                    includeDirs("$projectDir/include/SDL2")
                }
                val zlib by creating {
                    includeDirs("$projectDir/include/")
                }
                val openal by creating {
                    includeDirs("$projectDir/include/AL/")
                }
            }
        }
        binaries {
            executable {
                entryPoint = "neo.win_main.main"
            }
        }
    }
    sourceSets {
        val nativeMain by getting
        val nativeTest by getting
    }

}
