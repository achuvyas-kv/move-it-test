# move-it-test

Camera-based body-motion input for games, built with Kotlin Multiplatform (Android target for now).

## Modules
- `motion-core` — pure Kotlin: smoothing, calibration, hand cursors, slash, lean/lanes, jump, duck. Unit-tested on the JVM.
- `motion-android` — CameraX + MediaPipe Pose Landmarker (BlazePose lite, GPU with CPU fallback).
- `motion-debug` — Compose skeleton overlay and live readouts.
- `androidApp` — debug app showing everything live.

## Build
```
./gradlew :motion-core:jvmTest        # motion logic tests
./gradlew :androidApp:installDebug    # install on a connected phone
```
Requires Android SDK platform 37. Tunable thresholds live in `motion-core/.../MotionConfig.kt`.
