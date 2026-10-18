pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GPT-Android-Miroir-3-TZZ1AC0"

include(":app")

// By default, use the checked-in RootEncoder 2.6.1 source tree so the temporary
// GL diagnostic can be compiled reproducibly without downloading or patching sources.
// Pass -ProotEncoderGlMatrixDiagnostic=false to use the published Maven artifact.
val rootEncoderGlMatrixDiagnostic =
    providers.gradleProperty("rootEncoderGlMatrixDiagnostic").orNull?.toBoolean() != false

if (rootEncoderGlMatrixDiagnostic) {
    include(":common", ":encoder", ":rtmp", ":rtsp", ":srt", ":udp", ":library")

    project(":common").projectDir = file("third_party/RootEncoder-2.6.1/common")
    project(":encoder").projectDir = file("third_party/RootEncoder-2.6.1/encoder")
    project(":rtmp").projectDir = file("third_party/RootEncoder-2.6.1/rtmp")
    project(":rtsp").projectDir = file("third_party/RootEncoder-2.6.1/rtsp")
    project(":srt").projectDir = file("third_party/RootEncoder-2.6.1/srt")
    project(":udp").projectDir = file("third_party/RootEncoder-2.6.1/udp")
    project(":library").projectDir = file("third_party/RootEncoder-2.6.1/library")
}
