# CameraX automatic exposure in the RTSP preview

The production app uses `FrontCameraXSource` to provide camera frames to RootEncoder. The
source now binds both `Preview` and `ImageAnalysis`, and explicitly requests
`CONTROL_MODE_AUTO` plus `CONTROL_AE_MODE_ON` on both use cases.

## Why

On the target phone, the Preview-only pipeline could remain underexposed even though the
capture request asked for automatic exposure. The validated diagnostic source converged
when an ImageAnalysis use case was also bound and requested automatic exposure.

The analyzer deliberately closes every image immediately; it does not retain frames or
perform image processing. `STRATEGY_KEEP_ONLY_LATEST` avoids building up a queue.

## Scope

- Preserves the existing Compose UI and RTSP server implementation.
- Does not change RootEncoder or RTSP-Server dependency versions.
- Does not set a fixed camera frame rate, so sensor exposure is not capped by a 30 fps
  capture-request range.
- Requires build/CI verification and a real-device check of brightness, orientation,
  preview lifecycle, and RTSP output before merging.
