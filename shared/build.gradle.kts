import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

fun sdkPath(sdk: String): String =
    providers.exec {
        commandLine("xcrun", "--sdk", sdk, "--show-sdk-path")
    }.standardOutput.asText.get().trim()

val headerDir = project.file("src/nativeInterop/cinterop")
val keychainSrc = project.file("src/nativeInterop/cinterop/KeychainHelper.m")

fun registerKeychainLibTasks(label: String, sdk: String): TaskProvider<Exec> {
    val libDir = layout.buildDirectory.dir("keychainLib/$label").get().asFile
    val objFile = File(libDir, "KeychainHelper.o")
    val libFile = File(libDir, "libKeychainHelper.a")

    val compile = tasks.register<Exec>("compileKeychain_$label") {
        inputs.file(keychainSrc)
        outputs.file(objFile)
        doFirst { libDir.mkdirs() }
        commandLine(
            "xcrun", "clang",
            "-fobjc-arc",
            "-arch", "arm64",
            "-isysroot", sdkPath(sdk),
            "-I", headerDir.absolutePath,
            "-c", keychainSrc.absolutePath,
            "-o", objFile.absolutePath
        )
    }

    return tasks.register<Exec>("archiveKeychain_$label") {
        dependsOn(compile)
        inputs.file(objFile)
        outputs.file(libFile)
        commandLine("ar", "rcs", libFile.absolutePath, objFile.absolutePath)
    }
}

val arm64Archive = registerKeychainLibTasks("iosArm64", "iphoneos")
val simArchive   = registerKeychainLibTasks("iosSimulatorArm64", "iphonesimulator")

fun libFile(label: String) =
    layout.buildDirectory.file("keychainLib/$label/libKeychainHelper.a").get().asFile

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts(libFile("iosArm64").absolutePath)
        }
        compilations.getByName("main") {
            cinterops {
                val KeychainHelper by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/KeychainHelper.def"))
                    includeDirs(headerDir)
                }
            }
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts(libFile("iosSimulatorArm64").absolutePath)
        }
        compilations.getByName("main") {
            cinterops {
                val KeychainHelper by creating {
                    definitionFile.set(project.file("src/nativeInterop/cinterop/KeychainHelper.def"))
                    includeDirs(headerDir)
                }
            }
        }
    }

    androidLibrary {
        namespace = "io.github.froyder.biometricauthenticator.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }

    sourceSets {
        androidMain.dependencies {
            implementation("androidx.biometric:biometric:1.1.0")
            implementation("androidx.fragment:fragment-ktx:1.8.5")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// Wire: cinterop and link tasks must wait for the static library to be built
afterEvaluate {
    tasks.named("cinteropKeychainHelperIosArm64") { dependsOn(arm64Archive) }
    tasks.named("cinteropKeychainHelperIosSimulatorArm64") { dependsOn(simArchive) }
    tasks.matching {
        it.name.startsWith("linkDebugFrameworkIosArm64") ||
                it.name.startsWith("linkReleaseFrameworkIosArm64")
    }.configureEach { dependsOn(arm64Archive) }
    tasks.matching {
        it.name.startsWith("linkDebugFrameworkIosSimulatorArm64") ||
                it.name.startsWith("linkReleaseFrameworkIosSimulatorArm64")
    }.configureEach { dependsOn(simArchive) }
}