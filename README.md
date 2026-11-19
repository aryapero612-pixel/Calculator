# SmartCalc AI 🧮✨🤖

An AI-powered smart calculator for Android, built with Kotlin + Jetpack Compose.
The APK is built automatically by **GitHub Actions** on every push.

## Features

| Tab | What it does |
|---|---|
| 🧮 **Calc** | Fast calculator with live result preview, scientific keypad (sin/cos/tan + inverses, ln, log, √, xʸ, x!, π, e, Ans), DEG/RAD mode, smart `%` (`200+10%` = 220), implicit multiplication (`2π`, `3(4+1)`) |
| ✨ **Smart** | Plain-language questions, **fully offline**, with step-by-step working: percentages, GST/tax, tips, discounts, loan EMI, simple & compound interest, BMI, average/sum/min/max, unit conversion (length, mass, volume, area, speed, time, data, temperature), number words ("twenty five plus three") and 🎤 voice input |
| 🤖 **AI Solve** | Word problems and equations solved step by step by Claude (English / Hindi / Hinglish), with voice input and share. Needs your own Anthropic API key (Settings ⚙) |
| 🕘 **History** | Last 200 calculations saved on device; tap to reuse, long-press to copy |

Settings: light/dark/system theme (Material You colours on Android 12+), angle mode, key vibration, API key.

### Smart examples
```
15% of 2400                       → 360
18% gst on 1500                   → 1,770.00
emi 500000 at 8.5% for 20 years   → 4,339.12 / month
10 km to miles                    → 6.21371 mi
250 is what percent of 1000       → 25%
compound interest 10000 at 8% for 5 years
bmi 70 kg 175 cm
```

## Download the APK

1. Open the **Actions** tab → latest **Build Android APK** run → download the `SmartCalc-AI-apk` artifact (zip containing the APK).
2. Or push a version tag to get a GitHub Release with the APK attached:
   ```bash
   git tag v1.0.0 && git push origin v1.0.0
   ```

Install the APK on your phone (allow "Install unknown apps" for your browser/file manager).

## Project layout

```
core/   Pure Kotlin engine (expression parser, smart NL engine, unit converter, Claude client) + unit tests
app/    Android app (Jetpack Compose UI)
.github/workflows/android.yml   CI: tests → APK → artifact / release
```

## Build locally

Requires JDK 17 and the Android SDK.
```bash
./gradlew :core:test           # engine tests
./gradlew :app:assembleDebug   # APK at app/build/outputs/apk/debug/
```

## AI notes

AI Solve uses the official Anthropic Java SDK with the `claude-opus-5-5` model. The API key is stored only
in the app's private storage on your phone and is sent only to Anthropic's API.
The release APK is signed with a debug key so it installs directly — for Play Store publishing, set up your own signing config.
