import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.plugins.signing.SigningExtension

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.vanniktech.publish)
    id("signing")
}

val headerDir = project.file("src/nativeInterop/cinterop")
val keychainSrc = project.file("src/nativeInterop/cinterop/KeychainHelper.m")

fun sdkPath(sdk: String): String =
    providers.exec {
        commandLine("xcrun", "--sdk", sdk, "--show-sdk-path")
    }.standardOutput.asText.get().trim()

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
val simArchive = registerKeychainLibTasks("iosSimulatorArm64", "iphonesimulator")

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    iosArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        compilations.getByName("main") {
            cinterops.create("KeychainHelper") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/KeychainHelper.def"))
                includeDirs(headerDir)
            }
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        compilations.getByName("main") {
            cinterops.create("KeychainHelper") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/KeychainHelper.def"))
                includeDirs(headerDir)
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
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        androidMain.dependencies {
            implementation("androidx.biometric:biometric:1.1.0")
            implementation("androidx.fragment:fragment-ktx:1.8.5")
        }
        iosMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

afterEvaluate {
    tasks.named("cinteropKeychainHelperIosArm64") { dependsOn(arm64Archive) }
    tasks.named("cinteropKeychainHelperIosSimulatorArm64") { dependsOn(simArchive) }
}

val localProps = gradleLocalProperties(rootDir, providers)
group = "io.github.froyder"
version = "1.0.0"

val signingKeyId = localProps.getProperty("signing.keyId") ?: ""
val signingPassword = localProps.getProperty("signing.password") ?: ""
val signingSecretKeyFile = localProps.getProperty("signing.secretKeyFile") ?: ""

if (signingKeyId.isNotEmpty() && signingSecretKeyFile.isNotEmpty()) {
    extensions.configure<SigningExtension> {
        useInMemoryPgpKeys(
            signingKeyId,
            file(signingSecretKeyFile).readText(),
            signingPassword
        )
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(
        groupId = "io.github.froyder",
        artifactId = "kmp-biometric-auth",
        version = "1.0.0"
    )

    pom {
        name = "KMP Biometric Auth"
        description = "Kotlin Multiplatform biometric authentication library with hardware-backed crypto. Keystore-bound AES/GCM on Android, Keychain with biometryCurrentSet on iOS."
        url = "https://github.com/Froyder/kmp-biometric-auth"

        licenses {
            license {
                name = "Apache License 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        developers {
            developer {
                id = "froyder"
                name = "Ilia Khomutskikh"
                email = "homutskih@gmail.com"
            }
        }

        scm {
            url = "https://github.com/Froyder/kmp-biometric-auth"
            connection = "scm:git:git://github.com/Froyder/kmp-biometric-auth.git"
            developerConnection = "scm:git:ssh://git@github.com/Froyder/kmp-biometric-auth.git"
        }
    }
}