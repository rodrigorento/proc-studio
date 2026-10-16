# Instagram Reels Blocker (ProjDIIC)

An Android app that encourages more intentional Instagram use by displaying a full-screen **“Go Study”** prompt after a series of scrolling events in Instagram. You can also save up to three weekly tasks and show them in the prompt as a reminder.

> **Implementation note:** The current UI says the prompt appears after 3 Reels, but the accessibility service actually triggers it after **8 Instagram scroll events**, with a minimum interval of 800 ms between counted events. It does not currently identify distinct Reels. The counter and description in the main screen should be updated if the threshold is changed.

## Features

- **Instagram scroll monitoring:** Uses an Android Accessibility Service configured to receive events from the Instagram app.
- **Study prompt overlay:** Shows a full-screen prompt after the configured scroll-event threshold. The prompt remains visible until dismissed or until you choose to enter your tasks.
- **Weekly task list:** Save up to three tasks. Saved tasks are shown in the overlay and can be edited later.
- **Permission status:** The main screen shows whether the accessibility service and “display over other apps” permission are active, with shortcuts to the relevant Android settings.
- **Local storage:** Tasks are stored on the device using `SharedPreferences`; no backend is configured in this project.

## Requirements

- Android device or emulator
- Android SDK Platform 37 (the module's `compileSdk`)
- JDK 25 for the configured Gradle daemon toolchain
- Gradle wrapper included in the repository
- Instagram installed on the test device to exercise the monitoring feature

The app's `minSdk` is **24** (Android 7.0). The project uses Android Gradle Plugin **9.4.1**, Gradle **9.6.0**, Kotlin **2.2.10**, and Jetpack Compose Material 3.

## Getting started

1. Clone or download this repository and open the project root in Android Studio.
2. Install the Android SDK platform and other SDK components requested by Android Studio/Gradle.
3. Let Gradle sync complete.
4. Connect an Android device or start an emulator, then run the `app` configuration.

You can also build from the project root with the Gradle wrapper:

```bash
# macOS / Linux
./gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.

## First-run permissions

The app needs two user-granted permissions for its main flow:

1. **Accessibility service:** On the main screen, tap **Open Accessibility Settings** and enable the service for this app. Android's settings labels vary by device.
2. **Display over other apps:** On the main screen, tap **Grant Overlay Permission** and allow the app to display over other apps.

Return to the app and confirm both statuses are active. Then open Instagram and scroll. After the threshold is reached, the overlay presents your saved tasks, if any.

Accessibility access is powerful. Enable it only on a device where you understand and consent to the access being granted.

## How it works

1. `InstagramReelsAccessibilityService` receives accessibility events filtered to `com.instagram.android`.
2. On `TYPE_VIEW_SCROLLED`, it increments an in-memory counter if at least 800 ms have passed since the previous counted scroll event.
3. When the counter reaches 8, it resets the counter and displays `overlay_go_study` using Android's `WindowManager` overlay API.
4. The overlay reads saved tasks from `TaskCache`. **Type it** opens the task editor; **Nothing for now** dismisses the overlay.
5. `TaskInputActivity` saves up to three non-blank task entries using `TaskCache` and Android `SharedPreferences`.

The scroll counter is held in memory and resets when the service is recreated. Dismissing the overlay does not disable monitoring.

## Project structure

```text
.
├── app/
│   ├── src/main/
│   │   ├── java/com/example/projdiic/
│   │   │   ├── MainActivity.kt                         # Main status screen (Compose)
│   │   │   ├── InstagramReelsAccessibilityService.kt  # Scroll monitoring and overlay
│   │   │   ├── TaskInputActivity.kt                   # Task editor
│   │   │   ├── TaskCache.kt                           # Local task persistence
│   │   │   ├── PermissionUtils.kt                     # Permission checks
│   │   │   ├── PopupWarningActivity.kt                # Additional warning UI
│   │   │   └── BootReceiver.kt                        # Currently a no-op receiver class
│   │   └── res/
│   │       ├── layout/                                # Task editor and overlay layouts
│   │       └── xml/accessibility_service_config.xml   # Accessibility event configuration
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## Build and test

```bash
# Run local unit tests
./gradlew test

# Run Android instrumented tests (requires a connected device or emulator)
./gradlew connectedAndroidTest

# Build a debug APK
./gradlew assembleDebug
```

The repository currently contains template/example tests; they do not yet test the Reels-counting or overlay behavior.

## Current limitations

- The configured threshold is **8 scroll events**, not 3 distinct Reels. A scroll event is only a proxy for moving through content and may not correspond to a new Reel.
- The prompt is dismissible; this is a study reminder, not a device-level lock or an unskippable Instagram block.
- The counter is in memory and does not persist across service restarts.
- The task list supports at most three entries and is stored locally on the device.
- Some permission declarations and helper classes in the project are not used by the current monitoring flow. For example, `BootReceiver` is a no-op, and `InstagramMonitorService.kt` is empty.
- Automated coverage for the main functionality has not been implemented yet.

## Tech stack

- Kotlin
- Android platform APIs and Accessibility Service
- Jetpack Compose and Material 3 for the main screen
- XML layouts for the task editor and overlay
- Gradle Kotlin DSL and a version catalog
