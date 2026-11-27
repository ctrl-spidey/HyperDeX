package com.spiderman4u.hyperdex.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

public class DeXNotificationToastController {

    private static View sCurrentToastView = null;
    private static PopupWindow sCurrentPopupWindow = null;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sDismissRunnable = null;

    public static void showNotificationToast(Context context, String pkg, String title, String text, String time, DeXSystemTrayView trayView) {
        if (context == null || TextUtils.isEmpty(pkg)) return;
        if (!ConfigManager.isFloatingPopupsEnabled(context)) return;

        sHandler.post(() -> {
            try {
                final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                cleanupCurrentToast(wm);

                PackageManager pm = context.getPackageManager();
                String appLabel = pkg;
                Drawable appIcon = null;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    appLabel = pm.getApplicationLabel(ai).toString();
                    appIcon = pm.getApplicationIcon(ai);
                } catch (Throwable ignored) {}

                // Build Glass Acrylic Toast View
                final LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                int padH = dp(context, 14);
                int padV = dp(context, 10);
                card.setPadding(padH, padV, padH, padV);

                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.parseColor("#EB1C1E26"));
                bg.setCornerRadius(dp(context, 14));
                bg.setStroke(dp(context, 1), Color.parseColor("#38FFFFFF"));
                card.setBackground(bg);
                card.setElevation(dp(context, 12));

                // Header Row (App Icon + App Name + time)
                LinearLayout header = new LinearLayout(context);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);

                ImageView ivIcon = new ImageView(context);
                if (appIcon != null) {
                    ivIcon.setImageDrawable(appIcon);
                } else {
                    ivIcon.setImageDrawable(DeXVectorDrawables.createBellDrawable(Color.parseColor("#0A84FF")));
                }
                header.addView(ivIcon, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));

                TextView tvApp = new TextView(context);
                tvApp.setText(appLabel);
                tvApp.setTextColor(Color.parseColor("#B0B3BE"));
                tvApp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                tvApp.setTypeface(null, Typeface.BOLD);
                LinearLayout.LayoutParams lpApp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lpApp.setMarginStart(dp(context, 6));
                header.addView(tvApp, lpApp);

                TextView tvTime = new TextView(context);
                tvTime.setText(!TextUtils.isEmpty(time) ? time : "now");
                tvTime.setTextColor(Color.parseColor("#80838E"));
                tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                header.addView(tvTime);

                card.addView(header);

                // Title
                if (!TextUtils.isEmpty(title)) {
                    TextView tvTitle = new TextView(context);
                    tvTitle.setText(title);
                    tvTitle.setTextColor(Color.WHITE);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvTitle.setTypeface(null, Typeface.BOLD);
                    tvTitle.setMaxLines(1);
                    tvTitle.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lpTitle.topMargin = dp(context, 3);
                    card.addView(tvTitle, lpTitle);
                }

                // Text
                if (!TextUtils.isEmpty(text)) {
                    TextView tvBody = new TextView(context);
                    tvBody.setText(text);
                    tvBody.setTextColor(Color.parseColor("#D0D3DC"));
                    tvBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    tvBody.setMaxLines(2);
                    tvBody.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams lpBody = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lpBody.topMargin = dp(context, 2);
                    card.addView(tvBody, lpBody);
                }

                // Tap launches app
                card.setOnClickListener(v -> {
                    try {
                        Intent launch = pm.getLaunchIntentForPackage(pkg);
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(launch);
                        }
                    } catch (Throwable ignored) {}
                    dismissToastImmersive(wm, card, trayView);
                });

                int barHeight = ConfigManager.getTaskbarHeight(context);
                int cardWidth = dp(context, 290);
                int xOffset = dp(context, 14);
                int yOffset = dp(context, barHeight + 10);

                boolean addedOverlay = false;
                if (wm != null) {
                    try {
                        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                                cardWidth,
                                WindowManager.LayoutParams.WRAP_CONTENT,
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                                PixelFormat.TRANSLUCENT
                        );
                        wlp.gravity = Gravity.BOTTOM | Gravity.END;
                        wlp.x = xOffset;
                        wlp.y = yOffset;
                        wm.addView(card, wlp);
                        sCurrentToastView = card;
                        addedOverlay = true;
                    } catch (Throwable ignored) {}
                }

                // Fallback to non-focusable PopupWindow anchored to systemTrayView if overlay fails
                if (!addedOverlay && trayView != null && trayView.isAttachedToWindow()) {
                    PopupWindow pw = new PopupWindow(card, cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, false);
                    pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    pw.setOutsideTouchable(false);
                    pw.setTouchable(true);
                    pw.setElevation(dp(context, 12));
                    pw.showAtLocation(trayView, Gravity.BOTTOM | Gravity.END, xOffset, yOffset);
                    sCurrentPopupWindow = pw;
                    sCurrentToastView = card;
                    addedOverlay = true;
                }

                if (!addedOverlay) return;

                // 1. Fluid macOS-like Entrance Animation
                card.setAlpha(0f);
                card.setScaleX(0.86f);
                card.setScaleY(0.86f);
                card.setTranslationY(dp(context, 18));
                card.animate()
                        .alpha(1.0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .setDuration(300)
                        .setInterpolator(new PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                        .start();

                // 2. Schedule Immersive Ingestion directly into the notification bell after 3.4s
                sDismissRunnable = () -> dismissToastImmersive(wm, card, trayView);
                sHandler.postDelayed(sDismissRunnable, 3400);

            } catch (Throwable ignored) {}
        });
    }

    private static void cleanupCurrentToast(WindowManager wm) {
        if (sDismissRunnable != null) {
            sHandler.removeCallbacks(sDismissRunnable);
            sDismissRunnable = null;
        }
        if (sCurrentPopupWindow != null) {
            try {
                sCurrentPopupWindow.dismiss();
            } catch (Throwable ignored) {}
            sCurrentPopupWindow = null;
        }
        if (sCurrentToastView != null && wm != null) {
            try {
                wm.removeView(sCurrentToastView);
            } catch (Throwable ignored) {}
            sCurrentToastView = null;
        }
    }

    private static void dismissToastImmersive(WindowManager wm, View card, DeXSystemTrayView trayView) {
        if (card == null) return;
        try {
            float targetDx = dp(card.getContext(), 24);
            float targetDy = dp(card.getContext(), 34);

            View bellIcon = trayView != null ? trayView.getNotificationIcon() : null;
            if (bellIcon != null && bellIcon.isAttachedToWindow() && card.isAttachedToWindow()) {
                int[] cardLoc = new int[2];
                int[] bellLoc = new int[2];
                card.getLocationOnScreen(cardLoc);
                bellIcon.getLocationOnScreen(bellLoc);

                float cardCenterX = cardLoc[0] + card.getWidth() * 0.5f;
                float cardCenterY = cardLoc[1] + card.getHeight() * 0.5f;
                float bellCenterX = bellLoc[0] + bellIcon.getWidth() * 0.5f;
                float bellCenterY = bellLoc[1] + bellIcon.getHeight() * 0.5f;

                if (bellCenterX > 0 && bellCenterY > 0) {
                    targetDx = bellCenterX - cardCenterX;
                    targetDy = bellCenterY - cardCenterY;
                }
            }

            card.setPivotX(card.getWidth() * 0.5f);
            card.setPivotY(card.getHeight() * 0.5f);

            card.animate()
                    .alpha(0.0f)
                    .scaleX(0.08f)
                    .scaleY(0.08f)
                    .translationX(targetDx)
                    .translationY(targetDy)
                    .setDuration(320)
                    .setInterpolator(new PathInterpolator(0.32f, 0.0f, 0.15f, 1.0f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            removeToastViews(wm, card);
                            if (trayView != null) {
                                trayView.pulseNotificationBell();
                            }
                        }
                    })
                    .start();
        } catch (Throwable t) {
            removeToastViews(wm, card);
        }
    }

    private static void removeToastViews(WindowManager wm, View card) {
        if (sCurrentPopupWindow != null) {
            try {
                sCurrentPopupWindow.dismiss();
            } catch (Throwable ignored) {}
            sCurrentPopupWindow = null;
        }
        if (wm != null && card != null) {
            try {
                wm.removeView(card);
            } catch (Throwable ignored) {}
        }
        if (sCurrentToastView == card) {
            sCurrentToastView = null;
        }
    }

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
