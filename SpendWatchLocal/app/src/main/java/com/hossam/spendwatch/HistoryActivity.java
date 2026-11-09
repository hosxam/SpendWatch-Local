package com.hossam.spendwatch;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** Readable on-phone history; no expiration of historical transaction records. */
public final class HistoryActivity extends Activity {
    private SpendDatabase db;
    private LinearLayout content;
    private int maxMonths = 18;
    private int maxDays = 45;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = SpendDatabase.getInstance(this);
        getWindow().setStatusBarColor(UiKit.BG);
        getWindow().setNavigationBarColor(UiKit.BG);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiKit.dp(this, 20),UiKit.dp(this, 20),
                UiKit.dp(this, 20),UiKit.dp(this, 28));
        content.setBackgroundColor(UiKit.BG);
        scroll.addView(content);
        setContentView(scroll);
        refresh();
    }

    private void section(String label, List<String> lines) {
        content.addView(UiKit.text(this,label,19,UiKit.TEXT,true));
        content.addView(UiKit.gap(this,8));
        if(lines.isEmpty())content.addView(UiKit.text(this,"No recorded payments.",
                12,UiKit.MUTED,false));
        for(String line:lines){
            LinearLayout card=UiKit.card(this,13);
            card.addView(UiKit.text(this,line,13,UiKit.TEXT,false));
            content.addView(card);
            content.addView(UiKit.gap(this,6));
        }
    }

    private void refresh(){
        content.removeAllViews();
        TextView back=UiKit.pill(this,"‹ BACK",UiKit.CYAN);
        back.setOnClickListener(v->finish());
        content.addView(back);
        content.addView(UiKit.gap(this,18));
        content.addView(UiKit.text(this,"Your spending history",27,UiKit.TEXT,true));
        content.addView(UiKit.gap(this,8));
        content.addView(UiKit.text(this,
                "Browse earlier calendar months and days. Every saved transaction "
                        + "also stays available in the Activity ledger and Excel export.",
                12,UiKit.MUTED,false));
        content.addView(UiKit.gap(this,22));
        List<String> months=db.getHistoricalTotals(false,maxMonths);
        section("MONTH BY MONTH",months);
        content.addView(UiKit.gap(this,8));
        TextView moreMonths=UiKit.pill(this,"SHOW OLDER MONTHS",UiKit.CYAN);
        moreMonths.setOnClickListener(v->{maxMonths+=18;refresh();});
        content.addView(moreMonths);
        content.addView(UiKit.gap(this,26));
        List<String> days=db.getHistoricalTotals(true,maxDays);
        section("DAY BY DAY",days);
        content.addView(UiKit.gap(this,8));
        TextView moreDays=UiKit.pill(this,"SHOW OLDER DAYS",UiKit.PURPLE);
        moreDays.setOnClickListener(v->{maxDays+=45;refresh();});
        content.addView(moreDays);
    }
}
