#!/usr/bin/env bash
set -euo pipefail

DEST="third_party/RootEncoder-2.6.1"
if [[ -f "$DEST/library/build.gradle.kts" && -f "$DEST/encoder/src/main/java/com/pedro/encoder/input/gl/render/CameraRender.java" && -f "gradle/libs.versions.toml" ]]; then
  echo "RootEncoder sources already vendored."
  exit 0
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
curl --fail --location --retry 3 \
  https://github.com/pedroSG94/RootEncoder/archive/refs/tags/2.6.1.zip \
  --output "$TMP/rootencoder.zip"
unzip -q "$TMP/rootencoder.zip" -d "$TMP"

mkdir -p third_party
cp -a "$TMP/RootEncoder-2.6.1" "$DEST"
mkdir -p gradle
cp "$DEST/gradle/libs.versions.toml" gradle/libs.versions.toml
python3 - <<'PY'
from pathlib import Path
catalog = Path("gradle/libs.versions.toml")
text = catalog.read_text()
text = text.replace('agp = "8.6.1"', 'agp = "8.7.3"')
text = text.replace('kotlin = "2.1.20"', 'kotlin = "2.0.21"')
catalog.write_text(text)

source = Path("third_party/RootEncoder-2.6.1/encoder/src/main/java/com/pedro/encoder/input/gl/render/CameraRender.java")
text = source.read_text()
if "ROOTENCODER_GL_MATRIX_DIAGNOSTIC" not in text:
    text = text.replace("import android.os.Build;", "import android.os.Build;\nimport android.util.Log;")
    text = text.replace("import java.nio.ByteOrder;", "import java.nio.ByteOrder;\nimport java.util.Arrays;")
    needle = "  private SurfaceTexture surfaceTexture;"
    replacement = """  private SurfaceTexture surfaceTexture;

  // ROOTENCODER_GL_MATRIX_DIAGNOSTIC: logs the matrix consumed by the GL renderer.
  private static final String DIAGNOSTIC_TAG = "CameraPreviewDiag";
  private static final long MAX_DIAGNOSTIC_FRAMES = 120;
  private long diagnosticFrameCount = 0;"""
    if needle not in text:
        raise SystemExit("Could not locate SurfaceTexture field in CameraRender.java")
    text = text.replace(needle, replacement, 1)
    needle = "    surfaceTexture.getTransformMatrix(STMatrix);"
    replacement = """    surfaceTexture.getTransformMatrix(STMatrix);
    logConsumedFrameMatrix();"""
    if needle not in text:
        raise SystemExit("Could not locate SurfaceTexture matrix read in CameraRender.java")
    text = text.replace(needle, replacement, 1)
    needle = "  public void updateTexImage() {"
    replacement = """  /** Logs the SurfaceTexture matrix immediately after RootEncoder reads it on the GL thread. */
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
    if needle not in text:
        raise SystemExit("Could not locate updateTexImage() in CameraRender.java")
    text = text.replace(needle, replacement, 1)
    source.write_text(text)
PY

# Keep the upstream license alongside the vendored source.
test -f "$DEST/LICENSE.txt"
