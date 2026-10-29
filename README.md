# AetherShell for Android

Lean offline Android launcher + static wallpaper studio, created for POCO X8 Pro Max (Android 16 / HyperOS 3).

## Features

- Optional launcher: HOME intent, installed-app drawer, instant package/name search, pin/unpin favorites, app info shortcuts and clock/date.
- 12 original vector-like wallpapers rendered locally: Aurora, Nocturne, Silk, Horizon, Orbit, Bloom, Tide, Starlight, Prism, Mist, Amethyst, Ember.
- Apply image to home screen, lock screen, or both; export PNG to `Pictures/AetherShell`.
- Import any local image through Android's Storage Access Framework.
- Appearance: six accent colors, 3/4/5-column app grid, 44/52/60dp icons, app labels, minimal monogram icons, clock/date, AMOLED black base.
- Quick access to relevant system settings.
- All settings stored only on device via SharedPreferences.
- No third-party runtime libraries, Internet permission, service, accessibility permission or ad/analytics SDK.

## Install

Download the `AetherShell-debug-apk` artifact from GitHub Actions and install `app-debug.apk`.

```bash
adb install -r app-debug.apk
```

Launch AetherShell normally first. **This installation does not change your default launcher.** If you like it, select **Set as launcher** within the app and choose AetherShell in HyperOS defaults. The wallpaper changes only after explicitly pressing **Apply wallpaper** and choosing a target.

## Build

Android Gradle Plugin 8.9.2, Gradle 8.11.1, JDK 17, SDK 36, `minSdk 26`, `targetSdk 35`.

```bash
gradle :app:assembleDebug
```

GitHub Actions builds the APK automatically on every push and runs Android Lint.

## Scope and limitations

AetherShell can replace its own home screen only with user consent. It cannot silently replace HyperOS quick settings, notification shade, lock-screen UI, status bar, other apps' views, or system-wide app icons; those require OS/system privileges. It is deliberately **not** a live wallpaper, overlay, notification listener or continuously running process. Actual battery/memory performance should be measured on-device; no performance claims are made without tests.

Wallpaper photos use a persisted read-only URI; no general gallery/storage access is requested. Artwork is generated locally using Android Canvas.
