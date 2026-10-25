// SPDX-License-Identifier: MIT
package me.jxl.kiosk.plugins.photomoderator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

public final class FloatingButtonView extends View {
    public interface OnClickListener {
        void onButtonClicked();
    }

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Rect textBounds = new Rect();

    private String iconSymbol = "🛡️";
    private float idleAlpha = 0.70f;
    private OnClickListener listener;

    public FloatingButtonView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xDD1E242B); // High-contrast dark charcoal

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0x88FFFFFF); // Crisp semi-transparent border
        strokePaint.setStrokeWidth(dp(1.5f));

        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setColor(Color.WHITE);
    }

    public void setListener(OnClickListener listener) {
        this.listener = listener;
    }

    public void configure(String iconOption, int opacityPercent) {
        if (iconOption != null) {
            if (iconOption.contains("Warning")) iconSymbol = "⚠️";
            else if (iconOption.contains("Flag")) iconSymbol = "🚩";
            else if (iconOption.contains("Eye")) iconSymbol = "👁️";
            else iconSymbol = "🛡️";
        }
        this.idleAlpha = Math.max(0.15f, Math.min(1.0f, opacityPercent / 100.0f));
        setAlpha(idleAlpha);
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        float radius = Math.min(cx, cy) - dp(2.0f);

        if (radius <= 0) return;

        // Draw circular background
        canvas.drawCircle(cx, cy, radius, bgPaint);
        canvas.drawCircle(cx, cy, radius, strokePaint);

        // Draw icon centered
        float textSize = radius * 1.15f;
        iconPaint.setTextSize(textSize);
        iconPaint.getTextBounds(iconSymbol, 0, iconSymbol.length(), textBounds);
        float textY = cy - textBounds.exactCenterY();
        canvas.drawText(iconSymbol, cx, textY, iconPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            setAlpha(1.0f);
            setScaleX(0.90f);
            setScaleY(0.90f);
            return true; // Consume touch to prevent screensaver dismissal
        } else if (action == MotionEvent.ACTION_UP) {
            setScaleX(1.0f);
            setScaleY(1.0f);
            setAlpha(idleAlpha);
            if (listener != null) {
                listener.onButtonClicked();
            }
            return true; // Consume touch!
        } else if (action == MotionEvent.ACTION_CANCEL) {
            setScaleX(1.0f);
            setScaleY(1.0f);
            setAlpha(idleAlpha);
            return true;
        }
        return super.onTouchEvent(event);
    }
}
