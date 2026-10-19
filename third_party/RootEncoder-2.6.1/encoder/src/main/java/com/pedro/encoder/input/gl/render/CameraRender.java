/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.encoder.input.gl.render;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import com.pedro.encoder.R;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.encoder.utils.gl.GlUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Created by pedro on 29/01/18.
 */

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CameraRender extends BaseRenderOffScreen {

  private final int[] textureID = new int[1];
  private final float[] rotationMatrix = new float[16];
  private final float[] scaleMatrix = new float[16];

  private int program = -1;
  private int uMVPMatrixHandle = -1;
  private int uSTMatrixHandle = -1;
  private int aPositionHandle = -1;
  private int aTextureCameraHandle = -1;

  private SurfaceTexture surfaceTexture;

  // ROOTENCODER_GL_MATRIX_DIAGNOSTIC: logs the matrix consumed by the GL renderer.
  private static final String DIAGNOSTIC_TAG = "CameraPreviewDiag";
  private static final long MAX_DIAGNOSTIC_FRAMES = 120;
  private long diagnosticFrameCount = 0;
  private Surface surface;

  public CameraRender() {
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.setIdentityM(STMatrix, 0);
    float[] vertex = CameraHelper.getVerticesData();
    squareVertex = ByteBuffer.allocateDirect(vertex.length * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    squareVertex.put(vertex).position(0);
    setRotation(0);
    setFlip(false, false);
  }

  @Override
  public void initGl(int width, int height, Context context, int previewWidth,
      int previewHeight) {
    this.width = width;
    this.height = height;
    GlUtil.checkGlError("initGl start");
    String vertexShader = GlUtil.getStringFromRaw(context, R.raw.simple_vertex);
    String fragmentShader = GlUtil.getStringFromRaw(context, R.raw.camera_fragment);

    program = GlUtil.createProgram(vertexShader, fragmentShader);
    aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    aTextureCameraHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
    uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
    uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");

    //camera texture
    GlUtil.createExternalTextures(textureID.length, textureID, 0);
    surfaceTexture = new SurfaceTexture(textureID[0]);
    surfaceTexture.setDefaultBufferSize(width, height);
    surface = new Surface(surfaceTexture);
    initFBO(width, height);
    GlUtil.checkGlError("initGl end");
  }

  @Override
  public void draw() {
    GlUtil.checkGlError("drawCamera start");
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderHandler.getFboId()[0]);

    surfaceTexture.getTransformMatrix(STMatrix);
    logConsumedFrameMatrix();
    GLES20.glViewport(0, 0, width, height);
    logFirstRenderGeometry();
    GLES20.glUseProgram(program);
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

    squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aPositionHandle);

    squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET);
    GLES20.glVertexAttribPointer(aTextureCameraHandle, 2, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex);
    GLES20.glEnableVertexAttribArray(aTextureCameraHandle);

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
    GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
    //camera
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID[0]);
    //draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    GlUtil.disableResources(aTextureCameraHandle, aPositionHandle);
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    GlUtil.checkGlError("drawCamera end");
  }

  @Override
  public void release() {
    GLES20.glDeleteProgram(program);
    if (surfaceTexture != null) surfaceTexture.release();
    if (surface != null) surface.release();
  }

  /**
   * One-shot diagnostic for the first camera frame before preview/encoder fan-out.
   * Reports the exact GL input geometry and the four UV corners after SurfaceTexture's
   * transform, making rotation/crop/scale visible without changing rendering behavior.
   */
  private boolean firstRenderGeometryLogged = false;

  private void logFirstRenderGeometry() {
    if (firstRenderGeometryLogged) return;
    firstRenderGeometryLogged = true;

    int[] viewport = new int[4];
    GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0);
    float[] uvCorners = {
        0f, 0f, 0f, 1f,
        1f, 0f, 0f, 1f,
        0f, 1f, 0f, 1f,
        1f, 1f, 0f, 1f
    };
    float[] transformed = new float[uvCorners.length];
    for (int i = 0; i < 4; i++) {
      int offset = i * 4;
      float u = uvCorners[offset];
      float v = uvCorners[offset + 1];
      transformed[offset] = STMatrix[0] * u + STMatrix[4] * v + STMatrix[12];
      transformed[offset + 1] = STMatrix[1] * u + STMatrix[5] * v + STMatrix[13];
      transformed[offset + 2] = 0f;
      transformed[offset + 3] = 1f;
    }
    Log.d(DIAGNOSTIC_TAG,
        "FIRST_RENDER_GEOMETRY"
            + " timestampNs=" + surfaceTexture.getTimestamp()
            + " fbo=" + width + "x" + height
            + " glViewport=" + viewport[0] + "," + viewport[1] + ","
            + viewport[2] + "x" + viewport[3]
            + " rawUvCorners=" + Arrays.toString(uvCorners)
            + " transformedUvCorners=" + Arrays.toString(transformed)
            + " stMatrix=" + Arrays.toString(STMatrix)
            + " mvpMatrix=" + Arrays.toString(MVPMatrix));
  }

  /** Logs the SurfaceTexture matrix immediately after RootEncoder reads it on the GL thread. */
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

  public void updateTexImage() {
    surfaceTexture.updateTexImage();
  }

  public SurfaceTexture getSurfaceTexture() {
    return surfaceTexture;
  }

  public Surface getSurface() {
    return surface;
  }

  public void setRotation(int rotation) {
    Matrix.setIdentityM(rotationMatrix, 0);
    Matrix.rotateM(rotationMatrix, 0, rotation, 0f, 0f, -1f);
    update();
  }

  public void setFlip(boolean isFlipHorizontal, boolean isFlipVertical) {
    Matrix.setIdentityM(scaleMatrix, 0);
    Matrix.scaleM(scaleMatrix, 0, isFlipHorizontal ? -1f : 1f, isFlipVertical ? -1f : 1f, 1f);
    update();
  }

  private void update() {
    Matrix.setIdentityM(MVPMatrix, 0);
    Matrix.multiplyMM(MVPMatrix, 0, scaleMatrix, 0, MVPMatrix, 0);
    Matrix.multiplyMM(MVPMatrix, 0, rotationMatrix, 0, MVPMatrix, 0);
  }
}
