import org.gradle.api.tasks.Exec

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

    sourceSets.getByName("main") {
        jniLibs.srcDir(rustJniLibraries)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val buildRustAndroid by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the read-only libgit2 JNI library for the supported Android ABIs."
    inputs.file(rustManifest)
    inputs.file(layout.projectDirectory.file("src/main/rust/Cargo.lock"))
    inputs.dir(rustSources)
    outputs.dir(rustJniLibraries)
    environment("CARGO_TARGET_DIR", rustCargoTarget.get().asFile.absolutePath)
    commandLine(
        "cargo",
        "ndk",
        "--platform",
        "23",
        "--target",
        "arm64-v8a",
        "--target",
        "armeabi-v7a",
        "--target",
        "x86_64",
        "--output-dir",
        rustJniLibraries.get().asFile.absolutePath,
        "build",
        "--release",
        "--locked",
        "--manifest-path",
        rustManifest.asFile.absolutePath,
    )
}

tasks.named("preBuild").configure {
    dependsOn(buildRustAndroid)
}

dependencies {
    implementation(project(":core:domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
