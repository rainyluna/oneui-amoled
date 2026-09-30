# OneUI AMOLED

LSPosed module targeting Samsung One UI Home Launcher (`com.sec.android.app.launcher`) and System UI (`com.android.systemui`) on One UI 8 / Android 16. Replaces dark grey UI surfaces and semi-transparent blur backdrops with solid `#000000` AMOLED black.

## Technical Architecture

### 1. System UI (`com.android.systemui`)
- `com.android.systemui.NotiRune.NOTI_STYLE_ENR_WINDOW_BLUR`: Disabled via reflection on startup to prevent SurfaceFlinger from allocating and rendering background blur layers beneath Heads-Up Notification (HUN) banners.
- `com.android.systemui.statusbar.notification.row.NotificationBackgroundView`:
  - Intercepts `setDrawableAlpha` to block translucency.
  - Replaces default recoil drawables with solid `#000000` `GradientDrawable` instances maintaining exact corner radii.
  - Rejects `com.android.internal.graphics.drawable.BackgroundBlurDrawable` instances passed into `setBackground`.
- `com.android.systemui.shade.SecPanelBackground`: Enforces `mMaxAlpha = 1.0f` and pure black background color across notification shade expansions.
- `com.android.systemui.statusbar.phone.ColoredBGHelper`: Overrides alpha values to 255 and tints container layers to `#000000`.
- `com.android.systemui.qs.tileimpl.SecQSTileBaseView`: Configures inactive tile circle drawables to solid `#000000` fill.
- `com.android.systemui.blur.SecQpBlurController`: Hooks `doBlur` to manage opaque `#000000` visibility during pull-down transitions.
- `com.android.systemui.blur.ui.viewbinder.SecCapturedBlurContainerBinder`: Prevents background window capture passes and sets container views to `#000000`.
- `com.android.systemui.statusbar.phone.ScrimController`: Forces shade scrims (`mScrimBehind`) to opaque black.
- `com.android.systemui.media.SecMediaControlPanel` and `SecPlayerViewHolder`: Sets player view to `#000000` and detaches album art background tinting.
- `androidx.compose.material3.ColorScheme`: Scans class hierarchy dynamically and forces Material 3 dark container tokens to 64-bit AMOLED black.

### 2. One UI Home (`com.sec.android.app.launcher`)
- `com.android.quickstep.RecentsActivity`: Hooks `onCreate` and `onResume` to set window decor background, navigation bar, and status bar to `#000000`.
- `y2.d` (`ScrimView`): Overrides `b()` and `onDraw` to render an opaque `#000000` canvas.
- `y2.e` (`WallpaperBlurView`): Disables hardware wallpaper blur generation.

## Prerequisites

- Android 13 to Android 16 (API 33-36). Tested on SM-S916U1 running One UI 8.
- Working LSPosed environment (Zygisk-LSPosed, LSPosed_mod, or KernelSU Zygisk).
- Java Development Kit (JDK 17).
- Android SDK Build-Tools (34.0.0+) and Android API 34 platform jar.

## Building from Source

```bash
git clone https://github.com/rainyluna/oneui-amoled.git
cd oneui-amoled
chmod +x build.sh
./build.sh
```

### Build Pipeline Details
`build.sh` runs the following sequence without Gradle overhead:
1. `javac` compiles standalone Xposed stubs in `stubs/`.
2. `javac` compiles `src/com/vertigo/oneuiamoled/HookEntry.java` against `android.jar` and stub classes.
3. `d8` converts compiled classes into `classes.dex` targeting API 34.
4. `aapt2 compile` and `aapt2 link` compile manifests and resources (`res/`).
5. `zip` packages `classes.dex` and `assets/xposed_init` into the APK.
6. `zipalign` 4-byte aligns the package.
7. `apksigner` signs the package with debug RSA-2048 keys.
8. Output artifact: `oneui-amoled.apk`.

## Installation

1. Install `oneui-amoled.apk`:
   ```bash
   adb install -r oneui-amoled.apk
   ```
2. Open LSPosed Manager and enable the module.
3. Verify target package scope contains:
   - `com.android.systemui`
   - `com.sec.android.app.launcher`
4. Soft reboot `system_server` or restart SystemUI:
   ```bash
   adb shell "su -c 'kill \$(pidof system_server)'"
   ```
