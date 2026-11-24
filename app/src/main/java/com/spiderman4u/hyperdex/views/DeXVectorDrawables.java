package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

public class DeXVectorDrawables {

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }

    /**
     * Universal SVG Path Vector Drawable.
     * Renders standard Material Design / OneUI SVG vector paths with exact pixel accuracy.
     */
    public static class SvgPathDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path originalPath;
        private final Path transformedPath = new Path();
        private final Matrix matrix = new Matrix();
        private final float viewportWidth;
        private final float viewportHeight;
        private int intrinsicSize = 24;

        public SvgPathDrawable(String pathData, int color, float viewportWidth, float viewportHeight) {
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
            Path parsed;
            try {
                parsed = PathParser.createPathFromPathData(pathData);
            } catch (Throwable t) {
                parsed = new Path();
            }
            this.originalPath = parsed;
        }

        public void setColor(int color) {
            paint.setColor(color);
            invalidateSelf();
        }

        public void setIntrinsicSize(int sizeDp) {
            this.intrinsicSize = sizeDp;
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            matrix.reset();
            float sx = (float) bounds.width() / viewportWidth;
            float sy = (float) bounds.height() / viewportHeight;
            matrix.setScale(sx, sy);
            matrix.postTranslate(bounds.left, bounds.top);
            transformedPath.reset();
            originalPath.transform(matrix, transformedPath);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;
            canvas.drawPath(transformedPath, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }
    }

    // Standard Material / OneUI Vector Path Strings
    public static final String PATH_WIFI =
            "M12,4C7.31,4 3.07,5.9 0,8.98L12,21 24,8.98C20.93,5.9 16.69,4 12,4zm0,3c3.77,0 7.21,1.48 9.77,3.9L12,18.73 2.23,10.9C4.79,8.48 8.23,7 12,7z";

    public static final String PATH_BELL =
            "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zm6,-6v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z";

    public static final String PATH_VOLUME_HIGH =
            "M3,9v6h4l5,5V4L7,9H3zm13.5,3c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02zM14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z";

    public static final String PATH_VOLUME_MUTE =
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v2.21l2.45,2.45c0.03,-0.2 0.05,-0.41 0.05,-0.63zm2.5,0c0,0.94 -0.2,1.82 -0.54,2.64l1.51,1.51C20.63,14.91 21,13.5 21,12c0,-4.28 -2.99,-7.86 -7,-8.77v2.06c2.89,0.86 5,3.54 5,6.71zM4.27,3L3,4.27l4.73,4.73H3v6h4l5,5v-6.73l4.25,4.25c-0.67,0.52 -1.42,0.93 -2.25,1.18v2.06c1.38,-0.31 2.63,-0.95 3.69,-1.81L19.73,21 21,19.73l-9,-9L4.27,3zM12,4L9.91,6.09 12,8.18V4z";

    public static final String PATH_SCREENSHOT =
            "M3,5v4h2V5h4V3H5c-1.1,0 -2,0.9 -2,2zm2,10H3v4c0,1.1 0.9,2 2,2h4v-2H5v-4zm14,4h-4v2h4c1.1,0 2,-0.9 2,-2v-4h-2v4zm0,-16h-4v2h4v4h2V5c0,-1.1 -0.9,-2 -2,-2zm-7,6c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z";

    public static final String PATH_RECENTS =
            "M6,4c-0.55,0 -1,0.45 -1,1v14c0,0.55 0.45,1 1,1s1,-0.45 1,-1V5c0,-0.55 -0.45,-1 -1,-1zm6,0c-0.55,0 -1,0.45 -1,1v14c0,0.55 0.45,1 1,1s1,-0.45 1,-1V5c0,-0.55 -0.45,-1 -1,-1zm6,0c-0.55,0 -1,0.45 -1,1v14c0,0.55 0.45,1 1,1s1,-0.45 1,-1V5c0,-0.55 -0.45,-1 -1,-1z";

    public static final String PATH_HOME =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm0,18c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z";

    public static final String PATH_BACK =
            "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z";

    public static final String PATH_SEARCH =
            "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zm-6,0C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z";

    public static final String PATH_APPS_GRID =
            "M4,8h4V4H4v4zm6,12h4v-4h-4v4zm-6,0h4v-4H4v4zm0,-6h4v-4H4v4zm6,0h4v-4h-4v4zm6,-10v4h4V4h-4zm-6,4h4V4h-4v4zm6,6h4v-4h-4v4zm0,6h4v-4h-4v4z";

    public static final String PATH_GEAR =
            "M19.14,12.94c0.04,-0.3 0.06,-0.61 0.06,-0.94 0,-0.32 -0.02,-0.64 -0.07,-0.94l2.03,-1.58c0.18,-0.14 0.23,-0.41 0.12,-0.61l-1.92,-3.32c-0.12,-0.22 -0.37,-0.29 -0.59,-0.22l-2.39,0.96c-0.5,-0.38 -1.03,-0.7 -1.62,-0.94L14.4,2.81c-0.04,-0.24 -0.24,-0.41 -0.48,-0.41h-3.84c-0.24,0 -0.43,0.17 -0.47,0.41L9.25,5.35C8.66,5.59 8.12,5.92 7.63,6.29L5.24,5.33c-0.22,-0.08 -0.47,0 -0.59,0.22L2.74,8.87c-0.12,0.21 -0.08,0.47 0.12,0.61l2.03,1.58c-0.05,0.3 -0.09,0.63 -0.09,0.94s0.02,0.64 0.07,0.94l-2.03,1.58c-0.18,0.14 -0.23,0.41 -0.12,0.61l1.92,3.32c0.12,0.22 0.37,0.29 0.59,0.22l2.39,-0.96c0.5,0.38 1.03,0.7 1.62,0.94l0.36,2.54c0.05,0.24 0.24,0.41 0.48,0.41h3.84c0.24,0 0.44,-0.17 0.47,-0.41l0.36,-2.54c0.59,-0.24 1.13,-0.56 1.62,-0.94l2.39,0.96c0.22,0.08 0.47,0 0.59,-0.22l1.92,-3.32c0.12,-0.22 0.07,-0.47 -0.12,-0.61l-2.01,-1.58zM12,15.6c-1.98,0 -3.6,-1.62 -3.6,-3.6s1.62,-3.6 3.6,-3.6 3.6,1.62 3.6,3.6 -1.62,3.6 -3.6,3.6z";

    public static final String PATH_BLUETOOTH =
            "M17.71,7.71L12,2h-1v7.59L6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 11,14.41V22h1l5.71,-5.71 -4.3,-4.29 4.3,-4.29zM13,5.83l1.88,1.88L13,9.59V5.83zm1.88,10.46L13,18.17v-3.76l1.88,1.88z";

    public static final String PATH_BRIGHTNESS =
            "M12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5zM2,13h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zm18,0h2c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1h-2c-0.55,0 -1,0.45 -1,1s0.45,1 1,1zM11,2v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1V2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zm0,18v2c0,0.55 0.45,1 1,1s1,-0.45 1,-1v-2c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1zM5.99,4.58c-0.39,-0.39 -1.03,-0.39 -1.41,0s-0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41L5.99,4.58zm12.37,12.37c-0.39,-0.39 -1.03,-0.39 -1.41,0s-0.39,1.03 0,1.41l1.06,1.06c0.39,0.39 1.03,0.39 1.41,0s0.39,-1.03 0,-1.41l-1.06,-1.06zm1.06,-10.96c0.39,-0.39 0.39,-1.03 0,-1.41s-1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06zM7.05,18.36c0.39,-0.39 0.39,-1.03 0,-1.41s-1.03,-0.39 -1.41,0l-1.06,1.06c-0.39,0.39 -0.39,1.03 0,1.41s1.03,0.39 1.41,0l1.06,-1.06z";

    public static final String PATH_DND =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm5,11H7v-2h10v2z";

    public static final String PATH_TORCH =
            "M6,2h12v3l-3,3v14H9V8L6,5V2zm2,2v0.6l2.4,2.4H13.6L16,4.6V4H8z";

    public static SvgPathDrawable createWifiDrawable(int color) {
        return new SvgPathDrawable(PATH_WIFI, color, 24, 24);
    }

    public static SvgPathDrawable createBellDrawable(int color) {
        return new SvgPathDrawable(PATH_BELL, color, 24, 24);
    }

    public static SvgPathDrawable createVolumeDrawable(int color, boolean isMuted) {
        return new SvgPathDrawable(isMuted ? PATH_VOLUME_MUTE : PATH_VOLUME_HIGH, color, 24, 24);
    }

    public static SvgPathDrawable createScreenshotDrawable(int color) {
        return new SvgPathDrawable(PATH_SCREENSHOT, color, 24, 24);
    }

    public static SvgPathDrawable createRecentsDrawable(int color) {
        return new SvgPathDrawable(PATH_RECENTS, color, 24, 24);
    }

    public static SvgPathDrawable createHomeDrawable(int color) {
        return new SvgPathDrawable(PATH_HOME, color, 24, 24);
    }

    public static SvgPathDrawable createBackDrawable(int color) {
        return new SvgPathDrawable(PATH_BACK, color, 24, 24);
    }

    public static SvgPathDrawable createSearchDrawable(int color) {
        return new SvgPathDrawable(PATH_SEARCH, color, 24, 24);
    }

    public static SvgPathDrawable createAppsDrawable(int color) {
        return new SvgPathDrawable(PATH_APPS_GRID, color, 24, 24);
    }

    public static SvgPathDrawable createGearDrawable(int color) {
        return new SvgPathDrawable(PATH_GEAR, color, 24, 24);
    }

    public static SvgPathDrawable createBluetoothDrawable(int color) {
        return new SvgPathDrawable(PATH_BLUETOOTH, color, 24, 24);
    }

    public static SvgPathDrawable createBrightnessDrawable(int color) {
        return new SvgPathDrawable(PATH_BRIGHTNESS, color, 24, 24);
    }

    public static SvgPathDrawable createDndDrawable(int color) {
        return new SvgPathDrawable(PATH_DND, color, 24, 24);
    }

    public static SvgPathDrawable createTorchDrawable(int color) {
        return new SvgPathDrawable(PATH_TORCH, color, 24, 24);
    }

    /**
     * Authentic Samsung DeX Horizontal Battery Capsule Drawable
     * Body pill + tip + level fill + green charging lightning bolt
     */
    public static class BatteryDrawable extends Drawable {
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boltPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bodyRect = new RectF();
        private final RectF tipRect = new RectF();
        private final RectF fillRect = new RectF();
        private final Path boltPath = new Path();

        private int level = 100;
        private boolean isCharging = false;

        public BatteryDrawable(int color) {
            strokePaint.setColor(color);
            strokePaint.setStyle(Paint.Style.STROKE);

            fillPaint.setColor(color);
            fillPaint.setStyle(Paint.Style.FILL);

            boltPaint.setColor(Color.parseColor("#4CD964")); // Green charging bolt
            boltPaint.setStyle(Paint.Style.FILL);
        }

        public void setLevelAndCharging(int level, boolean charging) {
            this.level = Math.max(0, Math.min(100, level));
            this.isCharging = charging;
            if (charging) {
                fillPaint.setColor(Color.parseColor("#4CD964"));
            } else if (level <= 20) {
                fillPaint.setColor(Color.parseColor("#FF453A")); // Red low battery
            } else {
                fillPaint.setColor(Color.parseColor("#E1E2E8"));
            }
            invalidateSelf();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;

            float w = bounds.width();
            float h = bounds.height();
            float stroke = Math.max(1.5f, h * 0.1f);
            strokePaint.setStrokeWidth(stroke);

            float bodyWidth = w * 0.82f;
            float bodyHeight = h * 0.58f;
            float top = bounds.top + (h - bodyHeight) / 2f;
            float left = bounds.left + stroke / 2f;

            // Outer battery body capsule
            bodyRect.set(left, top, left + bodyWidth, top + bodyHeight);
            float radius = bodyHeight * 0.24f;
            canvas.drawRoundRect(bodyRect, radius, radius, strokePaint);

            // Battery positive tip on right
            float tipW = w * 0.08f;
            float tipH = bodyHeight * 0.44f;
            float tipTop = bounds.top + (h - tipH) / 2f;
            float tipLeft = bodyRect.right + stroke * 0.5f;
            tipRect.set(tipLeft, tipTop, tipLeft + tipW, tipTop + tipH);
            canvas.drawRoundRect(tipRect, radius * 0.5f, radius * 0.5f, fillPaint);

            // Inner fill
            float inset = stroke * 1.5f;
            float availableFillWidth = (bodyRect.width() - inset * 2f);
            float currentFillWidth = availableFillWidth * (level / 100f);
            if (currentFillWidth > 1f) {
                fillRect.set(bodyRect.left + inset, bodyRect.top + inset, bodyRect.left + inset + currentFillWidth, bodyRect.bottom - inset);
                canvas.drawRoundRect(fillRect, radius * 0.5f, radius * 0.5f, fillPaint);
            }

            // Charging lightning bolt indicator
            if (isCharging) {
                float cx = bodyRect.centerX();
                float cy = bodyRect.centerY();
                float bh = bodyHeight * 0.75f;
                float bw = bh * 0.55f;

                boltPath.reset();
                boltPath.moveTo(cx + bw * 0.1f, cy - bh * 0.5f);
                boltPath.lineTo(cx - bw * 0.45f, cy + bh * 0.05f);
                boltPath.lineTo(cx - bw * 0.05f, cy + bh * 0.05f);
                boltPath.lineTo(cx - bw * 0.2f, cy + bh * 0.5f);
                boltPath.lineTo(cx + bw * 0.45f, cy - bh * 0.05f);
                boltPath.lineTo(cx + bw * 0.05f, cy - bh * 0.05f);
                boltPath.close();

                canvas.drawPath(boltPath, boltPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            strokePaint.setAlpha(alpha);
            fillPaint.setAlpha(alpha);
            boltPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            strokePaint.setColorFilter(colorFilter);
            fillPaint.setColorFilter(colorFilter);
            boltPaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 24;
        }

        @Override
        public int getIntrinsicHeight() {
            return 14;
        }
    }
}
