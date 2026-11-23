package com.spiderman4u.hyperdex.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class DeXVectorDrawables {

    private static int dp(Context context, float val) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, context.getResources().getDisplayMetrics());
    }

    /**
     * Crisp Wi-Fi signal icon: 3 concentric curved arcs + bottom dot
     */
    public static class WifiDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private final int color;

        public WifiDrawable(int color) {
            this.color = color;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;

            float w = bounds.width();
            float h = bounds.height();
            float cx = bounds.left + w / 2f;
            float cy = bounds.top + h * 0.78f;

            float stroke = w * 0.09f;
            paint.setStrokeWidth(stroke);
            paint.setStyle(Paint.Style.STROKE);

            // Arc 1 (Outer)
            float r1 = w * 0.42f;
            oval.set(cx - r1, cy - r1, cx + r1, cy + r1);
            canvas.drawArc(oval, 225, 90, false, paint);

            // Arc 2 (Middle)
            float r2 = w * 0.28f;
            oval.set(cx - r2, cy - r2, cx + r2, cy + r2);
            canvas.drawArc(oval, 225, 90, false, paint);

            // Arc 3 (Inner dot)
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, stroke * 0.85f, paint);
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
            return 24;
        }

        @Override
        public int getIntrinsicHeight() {
            return 24;
        }
    }

    /**
     * Modern battery icon: Rounded body rectangle + positive tip on right + inner fill level
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

            // Outer battery body
            bodyRect.set(left, top, left + bodyWidth, top + bodyHeight);
            float radius = bodyHeight * 0.22f;
            canvas.drawRoundRect(bodyRect, radius, radius, strokePaint);

            // Battery positive tip
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

    /**
     * Clean notification bell icon: Bell dome + rim + clapper
     */
    public static class BellDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public BellDrawable(int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) return;

            float w = bounds.width();
            float h = bounds.height();
            float cx = bounds.left + w / 2f;
            float cy = bounds.top + h / 2f;

            float stroke = w * 0.08f;
            paint.setStrokeWidth(stroke);
            paint.setStyle(Paint.Style.STROKE);

            // Bell outline
            path.reset();
            float topY = cy - h * 0.35f;
            float bottomY = cy + h * 0.22f;
            float flareX = w * 0.35f;
            float neckX = w * 0.18f;

            path.moveTo(cx, topY);
            path.cubicTo(cx + neckX, topY, cx + neckX * 1.2f, cy, cx + flareX, bottomY);
            path.lineTo(cx - flareX, bottomY);
            path.cubicTo(cx - neckX * 1.2f, cy, cx - neckX, topY, cx, topY);
            canvas.drawPath(path, paint);

            // Top handle dot / arc
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawArc(cx - stroke, topY - stroke * 1.2f, cx + stroke, topY + stroke * 0.8f, 180, 180, false, paint);

            // Bottom clapper dot
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, bottomY + stroke * 0.8f, stroke * 0.9f, paint);
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
            return 22;
        }

        @Override
        public int getIntrinsicHeight() {
            return 22;
        }
    }
}
