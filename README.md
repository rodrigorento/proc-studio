# Instagram Reels Blocker (ProjDIIC) · Scroll Gate

An Android app that pauses Instagram after a stretch of scrolling and shows **only your next pending task**. To keep scrolling you pick one of three ways out: **Do it now**, **Pick a time** or **Snooze once**. It is the working prototype for Session 3 of the study.

The screens follow the Scroll Gate design (Gate A: a calm sheet over the dimmed feed). The gate has no close button; leaving Instagram hides it, and it comes back when Instagram does.

## Features

- **Gate over Instagram:** after **N swipes or T minutes** of continuous scrolling (whichever comes first), the feed is dimmed (blurred on Android 12+) and a sheet shows one task with three choices. A cooldown follows every gate.
- **Do it now:** Instagram stays closed (the screen says "Feed closed") until you tap **Done**, which removes the task, or **Back to choices**. Other apps stay free to use, so you can go and do the task.
- **Pick a time:** quick choices (in 15 minutes, in 1 hour, tonight, tomorrow morning). The task comes back at the first gate after that time. **Add to Calendar instead** keeps the calendar flow: it opens a new Calendar event 15 minutes from now.
- **Snooze once:** 5 minutes, once per task. The next gate for that task has no Snooze button.
- **Rotating wording:** the line above the task changes every gate so it doesn't go stale.
- **Trusted-person check-in (Wizard of Oz):** off by default. When the participant switches it on, the third gate for a task that was passed twice asks whether their trusted person may check in. The app logs `check_in` and a teammate sends the real message by hand.
- **Task list:** up to three tasks, added on the main screen or on the pause screen when the list is empty, by voice or by typing.
- **When a task is for:** each task has a time pill. Tap it for fixed choices: Anytime, Today, Tonight, Tomorrow, This week. Times said or typed with the task are read on the phone, with no network call: "dentist tomorrow at 3pm", "gym on Monday", "return the book by Wednesday", "next week", "in 2 hours", "on the 25th". The words are taken out of the title, and the chip they matched is pre-selected. A task only reaches the gate once its time has started; the one due soonest comes first. English only for now (`WhenParser`).
- **Facilitator panel:** hidden on the main screen; **long-press the "Scroll Gate" title** to show it. Set N, T and the cooldown, switch check-ins on, trigger a gate on the next swipe, export the log, reset the data.
- **Interaction log:** every event (swipe, gate shown, choice, time on the gate, task done, leaving Instagram) is kept on the phone with a timestamp and exported as JSON or CSV through the share sheet.
- **Local only:** tasks, settings and the log stay on the device; no backend.

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
# macOS / Linux (the wrapper isn't marked executable in git, so call it through bash)
bash gradlew assembleDebug

# Windows
gradlew.bat assembleDebug
```

The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.

## First-run setup

The main screen lists what the app needs and has a button for each:

1. **Accessibility service:** tap **Turn on** and enable the service for this app. Android's settings labels vary by device.
2. **Display over other apps:** tap **Allow** and allow the app to display over other apps.
3. **Microphone (optional):** lets the pause screen take tasks by voice. Typing always works.

Then add up to three tasks, open Instagram and scroll.

Accessibility access is powerful. Enable it only on a device where you understand and consent to the access being granted.

## Running Session 3

1. Long-press the **Scroll Gate** title to open the facilitator panel. Lower **N** (for example to 5) so the gate fires at least twice in ten minutes.
2. Add the participant's three real tasks with them (the mic button on the task field uses the phone's voice input).
3. Switch **Trusted-person check-in** on only if they agree, and enter the person's first name.
4. Use **Pause at the next swipe** to show the gate on demand.
5. When the screen shows a check-in being accepted (the log records a `check_in` event with `choice=accepted`), the teammate sends the real message.
6. At the end, **Export CSV** (or JSON) and share it to the team's drive.

## How it works

1. `InstagramReelsAccessibilityService` receives scroll events and window changes. It counts only `TYPE_VIEW_SCROLLED` from `com.instagram.android`, at most one every 800 ms (`GateTrigger`).
2. When the swipe count or the minutes of continuous scrolling reach the threshold, it picks the next task (`TaskRules.next`: a task whose picked time has come first, then the oldest) and asks `GateOverlay` to draw the gate.
3. Each choice updates the task in `TaskCache` (snooze, picked time, passes) and is written to `EventLog`. Done removes the task.
4. Window changes from other apps (not the keyboard or the system UI) mean Instagram left the screen: the overlay is hidden and shown again when Instagram returns. Walking away from a gate counts as a pass on its task, and a gate left for more than 10 minutes is dropped (the "Feed closed" screen of Do it now is kept). A gate only opens while Instagram is the active app, so late scroll events never draw it over another app.
5. After two passes on the same task, and only if check-ins are on, the next gate for it is the check-in screen.

## Project structure

```text
app/src/main/
├── java/com/example/projdiic/
│   ├── MainActivity.kt                         # Setup, task list, hidden facilitator panel (Compose)
│   ├── InstagramReelsAccessibilityService.kt  # When the gate opens and what each choice does
│   ├── GateOverlay.kt                         # Draws the gate screens over Instagram
│   ├── GateScreen.kt                          # Which screen is showing
│   ├── GateTrigger.kt                         # N swipes or T minutes, debounce, cooldown
│   ├── Task.kt                                # Task model and rules (next task, snooze, passes)
│   ├── TimeChoices.kt                         # Pick a time quick choices
│   ├── TaskWhen.kt / WhenParser.kt            # Time chips, their labels, reading times from speech or text
│   ├── TaskCache.kt                           # Task storage (JSON in SharedPreferences)
│   ├── GateSettings.kt                        # Facilitator settings
│   ├── EventLog.kt / LogFormat.kt             # On-device log and JSON/CSV export
│   ├── PermissionUtils.kt                     # Permission checks
│   ├── PopupWarningActivity.kt                # Additional warning UI (unused)
│   └── BootReceiver.kt                        # Currently a no-op receiver class
└── res/
    ├── layout/overlay_*.xml                   # Gate, Pick a time, Do it now, Check-in, Add tasks
    └── xml/accessibility_service_config.xml   # Accessibility event configuration
```

## Build and test

```bash
# Unit tests: gate trigger, task rules, time choices, log export
bash gradlew testDebugUnitTest

# Android instrumented tests (requires a connected device or emulator)
bash gradlew connectedAndroidTest

# Build a debug APK
bash gradlew assembleDebug
```

## Current limitations

- A scroll event is a proxy for moving to the next Reel; it does not identify distinct Reels.
- The minutes threshold is checked on each counted swipe, so watching one long video without scrolling doesn't trigger it.
- Voice capture on the pause screen uses Android's speech recognizer from the background service. Some phones may refuse microphone access there; the pause screen then says it didn't catch that, and typing still works. The main screen's mic uses the phone's own voice input and always works where voice input is installed.
- Pausing Instagram's sound uses audio focus and is best effort: Android 15 and later may refuse it to a background service. Each `gate_shown` event records `sound_paused` so the pilot shows whether it works on the test phone.
- Any app window that opens over Instagram (the Assistant, chat heads) counts as leaving it, so it can record a pass the participant didn't choose.
- The trusted-person message is not sent by the app (Wizard of Oz).
- Some permission declarations and helper classes are not used by the current flow, for example `BootReceiver`, `PopupWarningActivity` and the empty `InstagramMonitorService.kt`.

## Tech stack

- Kotlin
- Android platform APIs and Accessibility Service
- Jetpack Compose and Material 3 for the main screen
- XML layouts for the overlay screens
- Gradle Kotlin DSL and a version catalog
