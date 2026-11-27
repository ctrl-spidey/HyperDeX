package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.spiderman4u.hyperdex.config.ConfigManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Floating macOS/DeX-style Clock & Interactive Calendar widget popup anchored above tray clock.
 */
public class DeXClockPopup {

    private static PopupWindow sCurrentPopup = null;

    public static void show(Context context, View anchorView) {
        if (sCurrentPopup != null && sCurrentPopup.isShowing()) {
            dismiss();
            return;
        }
        DeXQuickSettingsPopup.dismiss();
        DeXNotificationPopup.dismiss();

        int width = dp(context, 332);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F416181F"));
        bg.setCornerRadius(dp(context, 18));
        bg.setStroke(dp(context, 1), Color.parseColor("#33FFFFFF"));
        root.setBackground(bg);

        // 1. Live Clock Header Card
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout timeCol = new LinearLayout(context);
        timeCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvClock = new TextView(context);
        tvClock.setTextColor(Color.WHITE);
        tvClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        tvClock.setTypeface(null, Typeface.BOLD);
        tvClock.setIncludeFontPadding(false);
        tvClock.setFontFeatureSettings("tnum");
        timeCol.addView(tvClock);

        TextView tvFullDate = new TextView(context);
        tvFullDate.setTextColor(Color.parseColor("#9DA3B4"));
        tvFullDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvFullDate.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lpSub = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSub.topMargin = dp(context, 4);
        timeCol.addView(tvFullDate, lpSub);

        headerRow.addView(timeCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTz = new TextView(context);
        String tzShort = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT);
        tvTz.setText(tzShort != null ? tzShort : "Local");
        tvTz.setTextColor(Color.parseColor("#5EB5FF"));
        tvTz.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvTz.setTypeface(null, Typeface.BOLD);
        tvTz.setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5));
        GradientDrawable tzBg = new GradientDrawable();
        tzBg.setColor(Color.parseColor("#220A84FF"));
        tzBg.setCornerRadius(dp(context, 10));
        tzBg.setStroke(dp(context, 1), Color.parseColor("#440A84FF"));
        tvTz.setBackground(tzBg);
        headerRow.addView(tvTz);

        root.addView(headerRow);

        View divider = new View(context);
        divider.setBackgroundColor(Color.parseColor("#1FFFFFFF"));
        LinearLayout.LayoutParams lpDiv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lpDiv.topMargin = dp(context, 14);
        lpDiv.bottomMargin = dp(context, 12);
        root.addView(divider, lpDiv);

        // 2. Interactive Monthly Calendar Section
        final Calendar displayMonth = Calendar.getInstance();
        displayMonth.set(Calendar.DAY_OF_MONTH, 1);

        LinearLayout monthHeader = new LinearLayout(context);
        monthHeader.setOrientation(LinearLayout.HORIZONTAL);
        monthHeader.setGravity(Gravity.CENTER_VERTICAL);
        monthHeader.setPadding(0, 0, 0, dp(context, 10));

        TextView tvMonthTitle = new TextView(context);
        tvMonthTitle.setTextColor(Color.WHITE);
        tvMonthTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvMonthTitle.setTypeface(null, Typeface.BOLD);
        monthHeader.addView(tvMonthTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnToday = createSmallPillButton(context, "Today", "#262934", "#5EB5FF");
        monthHeader.addView(btnToday);
        addSpace(monthHeader, context, 6);

        TextView btnPrev = createSmallPillButton(context, "‹", "#262934", "#E2E5EE");
        monthHeader.addView(btnPrev);
        addSpace(monthHeader, context, 4);

        TextView btnNext = createSmallPillButton(context, "›", "#262934", "#E2E5EE");
        monthHeader.addView(btnNext);

        root.addView(monthHeader);

        // Weekday Labels Row
        LinearLayout weekRow = new LinearLayout(context);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] days = new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        for (int i = 0; i < days.length; i++) {
            TextView tvDay = new TextView(context);
            tvDay.setText(days[i]);
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tvDay.setTypeface(null, Typeface.BOLD);
            tvDay.setTextColor(i == 0 ? Color.parseColor("#FF7675") : Color.parseColor("#8A90A0"));
            weekRow.addView(tvDay, new LinearLayout.LayoutParams(0, dp(context, 24), 1f));
        }
        root.addView(weekRow);

        // Calendar Grid Container (6 rows x 7 columns)
        LinearLayout gridContainer = new LinearLayout(context);
        gridContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(gridContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        Runnable renderCalendar = () -> {
            tvMonthTitle.setText(monthFmt.format(displayMonth.getTime()));
            gridContainer.removeAllViews();

            Calendar today = Calendar.getInstance();
            int curYear = displayMonth.get(Calendar.YEAR);
            int curMonth = displayMonth.get(Calendar.MONTH);

            Calendar cellCal = (Calendar) displayMonth.clone();
            int firstDow = cellCal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            cellCal.add(Calendar.DAY_OF_MONTH, -firstDow);

            for (int r = 0; r < 6; r++) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int c = 0; c < 7; c++) {
                    int y = cellCal.get(Calendar.YEAR);
                    int m = cellCal.get(Calendar.MONTH);
                    int d = cellCal.get(Calendar.DAY_OF_MONTH);
                    boolean isInMonth = (m == curMonth && y == curYear);
                    boolean isToday = (y == today.get(Calendar.YEAR)
                            && m == today.get(Calendar.MONTH)
                            && d == today.get(Calendar.DAY_OF_MONTH));

                    LinearLayout cellWrap = new LinearLayout(context);
                    cellWrap.setGravity(Gravity.CENTER);

                    TextView cell = new TextView(context);
                    cell.setText(String.valueOf(d));
                    cell.setGravity(Gravity.CENTER);
                    cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    if (isToday) {
                        cell.setTextColor(Color.WHITE);
                        cell.setTypeface(null, Typeface.BOLD);
                        GradientDrawable todayBg = new GradientDrawable();
                        todayBg.setColor(Color.parseColor("#0A84FF"));
                        todayBg.setShape(GradientDrawable.OVAL);
                        cell.setBackground(todayBg);
                    } else if (isInMonth) {
                        cell.setTextColor(c == 0 ? Color.parseColor("#FFA1A1") : Color.parseColor("#ECEEF5"));
                    } else {
                        cell.setTextColor(Color.parseColor("#4D5260"));
                    }
                    cellWrap.addView(cell, new LinearLayout.LayoutParams(dp(context, 30), dp(context, 30)));
                    row.addView(cellWrap, new LinearLayout.LayoutParams(0, dp(context, 34), 1f));
                    cellCal.add(Calendar.DAY_OF_MONTH, 1);
                }
                gridContainer.addView(row);
            }
        };

        btnPrev.setOnClickListener(v -> {
            displayMonth.add(Calendar.MONTH, -1);
            renderCalendar.run();
        });
        btnNext.setOnClickListener(v -> {
            displayMonth.add(Calendar.MONTH, 1);
            renderCalendar.run();
        });
        btnToday.setOnClickListener(v -> {
            Calendar nowCal = Calendar.getInstance();
            displayMonth.set(nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH), 1);
            renderCalendar.run();
        });
        renderCalendar.run();

        // 3. Footer Quick Launch Shortcuts
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpFooter = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpFooter.topMargin = dp(context, 12);
        root.addView(footer, lpFooter);

        View btnAlarms = createFooterShortcut(context, DeXVectorDrawables.createClockDrawable(Color.parseColor("#E6E9F2")), "Clock & Alarms", v -> {
            dismiss();
            launchClockApp(context);
        });
        footer.addView(btnAlarms, new LinearLayout.LayoutParams(0, dp(context, 38), 1f));

        addSpace(footer, context, 8);

        View btnCal = createFooterShortcut(context, DeXVectorDrawables.createCalendarDrawable(Color.parseColor("#E6E9F2")), "Calendar App", v -> {
            dismiss();
            launchCalendarApp(context);
        });
        footer.addView(btnCal, new LinearLayout.LayoutParams(0, dp(context, 38), 1f));

        // 4. Live Ticking Handler while visible
        final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault());
        final Handler ticker = new Handler(Looper.getMainLooper());
        final Runnable tickRunnable = new Runnable() {
            @Override
            public void run() {
                if (sCurrentPopup == null || !sCurrentPopup.isShowing()) return;
                Date now = new Date();
                tvClock.setText(timeFmt.format(now));
                tvFullDate.setText(dateFmt.format(now));
                ticker.postDelayed(this, 1000);
            }
        };

        PopupWindow popup = new PopupWindow(root, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(dp(context, 18));
        popup.setOnDismissListener(() -> {
            ticker.removeCallbacks(tickRunnable);
            if (sCurrentPopup == popup) {
                sCurrentPopup = null;
            }
        });

        int xOffset = dp(context, 12);
        int yOffset = dp(context, ConfigManager.getTaskbarHeight(context) + Math.max(0, ConfigManager.getTaskbarBottomOffset(context)) + 8);
        popup.showAtLocation(anchorView, Gravity.BOTTOM | Gravity.END, xOffset, yOffset);
        sCurrentPopup = popup;
        tickRunnable.run();

        // Smooth macOS entrance
        root.setAlpha(0f);
        root.setScaleX(0.92f);
        root.setScaleY(0.92f);
        root.setTranslationY(dp(context, 14));
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(new android.view.animation.PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f))
                .start();
    }

    public static void dismiss() {
        if (sCurrentPopup != null) {
            try {
                sCurrentPopup.dismiss();
            } catch (Throwable ignored) {}
            sCurrentPopup = null;
        }
    }

    private static void launchClockApp(Context context) {
        String[] clockPkgs = new String[]{"com.android.deskclock", "com.google.android.deskclock"};
        for (String pkg : clockPkgs) {
            try {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    return;
                }
            } catch (Throwable ignored) {}
        }
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static void launchCalendarApp(Context context) {
        String[] calPkgs = new String[]{"com.android.calendar", "com.xiaomi.calendar", "com.google.android.calendar"};
        for (String pkg : calPkgs) {
            try {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    return;
                }
            } catch (Throwable ignored) {}
        }
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    private static TextView createSmallPillButton(Context context, String label, String bgHex, String textHex) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(Color.parseColor(textHex));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(context, 8));
        tv.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), bg, null));
        return tv;
    }

    private static View createFooterShortcut(Context context, DeXVectorDrawables.SvgPathDrawable icon, String label, View.OnClickListener listener) {
        LinearLayout btn = new LinearLayout(context);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(context, 10), 0, dp(context, 10), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#242732"));
        bg.setCornerRadius(dp(context, 11));
        btn.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), bg, null));

        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon);
        btn.addView(iv, new LinearLayout.LayoutParams(dp(context, 15), dp(context, 15)));

        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#E6E9F2"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpTv = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTv.setMarginStart(dp(context, 6));
        btn.addView(tv, lpTv);

        btn.setOnClickListener(listener);
        return btn;
    }

    private static void addSpace(LinearLayout parent, Context context, int dpVal) {
        View s = new View(context);
        parent.addView(s, new LinearLayout.LayoutParams(dp(context, dpVal), 1));
    }

    private static int dp(Context context, float dpVal) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dpVal, context.getResources().getDisplayMetrics()
        ));
    }
}
