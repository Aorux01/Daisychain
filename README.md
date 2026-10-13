<div align="center">

# Daisychain

**Chain small blocks together to automate your Android phone — the way Apple Shortcuts does on iOS.**

Visual · Block-based · Reads Apple `.shortcut` files · Automations · Plugins

English · Français · Italiano · Deutsch · Español

</div>

---

## What is it?

Daisychain is an Android re-creation of Apple Shortcuts. You build a *shortcut* by stacking **blocks** (actions) in a
list: each block takes what the previous one produced, does one small thing, and hands its result on.
Blocks are deliberately generic, like in Scratch — you name your own variables instead of relying on dozens of
over-specialised actions.

* **Every block shows its input and its output**, so you always see what flows where.
* **Variables you define and name**, usable inside any text or parameter field.
* **Control flow**: If / Otherwise, Repeat, Repeat with each, Choose from menu — nested as deep as you like, with drag & drop.
* **In-app help** with a guide for each topic (Settings → Help and guide).
* A block that no longer exists is shown in **red** with a *Replace* button instead of silently disappearing.

## Features

| | |
|---|---|
| **~120 blocks** | scripting, text, numbers, dates, lists, dictionaries, JSON, web requests, files, photos & camera, audio recording, OCR, dictation, translation, QR codes, location & weather, contacts, calendar, SMS, alarms, timers, notifications, media control, settings… |
| **Apple compatibility** | block ids and parameter keys are Apple's (`is.workflow.actions.*`, `WF*`), so unsigned `.shortcut` files (binary or XML plist) can be **imported**, and shortcuts can be **exported** as `.shortcut` |
| **Automations** | time of day, charger, battery level, Wi‑Fi, Bluetooth device, airplane mode, NFC tag — optionally asking first with a notification |
| **Everywhere** | home-screen pinned shortcuts, long-press app menu, widget, quick-settings tile, Android share menu ("Run a shortcut on this") |
| **Plugins** | add blocks with a single JSON file — no code, see [docs/PLUGINS.md](docs/PLUGINS.md) |
| **System switches** | Do Not Disturb, ringer mode (sound / vibrate / silent) out of the box; Wi‑Fi, Bluetooth, mobile data and airplane mode through the free [Shizuku](https://shizuku.rikka.app) app; hotspot details (name, password) ready for a QR code |
| **Themes & languages** | light / dark / system · English, Français, Italiano, Deutsch, Español (auto-detected, switchable in Settings) |

### Import and export limits

* Shortcuts shared from iOS 15+ are **signed by Apple** (AEA format). Daisychain cannot read them yet and tells you so;
  the in-app Help explains how to obtain an unsigned file.
* Exported files are **unsigned**. Recent iOS only opens signed files: sign them on a Mac with `shortcuts sign`.
* Blocks with no Android equivalent are kept in the shortcut and flagged so nothing is lost.

### Blocks that need Shizuku

Android does not let apps switch Wi‑Fi, Bluetooth, mobile data or airplane mode. Daisychain asks the Shizuku app to run the
same commands as `adb shell`. Until Shizuku is set up (Settings → Advanced control), these blocks are shown with an
**orange outline** in the picker and in shortcuts. The hotspot cannot be switched on by any app; its block opens the screen.

### Translations

The whole app is translated: interface, block names, parameters, error messages and in-app help.

## Build

Requirements: JDK 17+, Android SDK (API 36), a device or emulator running **Android 8.0 (API 26)** or newer.

```bash
./gradlew assembleDebug            # APKs in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest        # unit tests: engine, import/export, triggers, plugins, translations
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
./gradlew assembleRelease          # signed APKs (needs keystore.properties, see below)
```

Each build produces one APK per phone type (`arm64-v8a` for almost every phone made since 2017, `armeabi-v7a` for older
ones) and a `universal` one that fits all.

To sign releases, create a keystore and a `keystore.properties` file next to `build.gradle.kts` (never commit either):

```properties
storeFile=../my-release.jks
storePassword=...
keyAlias=daisychain
keyPassword=...
```

## Project layout

```
app/src/main/java/com/daisychain/
  engine/        platform-independent core (testable on the JVM)
    spec/          ActionDefinition, parameters, registry (blocks auto-discovered)
    actions/       the built-in blocks
    run/           the runner and the DeviceApi/RunUi abstractions
    edit/          editing operations (insert, move, nest, duplicate…)
    apple/         plist reader/writer and .shortcut import/export
    plugin/        JSON plugin parser
  app/           Android part: Compose UI, Room database, widgets, tile, automations, plugin manager
docs/            ACTIONS.md (add a block), PLUGINS.md (write a plugin), examples/ (sample plugins)
```

### Adding a block

Write one class extending `ActionDefinition`, annotate it with `@AutoService(ActionDefinition::class)` — it appears in
the picker automatically. Details in [docs/ACTIONS.md](docs/ACTIONS.md).

## Permissions

Daisychain asks only when a shortcut needs it, just before it runs: location, contacts, calendar, SMS, camera,
microphone, photos, notifications. Automations that listen in the background (charger, Wi‑Fi…) show a quiet
notification while they are active. Everything stays on your phone; the only network traffic is what your own blocks
ask for.

## Roadmap

* Reading Apple-signed shortcuts
* Gallery tab (discover and import shared shortcuts)
* Live QR scanning, folder operations, "Wait to return"

## License

[GNU General Public License v3.0 or later](LICENSE) (GPL-3.0-or-later): you can use, study, share and modify Daisychain; if you distribute
a modified version, it must stay open under the same license.

Daisychain is an independent project. It is not affiliated with, endorsed by or sponsored by Apple Inc.;
"Shortcuts" and "iOS" are Apple trademarks used here only to describe file compatibility.
