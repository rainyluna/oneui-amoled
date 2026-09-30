package com.vertigo.oneuiamoled;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import android.widget.ImageView;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
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
        // 0. Disable Notification Row Window Blur (eliminates weird blur under HUN banner)
        try {
            Class<?> notiRuneClass = XposedHelpers.findClass("com.android.systemui.NotiRune", cl);
            Field f = notiRuneClass.getDeclaredField("NOTI_STYLE_ENR_WINDOW_BLUR");
            f.setAccessible(true);
            f.setBoolean(null, false);
            XposedBridge.log(TAG + "Disabled NotiRune.NOTI_STYLE_ENR_WINDOW_BLUR (killed HUN window blur)");
        } catch (Throwable t) {
        }

        // 1. Notification Card Background (NotificationBackgroundView)
        try {
            Class<?> notifBgClass = XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.NotificationBackgroundView", cl);

            XposedHelpers.findAndHookMethod(notifBgClass, "setBackground", Drawable.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = null; // Kills BackgroundBlurDrawable permanently
                }
            });
            
            XposedHelpers.findAndHookMethod(notifBgClass, "setTint", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = COLOR_AMOLED_BLACK;
                }
            });

            XposedHelpers.findAndHookMethod(notifBgClass, "setDrawableAlpha", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = 255;
                }
            });

            try {
                Class<?> recoilClass = XposedHelpers.findClass("androidx.appcompat.graphics.drawable.SeslRecoilDrawable", cl);
                XposedHelpers.findAndHookMethod(notifBgClass, "setCustomBackground", recoilClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object bg = param.args[0];
                        if (bg != null) {
                            try {
                                XposedHelpers.callMethod(bg, "setColorFilter", COLOR_AMOLED_BLACK, android.graphics.PorterDuff.Mode.SRC);
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
            } catch (Throwable ignored) {}

            final Paint amoledFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            amoledFillPaint.setStyle(Paint.Style.FILL);
            amoledFillPaint.setColor(COLOR_AMOLED_BLACK);

            XposedHelpers.findAndHookMethod(notifBgClass, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Canvas canvas = (Canvas) param.args[0];
                    if (canvas != null) {
                        View nbv = (View) param.thisObject;
                        int h = nbv.getHeight();
                        try {
                            h = (int) XposedHelpers.callMethod(nbv, "getActualHeight");
                        } catch (Throwable ignored) {}

                        float[] radii = null;
                        try {
                            radii = (float[]) XposedHelpers.getObjectField(nbv, "mCornerRadii");
                        } catch (Throwable ignored) {}

                        Path p = new Path();
                        RectF r = new RectF(0, 0, nbv.getWidth(), h);
                        if (radii != null) {
                            p.addRoundRect(r, radii, Path.Direction.CW);
                        } else {
                            p.addRect(r, Path.Direction.CW);
                        }
                        canvas.drawPath(p, amoledFillPaint);
                    }
                    param.setResult(null); // SUPPRESS seslRecoilDrawable (kills grey Nu / purple Telegram tints completely!)
                }
            });
            XposedBridge.log(TAG + "Hooked NotificationBackgroundView (direct solid pure black fill, no wireframe)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking NotificationBackgroundView: " + t);
        }
        // 1a. SecQpBlurController (Force SecPanelBackground to solid black on shade expand)
        try {
            Class<?> qpBlurClass = XposedHelpers.findClass("com.android.systemui.blur.SecQpBlurController", cl);
            XposedHelpers.findAndHookMethod(qpBlurClass, "doBlur", float.class, "com.android.systemui.blur.di.SecPanelBlurBinding$BlurType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    float f = (float) param.args[0];
                    try {
                        Object panelBgBinding = XposedHelpers.getObjectField(param.thisObject, "panelBackgroundBinding");
                        if (panelBgBinding != null) {
                            View view = (View) XposedHelpers.getObjectField(panelBgBinding, "view");
                            if (view != null) {
                                if (f > 0.01f) {
                                    view.setVisibility(View.VISIBLE);
                                    view.setBackgroundColor(COLOR_AMOLED_BLACK);
                                    XposedHelpers.setFloatField(view, "mMaxAlpha", 1.0f);
                                    XposedHelpers.callMethod(view, "setAlpha", 1.0f);
                                } else {
                                    view.setVisibility(View.GONE);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedBridge.log(TAG + "Hooked SecQpBlurController.doBlur for 100% pitch black AMOLED backdrop");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecQpBlurController.doBlur: " + t);
        }

        // 1b. SecCapturedBlurContainerBinder (Suppress screenshotting background app & force solid black)
        try {
            Class<?> cbBinderClass = XposedHelpers.findClass("com.android.systemui.blur.ui.viewbinder.SecCapturedBlurContainerBinder", cl);
            XposedHelpers.findAndHookMethod(cbBinderClass, "doBlur", "com.android.systemui.blur.di.SecPanelBlurBinding$BlurType", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null); // SUPPRESS SCREENSHOTTING / BLURRING THE BACKGROUND APP!
                }
            });
            XposedHelpers.findAndHookMethod(cbBinderClass, "setFraction", float.class, "com.android.systemui.blur.di.SecPanelBlurBinding$BlurType", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) XposedHelpers.getObjectField(param.thisObject, "view");
                    if (v != null) {
                        float f = (float) param.args[0];
                        if (f > 0.01f) {
                            v.setBackgroundColor(COLOR_AMOLED_BLACK);
                            v.setAlpha(1.0f);
                        } else {
                            v.setAlpha(0.0f);
                        }
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked SecCapturedBlurContainerBinder (suppressed app screenshot blur, forced solid black)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecCapturedBlurContainerBinder: " + t);
        }

        // 1c. CapturedBlurContainer (Ensure visible & black)
        try {
            Class<?> capturedBlurClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.CapturedBlurContainer", cl);
            XposedHelpers.findAndHookConstructor(capturedBlurClass, Context.class, android.util.AttributeSet.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    v.setVisibility(View.VISIBLE);
                    v.setBackgroundColor(COLOR_AMOLED_BLACK);
                }
            });
            XposedBridge.log(TAG + "Hooked CapturedBlurContainer constructor for AMOLED backdrop");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking CapturedBlurContainer: " + t);
        }

        // 1c. SecQSNewBlurView (Quick Settings hardware blur layer -> solid black)
        try {
            Class<?> qsNewBlurClass = XposedHelpers.findClass("com.android.systemui.blur.SecQSNewBlurView", cl);
            XposedHelpers.findAndHookMethod(qsNewBlurClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    v.setBackgroundColor(COLOR_AMOLED_BLACK);
                    try {
                        ImageView iv = (ImageView) XposedHelpers.getObjectField(v, "imageView");
                        if (iv != null) {
                            iv.setImageDrawable(null);
                            iv.setVisibility(View.GONE);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            XposedHelpers.findAndHookMethod(qsNewBlurClass, "setAlpha", float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    v.setBackgroundColor(COLOR_AMOLED_BLACK);
                }
            });
            XposedBridge.log(TAG + "Hooked SecQSNewBlurView for pure black backdrop");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecQSNewBlurView: " + t);
        }

        // 2. Samsung SecPanelBackground (Notification Shade backdrop blackout)
        try {
            Class<?> secPanelBgClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.SecPanelBackground", cl);
            XC_MethodHook panelHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    XposedHelpers.setFloatField(v, "mMaxAlpha", 1.0f);
                    Drawable bg = v.getBackground();
                    if (bg instanceof android.graphics.drawable.GradientDrawable) {
                        ((android.graphics.drawable.GradientDrawable) bg).setColor(COLOR_AMOLED_BLACK);
                        ((android.graphics.drawable.GradientDrawable) bg).setAlpha(255);
                    } else if (bg instanceof ColorDrawable) {
                        ((ColorDrawable) bg).setColor(COLOR_AMOLED_BLACK);
                    } else {
                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                        gd.setColor(COLOR_AMOLED_BLACK);
                        gd.setAlpha(255);
                        v.setBackground(gd);
                    }
                }
            };
            XposedHelpers.findAndHookConstructor(secPanelBgClass, Context.class, android.util.AttributeSet.class, panelHook);
            XposedHelpers.findAndHookMethod(secPanelBgClass, "setAlpha", float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View v = (View) param.thisObject;
                    XposedHelpers.setFloatField(v, "mMaxAlpha", 1.0f);
                    float f = (float) param.args[0];
                    if (f > 0.01f) {
                        param.args[0] = 1.0f;
                    }
                    Drawable bg = v.getBackground();
                    if (bg instanceof android.graphics.drawable.GradientDrawable) {
                        ((android.graphics.drawable.GradientDrawable) bg).setColor(COLOR_AMOLED_BLACK);
                        ((android.graphics.drawable.GradientDrawable) bg).setAlpha(255);
                    } else if (bg instanceof ColorDrawable) {
                        ((ColorDrawable) bg).setColor(COLOR_AMOLED_BLACK);
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked SecPanelBackground for 100% opaque AMOLED backdrop (GradientDrawable safe)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecPanelBackground: " + t);
        }

        // 2a-2. SecPanelBackgroundBinder.updateBackgroundColor (ensure pure black on QS blur background)
        try {
            Class<?> binderClass = XposedHelpers.findClass("com.android.systemui.blur.ui.viewbinder.SecPanelBackgroundBinder", cl);
            XposedHelpers.findAndHookMethod(binderClass, "updateBackgroundColor", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object binder = param.thisObject;
                    View view = (View) XposedHelpers.getObjectField(binder, "view");
                    if (view != null) {
                        view.setBackgroundColor(COLOR_AMOLED_BLACK);
                    }
                    View shadeWindow = (View) XposedHelpers.getObjectField(binder, "shadeWindowView");
                    if (shadeWindow != null) {
                        int qsBgId = shadeWindow.getResources().getIdentifier("qs_new_blur_background", "id", shadeWindow.getContext().getPackageName());
                        if (qsBgId != 0) {
                            View qsBg = shadeWindow.findViewById(qsBgId);
                            if (qsBg != null && qsBg.getBackground() instanceof android.graphics.drawable.GradientDrawable) {
                                ((android.graphics.drawable.GradientDrawable) qsBg.getBackground()).setColor(COLOR_AMOLED_BLACK);
                                ((android.graphics.drawable.GradientDrawable) qsBg.getBackground()).setAlpha(255);
                            }
                        }
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked SecPanelBackgroundBinder.updateBackgroundColor for pure black");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecPanelBackgroundBinder: " + t);
        }

        // 2c. ScrimController & ScrimView (100% Solid Pitch Black Backdrop)
        try {
            Class<?> scrimControllerClass = XposedHelpers.findClass("com.android.systemui.statusbar.phone.ScrimController", cl);
            XposedHelpers.findAndHookMethod(scrimControllerClass, "updateScrimColor", View.class, float.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    float alpha = (float) param.args[1];
                    if (alpha > 0.01f) {
                        param.args[1] = 1.0f;
                    }
                    param.args[2] = COLOR_AMOLED_BLACK;
                }
            });

            Class<?> scrimViewClass = XposedHelpers.findClass("com.android.systemui.scrim.ScrimView", cl);
            XposedHelpers.findAndHookMethod(scrimViewClass, "setTint", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = COLOR_AMOLED_BLACK;
                }
            });

            Class<?> scrimDrawableClass = XposedHelpers.findClass("com.android.systemui.scrim.ScrimDrawable", cl);
            XposedHelpers.findAndHookMethod(scrimDrawableClass, "setColor", int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = COLOR_AMOLED_BLACK;
                }
            });
            XposedBridge.log(TAG + "Hooked ScrimController & ScrimView for pure black backdrop");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking ScrimController/ScrimView: " + t);
        }

        // 3. Quick Settings ColoredBGHelper (Container cards + Subtle Outlines)
        try {
            Class<?> coloredBgClass = XposedHelpers.findClass("com.android.systemui.qs.bar.ColoredBGHelper", cl);

            XC_MethodHook bgHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        XposedHelpers.setIntField(param.thisObject, "WALLPAPER_FIXED_ALPHA", 255);
                        XposedHelpers.setIntField(param.thisObject, "THEME_FIXED_ALPHA", 255);
                        XposedHelpers.setIntField(param.thisObject, "curAlpha", 255);
                        XposedHelpers.setIntField(param.thisObject, "curExtractColor", COLOR_AMOLED_BLACK);
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
                        if (d != null) {
                            try {
                                int solidId = view.getResources().getIdentifier("colored_bg_solid", "id", view.getContext().getPackageName());
                                if (solidId != 0) {
                                    Drawable solid = (Drawable) XposedHelpers.callMethod(d, "findDrawableByLayerId", solidId);
                                    if (solid != null) {
                                        solid.setTint(COLOR_AMOLED_BLACK);
                                        solid.setAlpha(255);
                                    }
                                }
                            } catch (Throwable ignored) {}
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
            XposedBridge.log(TAG + "Hooked ColoredBGHelper for AMOLED containers with subtle outlines");
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
                    if (state == 0 || state == 1) {
                        param.setResult(COLOR_AMOLED_BLACK); // 100% PURE AMOLED PITCH BLACK circle!
                    }
                }
            });
            XposedBridge.log(TAG + "Hooked SecQSTileBaseView inactive circle color");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecQSTileBaseView: " + t);
        }

        // 4b. Media Output Card (SecMediaControlPanel)
        try {
            Class<?> mediaPanelClass = XposedHelpers.findClass("com.android.systemui.media.SecMediaControlPanel", cl);
            for (Method m : mediaPanelClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName()) && m.getParameterCount() == 2) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object vh = XposedHelpers.getObjectField(param.thisObject, "mViewHolder");
                                if (vh != null) {
                                    try {
                                        ImageView albumView = (ImageView) XposedHelpers.getObjectField(vh, "albumView");
                                        if (albumView != null) {
                                            albumView.setVisibility(View.GONE);
                                            albumView.setImageDrawable(null);
                                        }
                                    } catch (Throwable ignored) {}
                                    View playerView = (View) XposedHelpers.getObjectField(vh, "playerView");
                                    if (playerView != null) {
                                        int density = Math.round(playerView.getResources().getDisplayMetrics().density);
                                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                                        gd.setColor(COLOR_AMOLED_BLACK);
                                        gd.setCornerRadius(26f * density);
                                        playerView.setBackground(gd);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    break;
                }
            }
            XposedBridge.log(TAG + "Hooked SecMediaControlPanel for AMOLED media card");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecMediaControlPanel: " + t);
        }

        // 4c. MediaType.getSupportArtwork (Disable album art covering media card)
        try {
            Class<?> mediaTypeClass = XposedHelpers.findClass("com.android.systemui.media.MediaType", cl);
            XposedHelpers.findAndHookMethod(mediaTypeClass, "getSupportArtwork", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(false);
                }
            });
            XposedBridge.log(TAG + "Disabled MediaType.getSupportArtwork for pure black media card");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking MediaType.getSupportArtwork: " + t);
        }

        // 4d. SecPlayerViewHolder (Disable album art from construction)
        try {
            Class<?> holderClass = XposedHelpers.findClass("com.android.systemui.media.SecPlayerViewHolder", cl);
            for (Constructor<?> c : holderClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(c, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            ImageView albumView = (ImageView) XposedHelpers.getObjectField(param.thisObject, "albumView");
                            if (albumView != null) {
                                albumView.setVisibility(View.GONE);
                                albumView.setBackground(null);
                                albumView.setForeground(null);
                                albumView.setImageDrawable(null);
                            }
                        } catch (Throwable ignored) {}
                    }
                });
            }
            XposedBridge.log(TAG + "Hooked SecPlayerViewHolder constructors");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "Error hooking SecPlayerViewHolder: " + t);
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
