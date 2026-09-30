package com.vertigo.oneuiamoled;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "[OneUI-AMOLED] ";
    private static final int COLOR_AMOLED_BLACK = 0xFF000000;

    private static final String PKG_LAUNCHER = "com.sec.android.app.launcher";
    private static final String PKG_SYSTEMUI = "com.android.systemui";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (PKG_LAUNCHER.equals(lpparam.packageName)) {
            XposedBridge.log(TAG + "Hooking Launcher: " + lpparam.packageName);
            hookLauncher(lpparam.classLoader);
        } else if (PKG_SYSTEMUI.equals(lpparam.packageName)) {
            XposedBridge.log(TAG + "Hooking SystemUI: " + lpparam.packageName);
            hookSystemUI(lpparam.classLoader);
        }
    }

    // ==========================================
    // Launcher Hooks (Recents, Scrim, Decor)
    // ==========================================
    private void hookLauncher(final ClassLoader cl) {
        // 1. Hook RecentsActivity window & root view
        try {
            Class<?> recentsActivityClass = XposedHelpers.findClass("com.android.quickstep.RecentsActivity", cl);
            XC_MethodHook recentsHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    applyWindowBlack(activity);
                    try {
                        int rootId = activity.getResources().getIdentifier("recents_activity_root_view", "id", activity.getPackageName());
                        if (rootId != 0) {
                            View rootView = activity.findViewById(rootId);
                            if (rootView != null) {
                                rootView.setBackgroundColor(COLOR_AMOLED_BLACK);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "Error setting root view background: " + t);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(recentsActivityClass, "onCreate", Bundle.class, recentsHook);
            XposedHelpers.findAndHookMethod(recentsActivityClass, "onResume", recentsHook);
            XposedBridge.log(TAG + "Hooked RecentsActivity window decor & root view");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking RecentsActivity: " + t);
        }

        // 2. Hook ScrimView (y2.d)
        try {
            Class<?> scrimViewClass = XposedHelpers.findClass("y2.d", cl);
            hookScrimViewClass(scrimViewClass);
            XposedBridge.log(TAG + "Hooked known ScrimView class: y2.d");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking y2.d: " + t);
        }

        // 3. Hook WallpaperBlurView (y2.e) to disable wallpaper blur brightening
        try {
            Class<?> blurViewClass = XposedHelpers.findClass("y2.e", cl);
            XposedHelpers.findAndHookMethod(blurViewClass, "a", float.class, float.class, XposedHelpers.findClass("com.honeyspace.sdk.SemBlurInfoWrapper$PresetConfigure", cl), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    v.setVisibility(View.GONE);
                    param.setResult(null);
                }
            });
            XposedBridge.log(TAG + "Hooked WallpaperBlurView y2.e to suppress blur");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking y2.e: " + t);
        }
    }

    private static void hookScrimViewClass(Class<?> clazz) {
        try {
            // Hook onDraw
            XposedHelpers.findAndHookMethod(clazz, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        XposedHelpers.setIntField(param.thisObject, "i", COLOR_AMOLED_BLACK);
                        XposedHelpers.setIntField(param.thisObject, "h", COLOR_AMOLED_BLACK);
                    } catch (Throwable ignored) {}
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Canvas canvas = (Canvas) param.args[0];
                    if (canvas != null) {
                        canvas.drawColor(COLOR_AMOLED_BLACK);
                    }
                }
            });

            for (Method m : clazz.getDeclaredMethods()) {
                if ("setEndColor".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                XposedHelpers.setIntField(param.thisObject, "h", COLOR_AMOLED_BLACK);
                                XposedHelpers.setIntField(param.thisObject, "i", COLOR_AMOLED_BLACK);
                            } catch (Throwable ignored) {}
                        }
                    });
                }
            }
            XposedBridge.log(TAG + "Successfully hooked ScrimView: " + clazz.getName());
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Failed to hook ScrimView class " + clazz.getName() + ": " + t);
        }
    }

    private static void applyWindowBlack(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.setStatusBarColor(COLOR_AMOLED_BLACK);
                window.setNavigationBarColor(COLOR_AMOLED_BLACK);
                View decor = window.getDecorView();
                if (decor != null) {
                    decor.setBackgroundColor(COLOR_AMOLED_BLACK);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "applyWindowBlack error: " + t);
        }
    }

    // ==========================================
    // SystemUI Hooks (Notifications, Scrim, QS)
    // ==========================================
    private void hookSystemUI(final ClassLoader cl) {
        // 1. Notification Card Background (NotificationBackgroundView)
        try {
            Class<?> notifBgClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView", cl);
            
            XposedHelpers.findAndHookMethod(notifBgClass, "setTint", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = COLOR_AMOLED_BLACK;
                }
            });

            XposedHelpers.findAndHookMethod(notifBgClass, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View nbv = (View) param.thisObject;
                    Drawable curBg = nbv.getBackground();
                    if (curBg != null) {
                        curBg.setAlpha(0);
                    }
                    Object bg = XposedHelpers.getObjectField(nbv, "mBackground");
                    if (bg != null) {
                        try {
                            XposedHelpers.callMethod(bg, "setColorFilter", COLOR_AMOLED_BLACK, android.graphics.PorterDuff.Mode.SRC);
                        } catch (Throwable ignored) {}
                        try {
                            int layers = (int) XposedHelpers.callMethod(bg, "getNumberOfLayers");
                            for (int i = 0; i < layers; i++) {
                                Drawable l = (Drawable) XposedHelpers.callMethod(bg, "getDrawable", i);
                                if (l instanceof android.graphics.drawable.GradientDrawable) {
                                    android.graphics.drawable.GradientDrawable gd = (android.graphics.drawable.GradientDrawable) l;
                                    gd.setColor(COLOR_AMOLED_BLACK);
                                    gd.setAlpha(255);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked NotificationBackgroundView (setTint + onDraw)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking NotificationBackgroundView: " + t);
        }

        // 2. Samsung SecPanelBackground (Notification Shade backdrop blackout)
        try {
            Class<?> secPanelBgClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.SecPanelBackground", cl);
            XC_MethodHook panelHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    XposedHelpers.setFloatField(v, "mMaxAlpha", 1.0f);
                    v.setBackgroundColor(COLOR_AMOLED_BLACK);
                }
            };
            XposedHelpers.findAndHookConstructor(secPanelBgClass, Context.class, android.util.AttributeSet.class, panelHook);
            XposedHelpers.findAndHookMethod(secPanelBgClass, "setAlpha", float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    XposedHelpers.setFloatField(v, "mMaxAlpha", 1.0f);
                    v.setBackgroundColor(COLOR_AMOLED_BLACK);
                }
            });
            XposedBridge.log(TAG + "Hooked SecPanelBackground for 100% opaque AMOLED backdrop");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecPanelBackground: " + t);
        }

        // 2b. NotificationShadeWindowView onDraw blackout (only when panel/QS is open, never for HUN popups)
        try {
            Class<?> shadeWindowClass = XposedHelpers.findClass("com.android.systemui.shade.NotificationShadeWindowView", cl);
            XposedHelpers.findAndHookMethod(shadeWindowClass, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View root = (View) param.thisObject;
                    int panelBgId = root.getResources().getIdentifier("notification_panel_background", "id", root.getContext().getPackageName());
                    int qsId = root.getResources().getIdentifier("qs_frame", "id", root.getContext().getPackageName());
                    View panelBg = panelBgId != 0 ? root.findViewById(panelBgId) : null;
                    View qs = qsId != 0 ? root.findViewById(qsId) : null;
                    
                    // ONLY black out when the pull-down shade panel or QS is actively visible.
                    // Never check notification_stack_scroller here, because HUN (heads-up popups) make
                    // the stack scroller visible over running apps without opening the shade!
                    boolean panelVisible = panelBg != null && panelBg.getVisibility() == View.VISIBLE && panelBg.getAlpha() > 0.01f;
                    boolean qsVisible = qs != null && qs.getVisibility() == View.VISIBLE && qs.getAlpha() > 0.01f;
                    
                    if (panelVisible || qsVisible) {
                        Canvas canvas = (Canvas) param.args[0];
                        if (canvas != null) {
                            float alpha = panelVisible ? panelBg.getAlpha() : (qs != null ? qs.getAlpha() : 1.0f);
                            int a = Math.round(Math.min(1.0f, alpha * 2.0f) * 255);
                            canvas.drawColor(Color.argb(a, 0, 0, 0));
                        }
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked NotificationShadeWindowView.onDraw for guaranteed blackout (panel gated)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking NotificationShadeWindowView.onDraw: " + t);
        }

        // 2c. CapturedBlurContainer (Suppress grey blur)
        try {
            Class<?> capturedBlurClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.CapturedBlurContainer", cl);
            XposedHelpers.findAndHookConstructor(capturedBlurClass, Context.class, android.util.AttributeSet.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    v.setVisibility(View.GONE);
                }
            });
            XposedBridge.log(TAG + "Hooked CapturedBlurContainer to suppress blur");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking CapturedBlurContainer: " + t);
        }

        // 2c. ScrimView & ScrimDrawable
        try {
            Class<?> scrimViewClass = XposedHelpers.findClass("com.android.systemui.scrim.ScrimView", cl);
            XposedHelpers.findAndHookMethod(scrimViewClass, "setTint", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = COLOR_AMOLED_BLACK;
                }
            });

            XposedHelpers.findAndHookMethod(scrimViewClass, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        String name = (String) XposedHelpers.getObjectField(param.thisObject, "mScrimName");
                        if ("behind_scrim".equals(name) || "notifications".equals(name)) {
                            Canvas canvas = (Canvas) param.args[0];
                            float alpha = XposedHelpers.getFloatField(param.thisObject, "mViewAlpha");
                            if (canvas != null && alpha > 0.05f) {
                                int a = Math.round(Math.min(1.0f, alpha * 1.5f) * 255);
                                canvas.drawColor(Color.argb(a, 0, 0, 0));
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });

            Class<?> scrimDrawableClass = XposedHelpers.findClass("com.android.systemui.scrim.ScrimDrawable", cl);
            XposedHelpers.findAndHookMethod(scrimDrawableClass, "setColor", int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = COLOR_AMOLED_BLACK;
                }
            });
            XposedBridge.log(TAG + "Hooked ScrimView & ScrimDrawable for pure black backdrop");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking ScrimView/ScrimDrawable: " + t);
        }
        // 3. Quick Settings ColoredBGHelper (Container cards)
        try {
            Class<?> coloredBgClass = XposedHelpers.findClass("com.android.systemui.qs.bar.ColoredBGHelper", cl);
            XC_MethodHook bgHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        XposedHelpers.setIntField(param.thisObject, "WALLPAPER_FIXED_ALPHA", 255);
                        XposedHelpers.setIntField(param.thisObject, "THEME_FIXED_ALPHA", 255);
                        XposedHelpers.setIntField(param.thisObject, "curAlpha", 255);
                        XposedHelpers.setIntField(param.thisObject, "actualAppliedColor", COLOR_AMOLED_BLACK);
                    } catch (Throwable ignored) {}
                    if (param.args.length > 1 && param.args[1] instanceof Integer) {
                        param.args[1] = COLOR_AMOLED_BLACK;
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View view = (View) param.args[0];
                    if (view != null) {
                        Drawable d = view.getBackground();
                        if (d instanceof LayerDrawable) {
                            LayerDrawable ld = (LayerDrawable) d;
                            for (int i = 0; i < ld.getNumberOfLayers(); i++) {
                                Drawable layer = ld.getDrawable(i);
                                if (layer instanceof android.graphics.drawable.GradientDrawable) {
                                    ((android.graphics.drawable.GradientDrawable) layer).setColor(COLOR_AMOLED_BLACK);
                                }
                            }
                            ld.setColorFilter(COLOR_AMOLED_BLACK, android.graphics.PorterDuff.Mode.SRC_IN);
                        } else if (d instanceof android.graphics.drawable.GradientDrawable) {
                            ((android.graphics.drawable.GradientDrawable) d).setColor(COLOR_AMOLED_BLACK);
                        }
                    }
                }
            };
            XposedHelpers.findAndHookMethod(coloredBgClass, "setBackGroundDrawable", View.class, int.class, bgHook);
            XposedHelpers.findAndHookMethod(coloredBgClass, "addBarBackground", View.class, boolean.class, bgHook);

            XposedHelpers.findAndHookMethod(coloredBgClass, "getBGColor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(COLOR_AMOLED_BLACK);
                }
            });
            XposedBridge.log(TAG + "Hooked ColoredBGHelper for QS containers");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking ColoredBGHelper: " + t);
        }

        // 4. Quick Settings Inactive Tile Icon Circle (SecQSTileBaseView)
        try {
            Class<?> tileBaseViewClass = XposedHelpers.findClass("com.android.systemui.qs.tileimpl.SecQSTileBaseView", cl);
            XposedHelpers.findAndHookMethod(tileBaseViewClass, "getCircleColor", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    int state = (int) param.args[0];
                    if (state == 0 || state == 1) { // Inactive or disabled
                        param.setResult(0xFF161616); // Subtle dark contrast circle
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked SecQSTileBaseView inactive circle color");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecQSTileBaseView: " + t);
        }

        // 5. Brightness Slider Track (ToggleSeekBar)
        try {
            Class<?> toggleSeekBarClass = XposedHelpers.findClass("com.android.systemui.settings.brightness.ToggleSeekBar", cl);
            XposedHelpers.findAndHookMethod(toggleSeekBarClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View sb = (View) param.thisObject;
                    try {
                        Drawable pd = (Drawable) XposedHelpers.callMethod(sb, "getProgressDrawable");
                        if (pd instanceof LayerDrawable) {
                            LayerDrawable ld = (LayerDrawable) pd;
                            Drawable bg = ld.getDrawable(0);
                            if (bg instanceof android.graphics.drawable.GradientDrawable) {
                                ((android.graphics.drawable.GradientDrawable) bg).setColor(COLOR_AMOLED_BLACK);
                            } else if (bg != null) {
                                bg.setTint(COLOR_AMOLED_BLACK);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + "Hooked ToggleSeekBar track background");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking ToggleSeekBar: " + t);
        }

        // 6. Jetpack Compose Material 3 ColorScheme
        try {
            Class<?> colorSchemeClass = XposedHelpers.findClass("androidx.compose.material3.ColorScheme", cl);
            for (java.lang.reflect.Constructor<?> ctor : colorSchemeClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        patchComposeColorScheme(param.thisObject);
                    }
                });
            }
            XposedBridge.log(TAG + "Hooked Compose ColorScheme constructor");
        } catch (Throwable ignored) {}
    }

    private static void patchComposeColorScheme(Object colorScheme) {
        if (colorScheme == null) return;
        long composeBlack = 0xFF00000000000000L;
        String[] surfaceFields = {
            "background", "surface", "surfaceVariant",
            "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
            "surfaceContainerLow", "surfaceContainerLowest", "surfaceBright", "surfaceDim"
        };
        Class<?> cl = colorScheme.getClass();
        for (String fieldName : surfaceFields) {
            try {
                java.lang.reflect.Field f = cl.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.setLong(colorScheme, composeBlack);
            } catch (Throwable ignored) {}
        }
    }
}
