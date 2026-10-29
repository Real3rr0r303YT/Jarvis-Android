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

### 4. Autonomous Agent (ReAct + Tool Calling)
Jarvis can plan and execute multi-step tasks on its own. When the agent is
enabled (Settings) and an LLM API key is set, a command is routed through a
**ReAct loop** (Thought → Action → Observation) driven by `JarvisAgentEngine`,
which calls specialised tools implementing the `JarvisTool` interface:

| Tool | Does |
|---|---|
| `WebSearchTool` | Keyless web lookup (DuckDuckGo Instant Answer) returning a text summary. |
| `AppAutomationTool` | Opens apps, types, taps and navigates via `JarvisAccessibilityService`. |
| `SystemControlTool` | Wi-Fi/Bluetooth/volume/brightness/battery-saver, plus `shell:` raw commands via the elevated channel. |
| `LlmBrainTool` | Free-text reasoning: summaries, translations, Q&A. |

Every step is streamed live to the HUD terminal:
```
JARVIS@root:~# INFO   AGENT THINKING: need current weather, search the web
JARVIS@root:~# EXECUTE AGENT ACTION: web_search("Berlin weather today")
JARVIS@root:~# INFO   AGENT RESULT: 12°C, light rain. Routing to app_automation...
```

Supported providers: **OpenAI**, **Groq** (OpenAI-compatible) and **Gemini**.
The API key is stored **only on the device** and sent only to the provider you
pick. Without a key, Jarvis falls back to the offline rule-based pipeline.

### 5. Proactive Event Triggers
Jarvis can react to events, not just typed commands:

- **App-launch trigger** (`AppTriggerManager`) — the `JarvisAccessibilityService`
  watches `TYPE_WINDOW_STATE_CHANGED`; when a watched package comes to the
  foreground (e.g. TikTok `com.zhiliaoapp.musically`), Jarvis fires the content
  agent to generate **3 fresh short-video hook ideas** and shows them as a
  notification. Per-package debounced so it won't re-fire on every window change.
- **Audio-activity trigger** (`AudioTriggerManager`) — a lightweight,
  amplitude-based mic listener running inside the foreground service; on
  sustained sound it raises an event (the hook point for a future wake-word /
  keyphrase model). Off by default.
- Configure both in **Settings → Proactive Triggers** (presets for
  TikTok/Instagram/YouTube, plus custom package names, and the audio toggle).

> Background mic use runs under a `microphone` foreground-service type with the
> persistent monitoring notification visible, and is opt-in.

### 6. Background, Voice & Customisation
| Feature | Details |
|---|---|
| **Foreground Service** | `JarvisForegroundService` runs with an ongoing *"JARVIS Active — System Monitoring"* notification so Android won't kill the agent in the background. Toggle it from Settings; tap the notification to reopen the HUD. |
| **Voice Input** | `VoiceInput` wraps Android's on-device `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`, so spoken commands are transcribed locally and fed straight into the command processor. 🎙 button on the dashboard. |
| **Settings / Customisation** | Compose settings screen: live permission status, an **app whitelist** (optionally enforced so Jarvis only touches apps you approve), and saved **custom shell commands** run through the elevated channel you enabled. |

### 8. Multi-Agent System & HQ Voice (v1.2.0)
- **Planner + sub-agents** — `JarvisAgentEngine` is the Planner; it decomposes a request and delegates to specialised sub-agents exposed as tools: **DocAgent** (`DocTool`, local PDF generation via Android `PdfDocument`), **AutomationAgent** (`AppAutomationTool` + `SystemControlTool`), plus web search, diagnostics and a free-text brain.
- **WhatsApp node targeting** — compose now triggers the in-app **Search** node, types the contact, taps the result row, then writes into the message field — removing the `FAIL COMPOSE` race.
- **High-quality voice** — `TtsManager` optionally synthesises via **ElevenLabs** (API key + voice id in Settings) for a natural voice, with the tuned local engine (pitch/rate) as fallback.

### 7. Voice Output, Overlay & Diagnostics (v1.1.0)
- **Text-to-Speech** (`TtsManager`) — agent/LLM answers are shown in the action log **and read aloud** (toggle in Settings).
- **Continuous voice listening** — `AudioTriggerManager` runs an uninterrupted background listening loop in the foreground service, re-opening the mic on transient failures instead of stopping after one burst.
- **Smart Overlay HUD** (`OverlayHudService`) — a minimalist, draggable neon cyber chip over all apps; tap it for a quick voice command. Needs the draw-over-apps permission.
- **Auto-Content-Drafting** — when a watched app (e.g. TikTok) opens, the generated draft is **copied to the clipboard** and shown as a notification.
- **System Diagnostics Tool** (`SystemCheckTool`) — a ReAct agent tool reporting battery, RAM, CPU, network transport and service status, right in the terminal.

---

## 🗂️ Project Structure

```
app/src/main/java/com/real3rr0r303yt/jarvis/
├── MainActivity.kt                    # Compose host + telemetry loop
├── JarvisApplication.kt
├── service/
│   ├── JarvisAccessibilityService.kt  # on-screen automation core
│   ├── JarvisNotificationListenerService.kt
│   └── JarvisForegroundService.kt     # background monitoring + notification
├── system/
│   ├── SystemController.kt            # Wi-Fi / BT / volume / display / apps
│   └── ElevatedShell.kt              # optional Shizuku / root channel
├── agent/
│   ├── JarvisAgentEngine.kt          # Planner agent (Thought/Action/Observation)
│   ├── JarvisTool.kt                 # tool interface + step model
│   ├── Tools.kt                      # WebSearch/AppAutomation/SystemControl/LlmBrain
│   ├── DocTool.kt                    # DocAgent — local PDF generation (PdfDocument)
│   ├── SystemCheckTool.kt            # diagnostics tool for the planner
│   └── llm/LlmClient.kt              # OpenAI / Groq / Gemini client
├── trigger/
│   ├── AppTriggerManager.kt          # foreground-app launch trigger (debounced)
│   ├── AudioTriggerManager.kt        # continuous background audio listener
│   └── TriggerNotifier.kt            # surfaces trigger output as a notification
├── tts/TtsManager.kt                 # text-to-speech output
├── overlay/OverlayHudService.kt      # draggable floating overlay HUD
├── brain/
│   ├── IntentParser.kt               # natural language -> actions
│   ├── CommandProcessor.kt           # agent routing + rule-based pipeline
│   ├── VoiceInput.kt                 # offline SpeechRecognizer wrapper
│   └── model/JarvisAction.kt
├── core/
│   ├── JarvisLog.kt                  # terminal action log (StateFlow)
│   ├── JarvisPrefs.kt                # whitelist + custom commands store
│   └── SystemStats.kt                # dashboard telemetry
└── ui/
    ├── theme/Theme.kt                # black + neon theme
    ├── components/HudComponents.kt
    ├── dashboard/DashboardScreen.kt
    └── settings/SettingsScreen.kt    # permissions, whitelist, commands
```

## ⚙️ Continuous Integration

`.github/workflows/android-release.yml` builds the release APK on every push
and pull request to `main` (and on demand via *workflow_dispatch*), uploading
`jarvis-ai-release-apk` as a build artifact.

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
3. **Runtime permissions** (prompted on first launch / first use):
   `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, and `RECORD_AUDIO` (for voice
   commands).
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
