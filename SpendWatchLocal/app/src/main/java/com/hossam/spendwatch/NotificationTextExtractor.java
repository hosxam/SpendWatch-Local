package com.hossam.spendwatch;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.LinkedHashSet;
import java.util.Set;

final class NotificationTextExtractor {

    private NotificationTextExtractor() {}

    static String title(Notification notification) {
        if (notification == null || notification.extras == null) return "";
        return stringValue(notification.extras.getCharSequence(Notification.EXTRA_TITLE));
    }

    static String body(Notification notification) {
        if (notification == null || notification.extras == null) return "";
        Bundle extras = notification.extras;
        Set<String> parts = new LinkedHashSet<>();

        add(parts, extras.getCharSequence(Notification.EXTRA_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUB_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        add(parts, extras.getCharSequence(Notification.EXTRA_INFO_TEXT));

        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) add(parts, line);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            try {
                Parcelable[] rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (rawMessages != null) {
                    for (Parcelable raw : rawMessages) {
                        if (raw instanceof Bundle) {
                            Bundle message = (Bundle) raw;
                            add(parts, message.getCharSequence("text"));
                            add(parts, message.getCharSequence("sender"));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        StringBuilder body = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (body.length() > 0) body.append(" ");
            body.append(p);
        }
        return body.toString();
    }

    private static void add(Set<String> parts, CharSequence value) {
        String s = stringValue(value);
        if (!s.isEmpty()) parts.add(s);
    }

    private static String stringValue(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
