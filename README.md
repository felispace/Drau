# Drau

AR tracing app for artists using live camera overlays and smart alignment.

## Features

- **Live camera preview** with CameraX (front/back camera)
- **Image overlay** from device gallery with adjustable opacity (0-100%)
- **Pinch-to-zoom** and **drag** to resize and position the overlay
- **360° rotation** with two-finger gesture
- **Flash toggle** for low-light tracing
- **Image lock** to freeze overlay position while drawing
- **Screen lock** to prevent accidental touches
- **Timelapse capture** automatic screenshots saved to gallery
- **Collapsible side menu** for clean drawing view
- **Splash screen** with minimal branding

## Tech Stack

- Kotlin + Jetpack Compose
- CameraX (camera-view, camera-lifecycle)
- Coil (async image loading)
- Accompanist Permissions
- Material 3 + Material Icons Extended
- Min SDK 24 (Android 7.0+)

## Architecture

- Clean, modular architecture
- Single-activity with Compose navigation
- Mobile-first UX with kawaii/minimal visual style
- Low latency camera rendering
- Performant overlay with `graphicsLayer` transforms

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install

1. Transfer APK to your Android device
2. Enable "Install from unknown sources"
3. Open and install
4. Grant camera permission when prompted
