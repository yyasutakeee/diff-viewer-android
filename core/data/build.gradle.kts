import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    id("com.android.library")
}

val rustManifest = layout.projectDirectory.file("src/main/rust/Cargo.toml")
val rustSources = layout.projectDirectory.dir("src/main/rust/src")
val rustJniLibraries = layout.buildDirectory.dir("generated/rustJniLibs")
val rustCargoTarget = layout.buildDirectory.dir("rust-target")

android {
    namespace = "com.example.diffviewer.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

abstract class BuildRustAndroidTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @get:InputFile
    abstract val lockFile: RegularFileProperty

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val cargoTargetDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        execOperations.exec {
            workingDir(manifestFile.get().asFile.parentFile)
            environment("CARGO_TARGET_DIR", cargoTargetDirectory.get().asFile.absolutePath)
            commandLine(
                "cargo", "ndk", "--platform", "23",
                "--target", "arm64-v8a",
                "--target", "armeabi-v7a",
                "--target", "x86_64",
                "--output-dir", outputDirectory.get().asFile.absolutePath,
                "build", "--release", "--locked",
            )
        }
    }
}

val buildRustAndroid = tasks.register<BuildRustAndroidTask>("buildRustAndroid") {
    group = "build"
    description = "Builds the read-only libgit2 JNI library for the supported Android ABIs."
    manifestFile.set(rustManifest)
    lockFile.set(layout.projectDirectory.file("src/main/rust/Cargo.lock"))
    sourceDirectory.set(rustSources)
    outputDirectory.set(rustJniLibraries)
    cargoTargetDirectory.set(rustCargoTarget)
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            buildRustAndroid,
            BuildRustAndroidTask::outputDirectory,
        )
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
