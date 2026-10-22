# CameraX auto-exposure source

`AutoExposureCameraXSource` is a deliberately vendored copy of RootEncoder 2.8.1's `CameraXSource`:

- Upstream source: https://github.com/pedroSG94/RootEncoder/blob/2.8.1/extra-sources/src/main/java/com/pedro/extrasources/CameraXSource.kt
- Upstream license: Apache-2.0 (the copyright and license header is retained in the source file).
- Why the full class is copied: upstream keeps its `Preview` builder and bound `Camera` private and constructs the preview internally. There is no supported public hook to apply Camera2 capture-request options before binding. A small wrapper cannot set those options without reflection or a separate camera pipeline.
- Local change: when the preview is built, `Camera2Interop` explicitly requests `CONTROL_MODE_AUTO` and `CONTROL_AE_MODE_ON`, matching the approach used in the original camera implementation (PR #4).
- Keep in sync: when upgrading RootEncoder, compare this file with the upstream version and reapply the small Camera2Interop change. Avoid unrelated local modifications.
- Scope: this requests automatic exposure through Camera2; only testing on the target phone can confirm that the camera driver actually honors the request.
