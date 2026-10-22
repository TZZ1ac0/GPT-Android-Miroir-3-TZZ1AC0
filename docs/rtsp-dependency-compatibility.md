# RTSP dependency compatibility

## Chosen dependency line

- RootEncoder `library` and `extra-sources`: `2.8.1`, matching the device-tested CameraX baseline.
- RTSP-Server: `1.4.2`.
- CameraX: `1.4.2`.

## Why RTSP-Server 1.3.6 is not retained

The `1.3.6` RTSP-Server version declares RootEncoder `2.6.1` in its version catalog and build README. That is the version line used by the old `main` branch, not the RootEncoder `2.8.1` camera baseline.

RTSP-Server `1.4.2` declares RootEncoder `2.8.0` and updates its Ktor/coroutines dependency line. We explicitly pin RootEncoder to `2.8.1` so the camera implementation and RTSP integration share the selected RootEncoder version. This is a reasoned compatibility choice based on upstream dependency declarations, not yet a confirmed successful build or runtime test.

## Verification still required

1. Run the Gradle CI/build on this reconstruction branch and inspect the resolved dependency graph, especially that only RootEncoder `2.8.1` is selected.
2. If resolution or compilation fails, do not alter the validated camera source to work around it; first determine whether RTSP-Server `1.4.2` can be used with RootEncoder `2.8.1` or whether a small adapter is needed.
3. After compilation, test on the target phone: confirm camera orientation and auto-exposure still behave as in the baseline, then test RTSP playback in an external client.
4. Do not treat a successful build as proof of RTSP runtime compatibility.

Upstream references:

- [RootEncoder 2.8.1 dependency catalog](https://github.com/pedroSG94/RootEncoder/blob/2.8.1/gradle/libs.versions.toml)
- [RTSP-Server 1.3.6 dependency catalog](https://github.com/pedroSG94/RTSP-Server/blob/1.3.6/gradle/libs.versions.toml)
- [RTSP-Server 1.4.2 dependency catalog](https://github.com/pedroSG94/RTSP-Server/blob/1.4.2/gradle/libs.versions.toml)
- [RTSP-Server 1.4.2 README](https://github.com/pedroSG94/RTSP-Server/blob/1.4.2/README.md)
