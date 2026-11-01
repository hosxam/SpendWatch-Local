# SpendWatch release signing and data preservation

## Non-negotiable invariant
Every **user-distributed** APK must use the SAME Android package name,
`com.hossam.spendwatch`, and the SAME permanently held signing certificate.
Each new release increments the Android `versionCode`.

**Never distribute `assembleDebug` output to users.** GitHub-hosted runners
generate ephemeral debug signing keys, so successive APKs are incompatible.

## One-time owner setup
The project owner has a privately generated `SpendWatch_Permanent_Signing_Kit_PRIVATE.zip`.
Keep it secure offline. It contains the encrypted PKCS12 keystore and the
single-line value for GitHub's `SPENDWATCH_SIGNING_BUNDLE` repository secret.

1. Set `SPENDWATCH_SIGNING_BUNDLE` in GitHub repository Settings → Secrets
   and variables → Actions.
2. Run workflow **Build SpendWatch Updateable APK** on `main`.
3. Distribute ONLY its `SpendWatch-UPDATEABLE-APK` artifact.
4. Confirm the signing certificate SHA-256 is
   `176b883f7dec3cd16dc94c840de4031a168b5345be3e33a482a7e969333566d8`.
5. Never rotate the signing key for ordinary app updates. Do not put it in source
   control, commit it to a PR, or print secrets into CI logs.

The release workflow fails closed when the secret is unavailable or the signing
certificate differs from the expected identity. PR debug builds are explicitly
labeled as **NOT UPDATEABLE**.

## Existing users on older debug APKs
Android blocks installing a newly signed release over an APK with a different
certificate. The first transition may therefore require a **one-time migration**.

**Do not uninstall a current app containing data until a backup is verified.**
Previous debug builds can be backed up with USB debugging and Android `adb run-as`.
The `SpendWatch_One_Time_Migration_Kit.zip` provides a Windows backup script
that exports the existing app-private `databases/` directory as a ZIP.
Users can then install the permanent release and use Settings → Restore backup.
This process is a one-time exception; subsequent releases update in-place.

## Local backup and restore
From v4.4, Settings → Export full backup saves a ZIP with the SQLite
`spendwatch_uae.db` database. Settings → Restore backup accepts the ZIP,
validates SQLite integrity and version, and replaces the old database only
after verification, keeping a local pre-import fallback file.

The database contains transaction history, budgets, goals, recurring bills,
merchant-learning preferences, and income settings. **Backups are not encrypted**
and must be stored privately. No internet permission is required.

## Quality assurance
PR builds must compile and pass Android emulator tests, including the
`LocalBackupInstrumentedTest` roundtrip and corrupt-file protection tests.
The temporary key in the PR CI build test is disposable and NEVER uploaded as
a user release artifact. Only the pinned permanent signing identity is valid
for user-distributed updates.
