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
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

public class DeXNotificationToastController {

    private static View sCurrentToastView = null;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sDismissRunnable = null;

    public static void showNotificationToast(Context context, String pkg, String title, String text, String time, DeXSystemTrayView trayView) {
        if (context == null || TextUtils.isEmpty(pkg)) return;
        if (!ConfigManager.isFloatingPopupsEnabled(context)) return;

        sHandler.post(() -> {
            try {
                final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                if (wm == null) return;

                // Dismiss any existing toast first
                if (sCurrentToastView != null) {
                    try {
                        wm.removeView(sCurrentToastView);
                    } catch (Throwable ignored) {}
                    sCurrentToastView = null;
                }
                if (sDismissRunnable != null) {
                    sHandler.removeCallbacks(sDismissRunnable);
                    sDismissRunnable = null;
                }

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
                bg.setColor(Color.parseColor("#E61E212B"));
                bg.setCornerRadius(dp(context, 14));
                bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
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
                tvTime.setText("now");
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

                // LayoutParams for WindowManager
                int barHeight = ConfigManager.getTaskbarHeight(context);
                int cardWidth = dp(context, 290);
                WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                        cardWidth,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                );
                wlp.gravity = Gravity.BOTTOM | Gravity.END;
                wlp.x = dp(context, 16);
                wlp.y = dp(context, barHeight + 10);

                wm.addView(card, wlp);
                sCurrentToastView = card;

                // 1. Fluid Entrance Animation (macOS/DeX spring curve)
                card.setAlpha(0f);
                card.setScaleX(0.88f);
                card.setScaleY(0.88f);
                card.setTranslationY(dp(context, 16));
                card.animate()
                        .alpha(1.0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .setDuration(320)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .start();

                // 2. Schedule Immersive Ingestion Animation after 3.8s
                sDismissRunnable = () -> dismissToastImmersive(wm, card, trayView);
                sHandler.postDelayed(sDismissRunnable, 3800);

            } catch (Throwable t) {
                // WindowManager overlay fallback
            }
        });
    }

    private static void dismissToastImmersive(WindowManager wm, View card, DeXSystemTrayView trayView) {
        if (card == null) return;
        try {
            card.setPivotX(card.getWidth() * 0.9f);
            card.setPivotY(card.getHeight());

            card.animate()
                    .alpha(0.0f)
                    .scaleX(0.12f)
                    .scaleY(0.12f)
                    .translationY(30f)
                    .setDuration(340)
                    .setInterpolator(new AccelerateInterpolator(1.6f))
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            try {
                                wm.removeView(card);
                            } catch (Throwable ignored) {}
                            if (sCurrentToastView == card) {
                                sCurrentToastView = null;
                            }
                            // Pulse notification bell when immersed
                            if (trayView != null) {
                                trayView.pulseNotificationBell();
                            }
                        }
                    })
                    .start();
        } catch (Throwable t) {
            try {
                wm.removeView(card);
            } catch (Throwable ignored) {}
            if (sCurrentToastView == card) {
                sCurrentToastView = null;
            }
        }
    }

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }
}
