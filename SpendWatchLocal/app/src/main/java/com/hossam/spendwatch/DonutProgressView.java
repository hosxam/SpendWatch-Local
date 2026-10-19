package com.hossam.spendwatch;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Locale;

/** Lightweight, fully offline donut progress view; supports spending and savings. */
public class DonutProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private float fraction;
    private int ringColor = UiKit.GREEN;
    private String caption = "LEFT";
    private String centerText = "0%";

    public DonutProgressView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
    public DonutProgressView(Context context, AttributeSet attrs) { super(context, attrs); }

    public static int percentOf(double part, double total) {
        if (!Double.isFinite(part) || !Double.isFinite(total) || total <= 0) return 0;
        return (int) Math.round(Math.max(0d, Math.min(1d, part / total)) * 100d);
    }

    public void configure(double amount, double target, String label, int color) {
        int percent = percentOf(amount, target);
        fraction = percent / 100f;
        centerText = target <= 0 ? "—" : String.format(Locale.US, "%d%%", percent);
        caption = label == null ? "" : label.toUpperCase(Locale.ROOT);
        ringColor = color;
        setContentDescription(centerText + " " + caption);
        invalidate();
    }

    public int getDisplayedPercent() {
        if ("—".equals(centerText)) return -1;
        return Math.round(fraction * 100f);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = UiKit.dp(getContext(), 108);
        int width = resolveSize(size, widthMeasureSpec);
        int height = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float diameter = Math.min(w, h);
        float stroke = UiKit.dp(getContext(), 10);
        float pad = stroke / 2f + UiKit.dp(getContext(), 6);
        float left = (w - diameter) / 2f + pad;
        float top = (h - diameter) / 2f + pad;
        arc.set(left, top, w - left, h - top);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(UiKit.SURFACE_3);
        canvas.drawArc(arc, -90, 360, false, paint);

        if (fraction > 0f) {
            paint.setColor(ringColor);
            canvas.drawArc(arc, -90, fraction * 360f, false, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-black",
                android.graphics.Typeface.NORMAL));
        paint.setTextSize(UiKit.dp(getContext(), 25));
        paint.setColor(UiKit.TEXT);
        float centerX = w / 2f;
        float centerY = h / 2f;
        canvas.drawText(centerText, centerX, centerY + UiKit.dp(getContext(), 2), paint);

        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        paint.setTextSize(UiKit.dp(getContext(), 9));
        paint.setColor(UiKit.MUTED);
        canvas.drawText(caption, centerX, centerY + UiKit.dp(getContext(), 17), paint);
    }
}
