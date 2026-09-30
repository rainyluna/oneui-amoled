# OneUI AMOLED (LSPosed Module)

An Xposed / LSPosed module that transforms Samsung **One UI Home Launcher** (`com.sec.android.app.launcher`) and **System UI** (`com.android.systemui`) dark theme into a true, 100% pitch black **AMOLED (#000000)** experience.

Designed specifically for OLED / AMOLED displays to maximize battery life, turn off pixels, and eliminate dull grey scrims and blurry backdrops.

---

## 🌟 Features

- **True Pitch Black Recents / Overview (`#000000`)**:
  - Replaces One UI's dark grey `#212121` overview background with 100% pure black.
  - Suppresses wallpaper blur brightening (`y2.e` / `WallpaperBlurView`) and forces `y2.d` (`ScrimView`) to draw solid black.
  - Sets pure black status bar and navigation bar decor for seamless edge-to-edge aesthetics.
- **Pure AMOLED Notification Shade**:
  - Every notification card (`NotificationBackgroundView`) is styled with pure black background layers while preserving rounded corner geometry and vibrant icons/text.
  - Eliminates grey card blur (`BackgroundBlurDrawable`).
  - Full backdrop blackout via `NotificationShadeWindowView.onDraw` and `SecPanelBackground`, eliminating transparent see-through glass and wallpaper bleed-through.
- **AMOLED Quick Settings Panel (One UI 8 Split Layout)**:
  - Wi-Fi & Bluetooth pill cards, 4x3 toggle container, media card, and SmartThings container are themed to pure AMOLED black.
  - Inactive toggle buttons have subtle `#161616` pills for crisp icon contrast.
  - Brightness slider track background is blacked out.
  - Jetpack Compose Material 3 `ColorScheme` containers patched to pure black.

---

## 📱 Requirements

- Android 13+ up to Android 16+ (One UI 6.x, 7.x, 8.x)
- Root access (KernelSU, APatch, or Magisk)
- Zygisk-LSPosed or compatible modern Xposed framework
- Target packages:
  - `com.sec.android.app.launcher` (One UI Home)
  - `com.android.systemui` (System UI)

---

## 🛠️ How It Works

1. **One UI Home Launcher (`com.sec.android.app.launcher`)**:
   - `com.android.quickstep.RecentsActivity`: Hooks `onCreate` and `onResume` to set window decor, status bar, and root container to `#000000`.
   - `y2.d` (`ScrimView`): Hooks `onDraw` and `b()` / `setEndColor` to draw solid `#000000` alpha.
   - `y2.e` (`WallpaperBlurView`): Silences hardware wallpaper blur.

2. **System UI (`com.android.systemui`)**:
   - `NotificationBackgroundView`: Sets `BackgroundBlurDrawable` alpha to 0 and styles all `GradientDrawable` layers in `mBackground` to `#000000`.
   - `NotificationShadeWindowView`: Hooks `onDraw` to guarantee an opaque pure black backdrop behind all notification cards and quick settings.
   - `SecPanelBackground`: Forces `mMaxAlpha` to 1.0f and sets solid black `GradientDrawable`.
   - `ColoredBGHelper`: Overrides alpha values to 255 and tints all container `LayerDrawable` layers to pure black.
   - `SecQSTileBaseView`: Sets inactive toggle circle backgrounds to subtle dark pill (`#161616`).
   - `androidx.compose.material3.ColorScheme`: Patches Compose surface/container colors to 64-bit AMOLED black.

---

## 🚀 Building from Source

```bash
cd oneui_amoled
./build.sh
```

The standalone script uses `javac`, `d8`, `aapt2`, `zipalign`, and `apksigner` to produce `oneui-amoled.apk`.

---

## 📄 License

MIT License
