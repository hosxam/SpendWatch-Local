# SpendWatch Local (Android)

SpendWatch Local is a 100% on-device Android spending tracker. It listens for incoming bank and card transaction notifications, extracts outgoing expenses, saves them to a local SQLite database, and computes daily, weekly, and monthly totals.

## Strict Local Privacy
- **Zero INTERNET Permission**: SpendWatch does not request `android.permission.INTERNET` in its `AndroidManifest.xml`.
- **Zero Cloud / Telemetry**: No servers, no tracking, no third-party analytics.
- **On-Device Only**: All transaction extraction and computation runs locally using regex and SQLite.

## Architecture
- `BankNotificationListener.java`: Extends Android's `NotificationListenerService` to intercept notifications locally.
- `BankTransactionParser.java`: Parses bank titles and messages for outgoing purchase/debit amounts, currencies, and merchants. Rejects OTPs, credits, deposits, and non-expense notifications.
- `SpendDatabase.java`: Local SQLite database storing transactions and computing sum totals.
- `MainActivity.java`: UI showing real-time spending summaries, recent transactions, and notification listener permissions status.

## Building with Gradle
```bash
./gradlew assembleDebug
```
The generated APK will be in `app/build/outputs/apk/debug/app-debug.apk`.
