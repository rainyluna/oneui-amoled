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
  - Every notification card (`NotificationBackgroundView`) is drawn directly with a solid, pure black (`#000000`) fill matching native corner radii, completely suppressing app tint leaks (Nu, Telegram, etc.) with zero wireframes.
  - Eliminates the window blur under heads-up notifications (`NotiRune.NOTI_STYLE_ENR_WINDOW_BLUR` & `BackgroundBlurDrawable`).
  - Full backdrop blackout via `SecQpBlurController.doBlur`, `SecCapturedBlurContainerBinder`, `SecPanelBackground`, and `ScrimController.updateScrimColor`, ensuring 100% opaque `#000000` black with zero wallpaper or background app blur showing through.
- **AMOLED Quick Settings Panel (One UI 8 Split Layout)**:
  - Wi-Fi & Bluetooth pill cards, 4x3 toggle container, media card, and SmartThings container are themed to solid pure AMOLED black.
  - Inactive toggle buttons have pure `#000000` circular pills with crisp white icons.
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
   - `NotiRune.NOTI_STYLE_ENR_WINDOW_BLUR`: Disabled via reflection to permanently prevent SurfaceFlinger from projecting a blur layer under Heads-Up Notification (HUN) banners.
   - `NotificationBackgroundView`: Replaces `seslRecoilDrawable` with direct, clean `#000000` AMOLED fill matching exact corner radii, and rejects `BackgroundBlurDrawable` in `setBackground`.
   - `SecQpBlurController.doBlur`: Synchronizes `SecPanelBackground` to solid `#000000` black at `alpha = 1.0f` on expansion and hides it on collapse.
   - `SecCapturedBlurContainerBinder`: Suppresses background app screenshot capture and forces `CapturedBlurContainer` to solid black.
   - `ScrimController.updateScrimColor`: Forces all shade scrims (`mScrimBehind`, etc.) to 100% opaque `#000000` black.
   - `SecPanelBackground`: Forces `mMaxAlpha` to 1.0f with pure black fill.
   - `ColoredBGHelper`: Forces alpha values to 255 and tints all container backgrounds to pure black.
   - `SecQSTileBaseView`: Sets inactive toggle circle backgrounds to pure AMOLED `#000000`.
   - `SecMediaControlPanel` & `SecPlayerViewHolder`: Strips `albumView` and sets `playerView` to solid `#000000` black.
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
