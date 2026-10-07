package com.hossam.spendwatch;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class UiKit {
    private UiKit() {}

    public static final int BG = Color.rgb(7, 11, 20);
    public static final int SURFACE = Color.rgb(15, 23, 42);
    public static final int SURFACE_2 = Color.rgb(20, 30, 52);
    public static final int SURFACE_3 = Color.rgb(26, 38, 62);
    public static final int BORDER = Color.rgb(43, 58, 82);
    public static final int TEXT = Color.rgb(244, 247, 252);
    public static final int MUTED = Color.rgb(148, 163, 184);
    public static final int ACCENT = Color.rgb(91, 124, 250);
    public static final int CYAN = Color.rgb(34, 211, 238);
    public static final int GREEN = Color.rgb(52, 211, 153);
    public static final int RED = Color.rgb(251, 113, 133);
    public static final int AMBER = Color.rgb(251, 191, 36);
    public static final int PURPLE = Color.rgb(167, 139, 250);

    public static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(Context c, int color, int radiusDp, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != 0) gd.setStroke(dp(c, 1), strokeColor);
        return gd;
    }

    public static GradientDrawable gradient(Context c, int radiusDp, int... colors) {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colors
        );
        gd.setCornerRadius(dp(c, radiusDp));
        return gd;
    }

    public static TextView text(Context c, String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return tv;
    }

    public static TextView label(Context c, String text) {
        TextView tv = text(c, text.toUpperCase(), 11, MUTED, true);
        tv.setLetterSpacing(0.08f);
        return tv;
    }

    public static LinearLayout card(Context c, int paddingDp) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, paddingDp);
        card.setPadding(p, p, p, p);
        card.setBackground(round(c, SURFACE, 24, BORDER));
        return card;
    }

    public static TextView pill(Context c, String text, int color) {
        TextView pill = text(c, text, 10, color, true);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        int bg = Color.argb(28, Color.red(color), Color.green(color), Color.blue(color));
        pill.setBackground(round(c, bg, 999, Color.argb(65, Color.red(color), Color.green(color), Color.blue(color))));
        return pill;
    }

    public static ProgressBar progress(Context c, int progress, int tint) {
        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);
        bar.setProgress(Math.max(0, Math.min(1000, progress)));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            bar.setProgressTintList(ColorStateList.valueOf(tint));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(39, 51, 73)));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 7));
        bar.setLayoutParams(lp);
        return bar;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Color.rgb(37, 49, 69));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    public static View gap(Context c, int dp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, UiKit.dp(c, dp)));
        return v;
    }
}
