# Talos Guardian 🛡️

**Talos Guardian** is an advanced, AI-powered child safety application for Android. It leverages on-device intelligence and the power of Google's Gemini models to provide real-time content monitoring, insightful reporting, and digital wellbeing tools, all while prioritizing privacy and battery efficiency.

---

## 🧐 The Problem

In today's digital landscape, children are exposed to vast amounts of uncurated content. Traditional parental control apps often rely on:
1.  **Strict Blocking**: Which can be over-restrictive and hamper learning.
2.  **Cloud-Heavy Analysis**: Sending every screenshot to the cloud, raising privacy concerns and data costs.
3.  **Manual Review**: Overwhelming parents with raw logs instead of actionable insights.

Parents need a solution that is **smart**, **privacy-focused**, and **autonomous**.

## 💡 The Solution

**Talos Guardian** introduces a "Tiered Defense" architecture:
1.  **Local First**: Lightweight local models (TFLite/Histogram) act as the first line of defense to filter safe content instantly.
2.  **AI Analysis**: Suspicious content is analyzed by **Gemini 1.5 Flash** (Multimodal) to understand context (bullying, explicit content, predatory behavior).
3.  **The Scholar**: Instead of spamming parents, the "Scholar" module aggregates data locally and generates a **Weekly Report** using Gemini, summarizing the child's digital week with high-level insights.

---

## 🚀 Key Features

### 1. 👁️ Real-Time Monitoring
-   **Visual Analysis**: Periodically captures and analyzes screen content using Gemini Vision when risk is detected.
-   **Text & Notification Analysis**: Monitors incoming notifications for harmful language (Cyberbullying, Predatory patterns).
-   **Battery Optimized**: Intelligent "Tiered Defense" ensures AI is only invoked when necessary, preserving battery life.

### 2. 🎓 "The Scholar" Weekly Reports
-   **Contextual Summaries**: Generates human-readable reports summarizing screen time, app usage, and safety events.
-   **Local Generation**: Reports are generated on the parent's device (or locally on the child's device during idle time) to save cloud costs.

### 3. 📊 Digital Wellbeing & App Usage
-   **Usage Statistics**: Tracks time spent in specific applications.
-   **Categorization**: Identifies educational vs. entertainment apps.

### 4. 🛡️ Robust Security & Persistence
-   **Device Owner Mode**: Prevents unauthorized uninstallation or tampering (requires ADB provisioning).
-   **Boot Persistence**: Automatically restarts monitoring after device reboot.
-   **Offline Buffering**: Logs are saved locally when offline and synced securely to the cloud (Firestore) once connectivity is restored.

---

## 🛠️ Architecture & Tech Stack

-   **Language**: Kotlin
-   **Architecture**: MVVM (Model-View-ViewModel)
-   **AI Core**: Google Gemini 1.5 Flash (via Google AI Client SDK)
-   **Persistence**: Room Database (Local), Firebase Firestore (Cloud)
-   **Background Processing**: Android WorkManager (Reliable scheduling for sync and reports)
-   **UI**: Jetpack Compose / XML Views

---

## 📱 User Guide

### Prerequisites
-   **Child Device**: Android 10+ (Recommended).
-   **Parent Device**: Any Android device with the Talos Guardian app (Parent Mode).

### Installation Steps
1.  **Install APK**: Install the `TalosGuardian.apk` on the child's device.
2.  **Grant Permissions**: Open the app and grant necessary permissions (Accessibility, Usage Stats, Notification Access).
3.  **Pairing**: Scan the QR code from the Parent's device to link the accounts.

### 🔐 Provisioning Device Owner (Recommended)
To prevent the child from uninstalling the app or bypassing restrictions, you must set Talos Guardian as the **Device Owner**.

1.  Enable **Developer Options** and **USB Debugging** on the child's device.
2.  Connect the device to a computer via USB.
3.  Run the following ADB command:

```bash
adb shell dpm set-device-owner com.talos.guardian/.receivers.TalosAdminReceiver
```

*Note: This may require a factory reset on some devices if accounts are already present.*

---

## 🤝 Contributing
This project is currently under active development.

## 📄 License
[Proprietary/Private] - All rights reserved.
