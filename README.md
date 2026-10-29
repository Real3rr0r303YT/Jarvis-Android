<div align="center">

# 🤖 JARVIS-AI

### Android System Agent — full device control, on your own phone.

`JARVIS@root:~# EXECUTE ACTION...`

**by [Real3rr0r303YT](https://github.com/real3rr0r303yt)**

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-00FF66?style=flat-square&labelColor=000000)
![Language](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-00FF66?style=flat-square&labelColor=000000)
![License](https://img.shields.io/badge/license-GPL--3.0-00FF66?style=flat-square&labelColor=000000)

</div>

---

## What is Jarvis-AI?

Jarvis-AI is **not** just a chatbot or a voice terminal. It is a real **Android
system agent**: you type (or speak) a natural-language command, and Jarvis
translates it into concrete device actions — toggling radios, launching apps,
tapping on-screen elements, and replying to notifications — all running
**locally on your own device**.

Think of it as a scriptable, AI-driven cousin of Tasker / MacroDroid, wrapped in
a pitch-black neon-green hacker HUD.

> ⚠️ **It controls your own phone, with permissions you explicitly grant.**
> Jarvis uses the standard Android `AccessibilityService` and
> `NotificationListenerService` APIs. Nothing happens until *you* turn those
> services on in system Settings, and nothing leaves the device — the intent
> parser runs entirely offline.

---

## ✨ Features

### 1. System-Access & Automation Core
| Capability | How it works |
|---|---|
| **AccessibilityService** | Reads the on-screen node tree to find the element your command refers to, then performs taps / gestures / text input. |
| **NotificationListenerService** | Mirrors posted notifications and can send inline replies using the notification's own `RemoteInput` action. |
| **SystemController** | Wi-Fi, Bluetooth, media/ring/alarm volume, screen brightness, battery-saver, and app launching via public Android APIs. |
| **Root / Shizuku (optional)** | When available, performs silent radio toggles (`svc wifi …`) with ADB-level privileges. Fully optional — the app degrades gracefully to Settings-panel hand-offs without it. |

### 2. Jarvis AI Brain & Command Processor
- Natural-language **intent parser** (German **and** English) that splits
  compound commands (`"Schalte WLAN aus und aktiviere Energiesparmodus"`) into a
  sequence of concrete, auditable actions.
- Transparent, rule-based engine — no hidden behaviour, easy to extend. A cloud
  LLM can be dropped in behind the same `IntentParser` interface later.

Example commands:
```
Öffne WhatsApp und schreib "Bin in 10 Minuten da"
Schalte WLAN aus und aktiviere Energiesparmodus
Set volume to 30% and brightness to 20%
Open Spotify
```

### 3. Cyber / Hacker HUD (Jetpack Compose)
- **Pitch black `#000000`** background, **neon green `#00FF66`** foreground.
- Live dashboard: battery, RAM meter, CPU cores, and ONLINE/OFF status pills for
  every service.
- **Dynamic terminal log** streaming every executed action in
  `[HH:mm:ss] JARVIS@root:~# EXECUTE ...` style.

---

## 🗂️ Project Structure

```
app/src/main/java/com/real3rr0r303yt/jarvis/
├── MainActivity.kt                    # Compose host + telemetry loop
├── JarvisApplication.kt
├── service/
│   ├── JarvisAccessibilityService.kt  # on-screen automation core
│   └── JarvisNotificationListenerService.kt
├── system/
│   ├── SystemController.kt            # Wi-Fi / BT / volume / display / apps
│   └── ElevatedShell.kt              # optional Shizuku / root channel
├── brain/
│   ├── IntentParser.kt               # natural language -> actions
│   ├── CommandProcessor.kt           # dispatches actions
│   └── model/JarvisAction.kt
├── core/
│   ├── JarvisLog.kt                  # terminal action log (StateFlow)
│   └── SystemStats.kt                # dashboard telemetry
└── ui/
    ├── theme/Theme.kt                # black + neon theme
    ├── components/HudComponents.kt
    └── dashboard/DashboardScreen.kt
```

---

## 🚀 Installation / Build

**Requirements:** Android Studio (Koala or newer), JDK 17, Android SDK 35, a
device/emulator on **Android 8.0 (API 26)+**.

```bash
git clone https://github.com/real3rr0r303yt/Jarvis-Android.git
cd Jarvis-Android
./gradlew assembleDebug        # build the APK
./gradlew installDebug         # install on a connected device
```

> The Gradle build downloads the Android Gradle Plugin from Google's Maven, so
> the first build needs network access to `dl.google.com`.

---

## 🔐 Permissions Guide

Jarvis requests only what each feature needs, and the two most powerful services
must be enabled **by you** in system Settings — the app cannot grant them to
itself:

1. **Accessibility** — `Settings → Accessibility → Jarvis-AI Automation → On`.
   Required for reading the screen and performing taps/gestures/typing. Tap
   **ENABLE A11Y** in the app to jump straight there.
2. **Notification access** — `Settings → Notifications → Notification access →
   Jarvis-AI`. Required to read notifications and send inline replies. Tap
   **ENABLE NOTIF** in the app.
3. **Runtime permissions** (prompted on first launch): `POST_NOTIFICATIONS`,
   `BLUETOOTH_CONNECT`.
4. **Write settings** (prompted when first changing brightness):
   `Settings → Apps → Jarvis-AI → Modify system settings`.
5. **(Optional) Shizuku** — install the [Shizuku](https://shizuku.rikka.app/)
   app and start its service for silent Wi-Fi/Bluetooth/battery-saver toggles
   without root.

You can revoke any of these at any time; Jarvis simply falls back to asking you
to confirm via the relevant system screen.

---

## 🛡️ Privacy & Scope

- Runs **entirely on-device**. The intent parser is local; no screen content or
  notification text is sent anywhere.
- Open source under GPL-3.0 — audit every line.
- Intended for controlling **your own device**. Please use it responsibly and in
  line with the laws and app terms that apply to you.

---

## ☕ Support / Donate

If Jarvis-AI is useful to you, you can support development here:

**[☕ Buy Me a Coffee → https://www.buymeacoffee.com/real3rr0r303yt](https://www.buymeacoffee.com/real3rr0r303yt)**

---

## 📜 License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
See [LICENSE](LICENSE) for the full text.

```
Jarvis-AI — Android System Agent
Copyright (C) 2026  Real3rr0r303YT

This program is free software: you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. This program is distributed WITHOUT ANY WARRANTY.
```
