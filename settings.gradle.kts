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
include(":common", ":encoder", ":rtmp", ":rtsp", ":srt", ":udp", ":library")

// RootEncoder 2.6.1 is vendored in the repository so Gradle never downloads or
// patches upstream sources during configuration.
project(":common").projectDir = file("third_party/RootEncoder-2.6.1/common")
project(":encoder").projectDir = file("third_party/RootEncoder-2.6.1/encoder")
project(":rtmp").projectDir = file("third_party/RootEncoder-2.6.1/rtmp")
project(":rtsp").projectDir = file("third_party/RootEncoder-2.6.1/rtsp")
project(":srt").projectDir = file("third_party/RootEncoder-2.6.1/srt")
project(":udp").projectDir = file("third_party/RootEncoder-2.6.1/udp")
project(":library").projectDir = file("third_party/RootEncoder-2.6.1/library")
