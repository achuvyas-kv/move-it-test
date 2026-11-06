# move-it-test

Camera-based body-motion input for games, built with Kotlin Multiplatform (Android target for now).

## Modules
- `motion-core` — pure Kotlin: smoothing, calibration, hand cursors, slash, lean/lanes, jump, duck. Unit-tested on the JVM.
- `motion-android` — CameraX + MediaPipe Pose Landmarker (BlazePose lite, GPU with CPU fallback).
- `motion-debug` — Compose skeleton overlay and live readouts.
- `motion-link` — phone → TV transport: signed binary skeleton frames over UDP, TCP control channel (handshake, ping, clock sync), QR/PIN pairing, mDNS discovery. Unit- and loopback-tested on the JVM.
- `androidApp` — phone app: "Try on this phone" debug mode, and "Send to TV" (phone is the camera).
- `tvApp` — Android TV app: shows a QR code + PIN, receives the skeleton, runs the motion engine, shows live lag stats.

## Build
```
./gradlew :motion-core:jvmTest        # motion logic tests
./gradlew :motion-link:jvmTest        # protocol + localhost phone→TV tests
./gradlew :androidApp:installDebug    # install on a connected phone
./gradlew :tvApp:installDebug         # install on a TV connected with `adb connect <tv-ip>`
```
Requires Android SDK platform 37. Tunable thresholds live in `motion-core/.../MotionConfig.kt`.

## Phone → TV
1. Phone and TV on the same Wi-Fi (not a guest network). Open **Ninja Panda TV** on the TV.
2. On the phone: **Send to TV** → scan the QR code, or pick the TV and enter its PIN.
3. Ports: UDP 47800 (skeleton frames), TCP 47801 (control).
