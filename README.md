# Instagram Reels Blocker (ProjDIIC) · Task Farm

An Android app that pauses Instagram after a stretch of scrolling and shows **only your next pending task**, as a crop growing in a small field. To keep scrolling you either **Harvest it** (the task is done) or take **5 more minutes** (once). It is the working prototype for Session 3 of the study.

The pause screens follow Gate A of the original Scroll Gate design (a calm sheet over the dimmed feed); the app is now called Task Farm. The gate has no close button; leaving Instagram hides it, and it comes back when Instagram does.

## Features

- **Gate over Instagram:** after **N swipes or T minutes** of continuous scrolling (whichever comes first), the feed is dimmed (blurred on Android 12+) and a sheet shows one task and its crop: Harvest it, and 5 more minutes (once). A cooldown follows every gate.
- **Nothing due:** with an empty list, or with every task planned for later ("tomorrow", "on Monday"), opening Instagram asks for tasks straight away instead of waiting for swipes; the screen then says "Nothing's ripe right now". After "Not now" it asks again every 3 swipes (the swipe setting), with no cooldown. A full list of tasks all planned for later lets him scroll.
- **Default trigger:** 3 swipes (change it in the facilitator panel).
- **Close the gate until tomorrow:** a button on the ask-for-tasks screen turns every gate and question off until midnight. The app's main screen then shows "Gate closed until tomorrow" with **Open it**. Both are logged (`app_snoozed`, `app_turned_back_on`).
- **Harvest it** (green, with the basket): the task is done. A crop that isn't ripe yet ripens first, then it flies out of the bed and the feed opens. Logged as a `harvest` choice (with `ripe`) and `task_done`. The check-in screen offers it too. Crops grow with time only; there is no watering.
- **5 more minutes:** once per task. The next gate for that task doesn't offer it.
- **Rotating wording:** the line above the task changes every gate so it doesn't go stale.
- **Trusted-person check-in (Wizard of Oz):** off by default. When the participant switches it on, the third gate for a task that was passed twice asks whether their trusted person may check in. The app logs `check_in` and a teammate sends the real message by hand.
- **The field:** up to four tasks, one per plot. Each is planted in three steps, on the main screen or on the pause screen when nothing is due: name it (by voice or typing), pick its seed packet (when it's for), and watch it go in. The crop grows from planting to its due time. The main screen shows the field as one lawn: each task's bed, and empty plots marked out with pegs and string. Tapping a bed, or its card under the field (a card for every crop, the next one first, marked **Next up**, saying when to harvest it), opens the crop: its bed, how it's growing and **Harvest it**, which plays the harvest on the field and logs `task_done` with `screen: app`. Tapping an empty plot plants. **Plant a task** stays at the foot of the screen.
- **When a task is for:** each task has a time, picked as its seed packet when it is planted: Anytime, Today, Tonight, Tomorrow, This week, This month. On the pause that asks for tasks, tapping a crop's time changes it (and replants it). Times said or typed with the task are read on the phone, with no network call: "dentist tomorrow at 3pm", "gym on Monday", "return the book by Wednesday", "next week", "in 2 hours", "on the 25th". The words are taken out of the title, and the chip they matched is pre-selected. A task only reaches the gate once its time has started; the one due soonest comes first. English only for now (`WhenParser`).
- **Facilitator panel:** hidden on the main screen; **long-press the "Task Farm" title** to show it. Set N, T and the cooldown, switch check-ins on, trigger a gate on the next swipe, export the log, reset the data.
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

Then plant up to four tasks, open Instagram and scroll.

Accessibility access is powerful. Enable it only on a device where you understand and consent to the access being granted.

## Running Session 3

1. Long-press the **Task Farm** title to open the facilitator panel. Lower **N** (for example to 5) so the gate fires at least twice in ten minutes.
2. Plant the participant's real tasks with them (up to four; **Plant a task** takes them by typing or by voice).
3. Switch **Trusted-person check-in** on only if they agree, and enter the person's first name.
4. Use **Pause at the next swipe** to show the gate on demand.
5. When the screen shows a check-in being accepted (the log records a `check_in` event with `choice=accepted`), the teammate sends the real message.
6. At the end, **Export CSV** (or JSON) and share it to the team's drive.

## How it works

1. `InstagramReelsAccessibilityService` receives scroll events and window changes. It counts only `TYPE_VIEW_SCROLLED` from `com.instagram.android`, at most one every 800 ms (`GateTrigger`).
2. When the swipe count or the minutes of continuous scrolling reach the threshold, it picks the next task (`TaskRules.next`: the one due soonest, where one back from its 5 more minutes counts as due when they end, then the oldest) and asks `GateOverlay` to draw the gate.
3. Each choice updates the task in `TaskCache` (snooze, passes) and is written to `EventLog`. Harvest it removes the task.
4. A full-screen window from another app means Instagram left the screen: the overlay is hidden and shown again when Instagram returns. The keyboard (any keyboard app, recognised by its window), the system UI and windows that don't fill the screen, such as pop-ups, overlays and the navigation bar that Samsung's launcher draws, don't count (`WindowRules`). Each `left_instagram` event records the package and window that replaced Instagram, and each `window_ignored` event a window that opened over a screen without hiding it. Walking away from a gate counts as a pass on its task, and a gate left for more than 10 minutes is dropped. A gate only opens while Instagram is the active app, so late scroll events never draw it over another app.
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
│   ├── TaskWhen.kt / WhenParser.kt            # Time chips, their labels, reading times from speech or text
│   ├── TaskCache.kt                           # Task storage (JSON in SharedPreferences)
│   ├── GateSettings.kt                        # Facilitator settings
│   ├── EventLog.kt / LogFormat.kt             # On-device log and JSON/CSV export
│   ├── PermissionUtils.kt                     # Permission checks
│   ├── PopupWarningActivity.kt                # Additional warning UI (unused)
│   ├── BootReceiver.kt                        # Currently a no-op receiver class
│   ├── FarmHome.kt / PlantFlow.kt             # The field on the main screen, and planting a task
│   ├── FarmRules.kt                           # Crops and growth
│   ├── CropArt.kt / CropMotion.kt / FieldArt.kt # Farm images, animation frames and the field's layout (generated from art/)
│   └── FarmArtPreview.kt                      # Facilitator preview of the farm art
└── res/
    ├── drawable-nodpi/                        # Rendered farm cards, animation frames and props (generated)
    ├── layout/overlay_*.xml                   # Gate, Check-in, Add tasks and its planting steps
    └── xml/accessibility_service_config.xml   # Accessibility event configuration
```

## Farm art

The farm theme's beds, crops, animations, props and app icon are pre-rendered in Blender from code in `art/`:
`art/farmkit` builds them, `art/export_android.py` writes the images and the Kotlin that looks them up, and
`art/review.py` makes review sheets and checks `art/assets.yaml`, the list of every asset and its status. The
`farm-3d` skill in `.claude/skills` walks through the whole pipeline. To see the art in the app, long-press the
title and scroll to the farm art preview.

## Build and test

```bash
# Unit tests: gate trigger, task rules, farm rules, seed packets, log export
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
- An app screen that opens over Instagram (the Assistant, a chat head that opens a conversation) counts as leaving it, so it can record a pass the participant didn't choose. The other way round, an app screen that doesn't fill the screen, such as a dialog-style screen, stays under the gate. The log's `left_instagram` and `window_ignored` rows show which windows did what.
- The trusted-person message is not sent by the app (Wizard of Oz).
- Some permission declarations and helper classes are not used by the current flow, for example `BootReceiver`, `PopupWarningActivity` and the empty `InstagramMonitorService.kt`.

## Tech stack

- Kotlin
- Android platform APIs and Accessibility Service
- Jetpack Compose and Material 3 for the main screen
- XML layouts for the overlay screens
- Gradle Kotlin DSL and a version catalog
