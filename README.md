# freecorder

`freecorder` is an offline Android camera-signal research prototype. It captures front-camera colour traces in memory for algorithm development, but the public prototype deliberately withholds pulse and breathing-rate numbers because the method has not been clinically validated and can accept non-physiologic periodic artifacts.

It also provides a neutral BMI calculation from user-entered adult height and weight. It does not classify nutrition risk or infer health conditions from appearance.

## Medical and safety warning

`freecorder` is a research prototype that is not clinically validated or cleared for medical use. Do not use it for diagnosis, screening, emergencies, medication changes, treatment decisions, or reassurance.

Camera traces can be confounded by motion, periodic lighting, auto-exposure, skin reflectance, camera processing, frame timing, face-region mapping, and short observation periods. The app therefore does not display physiological measurements in this release.

The app deliberately:

- withholds exact pulse and breathing-rate output;
- requires sustained face detection before analyzing a trace;
- rejects flat signals, insufficient cycles, irregular frame timing, boundary-frequency peaks, and Gaussian-noise controls;
- does not infer anemia, malnutrition, obesity, body composition, disease, or nutrition risk from appearance or entered values;
- provides no clinical health score;
- states that anemia requires appropriate clinical assessment such as hemoglobin testing.

For chest pain, severe breathlessness, fainting, confusion, blue lips, heavy bleeding, or rapidly worsening symptoms, call local emergency services immediately. Do not complete or repeat a scan first.

## What it does

- Runs a 20-second front-camera research capture without saving video.
- Requires a detected face for at least 15 seconds.
- Computes internal experimental frequency-domain traces for development and tests, while withholding physiological numbers from the UI.
- Calculates BMI arithmetically from entered adult height and weight without category labels, screening conclusions, or risk flags.

## Privacy

- Camera frames are processed in memory by the app.
- The app does not save video or images.
- The manifest declares no Internet permission.
- The only runtime permission requested is Camera.
- Android, camera firmware, or other software on the device is outside this app's privacy guarantee.

## Build

This prototype uses a small command-line Android build rather than Gradle.

Requirements:

- JDK 17
- `aapt`, `zipalign`, and `apksigner`
- Android SDK platform 29 for compilation (override with `ANDROID_JAR`); the manifest targets API 35
- Android build-tools 35.0.1 (override with `BUILD_TOOLS` or `D8_JAR`)

Run:

```sh
./build.sh
```

The build runs all deterministic unit tests before packaging. Output:

```text
build/apk/freecorder.apk
```

The generated APK uses a local debug certificate and is intended for development testing only. Do not distribute it as a trusted release build.

## Source layout

- `src/` — Android and signal-processing source
- `tests/` — deterministic and negative-control tests
- `res/` — Android resources
- `AndroidManifest.xml` — permissions and app declaration
- `build.sh` — test and ARM CLI build script

## Current technical status

- App version: 0.3.0
- Package ID: `com.omri.healthscanner`
- Minimum SDK: 26
- Target SDK: 35
- Tested prototype device: Samsung Galaxy S22+, Android 14

The package ID retains the prototype's historical internal name. A production release would need a protected release key, current app-store target-SDK compliance, regulatory review where applicable, and real-device validation against appropriate reference equipment.

## License

MIT License. See `LICENSE`.
