import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

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

// Temporary RootEncoder GL-matrix diagnostic. Disable with
// -ProotEncoderGlDiagnostic=false to use the published Maven/JitPack dependency.
val rootEncoderGlDiagnostic = providers.gradleProperty("rootEncoderGlDiagnostic")
    .orNull
    ?.toBooleanStrictOrNull() ?: false

if (rootEncoderGlDiagnostic) {
    val cacheRoot = file("$rootDir/.gradle/rootencoder-gl-diagnostic")
    val rootEncoderDir = File(cacheRoot, "RootEncoder-2.6.1")
    val sourceFile = File(
        rootEncoderDir,
        "encoder/src/main/java/com/pedro/encoder/input/gl/render/CameraRender.java"
    )

    if (!File(rootEncoderDir, "settings.gradle.kts").isFile || !sourceFile.isFile) {
        cacheRoot.deleteRecursively()
        check(cacheRoot.mkdirs()) { "Cannot create RootEncoder diagnostic cache: $cacheRoot" }
        val archive = File(cacheRoot, "rootencoder-2.6.1.zip")
        URL("https://github.com/pedroSG94/RootEncoder/archive/refs/tags/2.6.1.zip")
            .openStream().use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = File(cacheRoot, entry.name)
                val canonicalRoot = cacheRoot.canonicalFile
                check(output.canonicalFile.toPath().startsWith(canonicalRoot.toPath())) {
                    "Unsafe path in RootEncoder archive: ${entry.name}"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        archive.delete()
    }

    check(sourceFile.isFile) {
        "RootEncoder 2.6.1 CameraRender.java was not found after extracting the source archive."
    }

    var source = sourceFile.readText()
    if (!source.contains("ROOTENCODER_GL_MATRIX_DIAGNOSTIC")) {
        check(source.contains("surfaceTexture.getTransformMatrix(STMatrix);")) {
            "Cannot instrument CameraRender.java: expected matrix read was not found."
        }
        source = source
            .replace(
                "import android.os.Build;",
                "import android.os.Build;\nimport android.util.Log;"
            )
            .replace(
                "import java.nio.ByteOrder;",
                "import java.nio.ByteOrder;\nimport java.util.Arrays;"
            )
            .replace(
                "  private SurfaceTexture surfaceTexture;",
                """  private SurfaceTexture surfaceTexture;

  // ROOTENCODER_GL_MATRIX_DIAGNOSTIC: temporary GL-thread frame instrumentation.
  private static final String DIAGNOSTIC_TAG = "CameraPreviewDiag";
  private static final long MAX_DIAGNOSTIC_FRAMES = 120;
  private long diagnosticFrameCount = 0;"""
            )
            .replace(
                "    surfaceTexture.getTransformMatrix(STMatrix);",
                """    surfaceTexture.getTransformMatrix(STMatrix);
    logConsumedFrameMatrix();"""
            )
            .replace(
                "  public void updateTexImage() {",
                """  /**
   * Runs in draw(), after RootEncoder's GL path has updated the SurfaceTexture.
   * STMatrix is the matrix passed to the shader for this camera frame.
   */
  private void logConsumedFrameMatrix() {
    if (diagnosticFrameCount >= MAX_DIAGNOSTIC_FRAMES) return;
    diagnosticFrameCount++;
    Log.d(DIAGNOSTIC_TAG,
        "GL_FRAME"
            + " frame=" + diagnosticFrameCount
            + " thread=" + Thread.currentThread().getName()
            + " timestampNs=" + surfaceTexture.getTimestamp()
            + " size=" + width + "x" + height
            + " surfaceTextureMatrix=" + Arrays.toString(STMatrix));
  }

  public void updateTexImage() {"""
            )
        sourceFile.writeText(source)
    }

    includeBuild(rootEncoderDir.absolutePath) {
        dependencySubstitution {
            substitute(module("com.github.pedroSG94.RootEncoder:library"))
                .using(project(":library"))
        }
    }
}
