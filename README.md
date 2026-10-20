# Instagram Reels Blocker (ProjDIIC) · Task Farm

An Android app that pauses Instagram after a stretch of scrolling and shows **only your next pending task**, as a crop growing in a small field. When its crop is due you either **Harvest it** (the task is done) or take **5 more minutes** (once). Every day the crops need watering: until each bed has been tapped once, the farm itself comes up every few swipes. It is the working prototype for Session 3 of the study.

The pause screens follow Gate A of the original Scroll Gate design (a calm sheet over the dimmed feed); the app is now called Task Farm. The gate has no close button; leaving Instagram hides it, and it comes back when Instagram does.

## Features

- **Gate over Instagram:** after **N swipes or T minutes** of continuous scrolling (whichever comes first), the next task is paused on. A **due** crop (ripe, or due by the end of today) gets the firm pause: the feed is dimmed (blurred on Android 12+) and a sheet shows the task, its crop and the time left ("23 hours left"), with Harvest it and 5 more minutes (once). A cooldown follows every firm pause. Without a due crop, the pause is the farm (below): every trigger while a crop is still dry today, otherwise only to ask for tasks. With every crop watered and none due, he scrolls on (`gate_skipped`, `why: all_watered`). `PauseRules` decides, and `gate_shown` logs its `why` (`due`, `check_in`, `water`, `ask`) and how many beds are `dry`.
- **Daily watering:** each farm day (from 4:00, so a night of scrolling counts as one day) starts with every crop dry: pale cracked soil, drooping leaves, and "Needs water" on its card. While any crop is dry, every trigger (3 swipes by default) opens the farm over Instagram instead of letting him scroll, until every bed is watered; **Back to the feed** works, and the farm comes back after the next swipes. A dry bed has a water drop bobbing over it. Tapping a dry bed waters it: the bed squashes and springs back with a tick, the can pours over it and it turns wet (a screen reader hears the task's name and how its crop is doing, "Carrots growing · 4 days left"). Tapping a watered bed opens the crop. A crop's sheet offers **Water it** too while it's dry. Watering the last dry bed sparkles across the field with a success buzz, and over Instagram **Back to the feed** pulses once. Watering in the app counts the same. A new crop starts dry: it needs watering the day it's planted too. Watering only changes the look; crops grow with time and never die. Logged as `watered` (`where`: app or overlay, `via`: bed, sheet or screen_reader, `stage`, `dry_left`). 5 more minutes hold the watering too, and **Close the gate until tomorrow** lasts until the next farm day starts (4:00).
- **Nothing for now:** with an empty list, or with every task waiting for its time later today ("tonight"), opening Instagram shows the farm straight away instead of waiting for swipes (with a crop dry, the watering above already brings it every few swipes): the same field, crop cards and **Plant a task** as the app's home screen (`OverlayFarm`, the shared `Farm` in Compose), under "What needs planting?" (or the day's watering line), sliding up over the feed when it opens, with **Back to the feed** (or **Not now**) and **Close the gate until tomorrow**. Tapping a crop opens its sheet there too (Harvest it on a due crop, logged `task_done` with `screen: add_tasks`; Remove it otherwise). It asks once per Instagram session (a farm opened for watering counts as that ask when there's nothing for now): after "Not now" it doesn't ask again until Instagram has gone more than 10 minutes unseen, away in another app or with the phone locked (logged as `gate_skipped` with `why: asked_this_session`). A task on its 5 more minutes still counts as something to do, so it doesn't ask then. A full list of tasks all waiting for later today, all watered, lets him scroll. `add_tasks_closed` logs the `why` the farm opened with.
- **Default trigger:** 3 swipes (change it in the facilitator panel).
- **Close the gate until tomorrow:** a link on the farm over Instagram turns every gate and question off until the next farm day (4:00). The app's main screen then shows "Gate closed until tomorrow" with **Open it**. Both are logged (`app_snoozed`, `app_turned_back_on`).
- **Harvest it** (green, with the basket): on a due crop. The task is done: a crop due today that isn't ripe yet ripens first, then it flies out of the bed and the feed opens. Logged as a `harvest` choice (with `ripe`) and `task_done`. The check-in screen offers it too.
- **5 more minutes:** once per task, on a due crop. The next gate for that task doesn't offer it.
- **Rotating wording:** the line above the task changes every gate so it doesn't go stale.
- **Trusted-person check-in (Wizard of Oz):** off by default. When the participant switches it on, the next gate for a task that was passed twice, once its crop is due, asks whether their trusted person may check in. The app logs `check_in` and a teammate sends the real message by hand.
- **The field:** up to four tasks, one per plot. Each is planted in three steps, on the main screen or on the farm over Instagram (the same Compose screens; there the voice uses the phone's speech recognizer): name it (by voice or typing), pick its seeds (when it's for), and watch them go in. The crop grows from planting to its due time: seed, then sprout a quarter of the way, growing from halfway, and ripe only on its due day; gone to seed after it. The main screen shows the field as one lawn: each task's bed, and empty plots marked out with pegs and string. Tapping a bed, or its card under the field (a card for every crop in the order they come up, saying when to harvest it; a due crop's line is a green pill with the basket, "Harvest now" or "Harvest by tonight"), opens the crop: its bed, how it's growing and, for a due crop, **Harvest it**, which plays the harvest on the field and logs `task_done` with `screen: app`. A crop not due yet offers **Remove it** (red outline) instead, which takes it off the field and logs `task_removed` with its `stage`. A due crop's bed glints now and then, and the light sweeps across its green pill; a card not due yet has a thin green bar for how far its crop has come. The crops sway in a light breeze, and now and then a butterfly crosses the lawn. A crop planted (or brought back by Undo) drops onto its plot with a puff of soil; a harvested one flies into the basket, which wobbles and says "+1". A card can also be swiped away either way, as in Gmail: the card tilts as it's dragged, ticks when it passes the point where letting go counts, slides off over green "Harvest" (a due crop) or red "Remove" (one not due), its gap closes, and it's done as from the sheet (TalkBack has the same as an action on the card). After a harvest or a removal, "Harvested · Pay the rent" (or "Removed · …") with **Undo** shows for 5 seconds (longer when accessibility settings ask for it); planting something new ends it; Undo puts the crop back in its plot as it was (logged `task_restored` with `after`). Tapping an empty plot plants. **Plant a task** stays at the foot of the screen. With Android's "Remove animations" on, the drops, glints, sparkles, splashes, sway, butterfly, squash, tilt, pulse and slide all stay still and the basket just shows; the ticks and buzz remain.
- **Growth news and the week's harvests:** a crop that has grown a stage since the farm last showed it is told in the watering line for a moment ("Your carrots sprouted!", "Your radishes are ripe!", crops with the same news together) and its bed pops; logged once as `growth_news` (`where`, `task`, `crop`, `stage`). Under the title, "3 harvests this week" (from Monday's farm day, 4:00) counts every harvest, in the app, on the farm over Instagram or on the pause; Undo takes one back. Both are kept on the phone (`growth_seen`, `farm_tally`) and cleared by Reset all data.
- **Sounds:** a soft pop as a crop is harvested (also on the pause), water pouring as a bed is watered, seeds rattling as they're planted. They play only with the phone's ringer on (silent and vibrate mute them) and can be switched off in the facilitator panel (`setting_changed`, `sounds`). They're made from code by `art/sounds.py`.
- **When a task is for:** six seeds, back to back, each saying the days it covers: **Today** (radish), the rest of this week from tomorrow (lettuce, e.g. "Tomorrow – 25 Oct"; none on a Sunday), then a week each, Monday to Sunday: next week (carrot), the week after (corn) and the one after that (pumpkin), and anything later (grapes, "From 16 Nov", up to a year). Tapping one hides the others, moves it up and opens its days, the rest closed: a week seed shows its one week, the grapes a month a page; it opens on the last day of the seed's first week, any time that day, or a time on Android's wheels. The crop always matches the day picked (`FarmRules.crop`, by the week it falls in). Tasks saved with the old Tomorrow, This week, This month, Tonight or Anytime keep their crops. Times said or typed with the task are read on the phone, with no network call: "dentist tomorrow at 3pm", "gym on Monday", "return the book by Wednesday", "next week", "in 2 hours", "on the 25th"; a part of the day keeps its end ("tomorrow morning" is by 12:00). The words are taken out of the title, and the seeds open on the seed that covers that day, with the day and time picked and "You said" on it. Every crop grows from planting, except a time said for later today ("tonight"): that task only reaches the gate once its time has started, and its crop isn't ripe before then. A step left open past midnight keeps the date it showed. A due crop comes first, then the one due soonest. English only for now (`WhenParser`).
- **Facilitator panel:** hidden on the main screen; **long-press the "Task Farm" title** to show it. Set N, T and the cooldown, switch check-ins and the farm sounds on or off, trigger a gate on the next swipe, export the log, reset the data.
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
3. **Microphone (optional):** lets the farm over Instagram take tasks by voice. Typing always works.

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
2. When the swipe count or the minutes of continuous scrolling reach the threshold, it picks the next task (`TaskRules.next`: a due crop first, `FarmRules.isDue`, then the one due soonest, then one back from its 5 more minutes, then the oldest). `PauseRules` picks the screen: the firm pause (or check-in) for a due crop, otherwise the farm while a crop is dry today, or to ask for tasks once a session (`InstagramSession`); `GateOverlay` draws it.
3. Each choice updates the task in `TaskCache` (snooze, passes) and is written to `EventLog`. Harvest it removes the task.
4. A full-screen window from another app means Instagram left the screen: the overlay is hidden and shown again when Instagram returns. The keyboard (any keyboard app, recognised by its window), the system UI and windows that don't fill the screen, such as pop-ups, overlays and the navigation bar that Samsung's launcher draws, don't count (`WindowRules`). Each `left_instagram` event records the package and window that replaced Instagram, and each `window_ignored` event a window that opened over a screen without hiding it. Walking away from a gate counts as a pass on its task (watering isn't one), and a gate left for more than 10 minutes is dropped. A gate only opens while Instagram is the active app, so late scroll events never draw it over another app.
5. After two passes on the same task, and only if check-ins are on, the next gate for it once its crop is due is the check-in screen.

## Project structure

```text
app/src/main/
├── java/com/example/projdiic/
│   ├── MainActivity.kt                         # Setup, the farm, hidden facilitator panel (Compose)
│   ├── Farm.kt / OverlayFarm.kt               # The farm screen, shared by the app and the pause over Instagram
│   ├── InstagramReelsAccessibilityService.kt  # When the gate opens and what each choice does
│   ├── InstagramSession.kt                    # One Instagram session: the ask for tasks comes once
│   ├── PauseRules.kt                          # Which pause a trigger opens: due crop, watering, the ask, or none
│   ├── GateOverlay.kt                         # Draws the gate screens over Instagram
│   ├── GateScreen.kt                          # Which screen is showing
│   ├── GateTrigger.kt                         # N swipes or T minutes, debounce, cooldown
│   ├── Task.kt                                # Task model and rules (next task, snooze, passes)
│   ├── TaskWhen.kt / WhenParser.kt            # Time windows, their labels, reading times from speech or text
│   ├── SeedPackets.kt / SeedCalendar.kt       # The six seeds, the day and time picked, and the seed calendar
│   ├── TaskCache.kt                           # Task storage (JSON in SharedPreferences)
│   ├── GateSettings.kt                        # Facilitator settings
│   ├── EventLog.kt / LogFormat.kt             # On-device log and JSON/CSV export
│   ├── PermissionUtils.kt                     # Permission checks
│   ├── PopupWarningActivity.kt                # Additional warning UI (unused)
│   ├── BootReceiver.kt                        # Currently a no-op receiver class
│   ├── FarmHome.kt / PlantFlow.kt             # The field on the main screen, and planting a task
│   ├── FarmJuice.kt                           # The field's small touches: drops, glints, sparkles, sway, butterfly, basket
│   ├── GrowthNews.kt / FarmTally.kt           # "Your carrots sprouted!", and the week's harvests
│   ├── FarmSounds.kt                          # The pop, pour and seed sounds (res/raw, made by art/sounds.py)
│   ├── FarmRules.kt                           # Crops, growth and the day's watering
│   ├── CropArt.kt / CropMotion.kt / FieldArt.kt # Farm images, animation frames and the field's layout (generated from art/)
│   └── FarmArtPreview.kt                      # Facilitator preview of the farm art
└── res/
    ├── drawable-nodpi/                        # Rendered farm cards, animation frames and props (generated)
    ├── layout/overlay_*.xml                   # The firm pause and the check-in (the farm over Instagram is Compose)
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
# Unit tests: gate trigger, Instagram sessions, task rules, farm rules, seed packets, log export
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
