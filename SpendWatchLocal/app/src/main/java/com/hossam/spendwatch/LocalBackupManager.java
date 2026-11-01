package com.hossam.spendwatch;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Offline, user-controlled backup of the entire SpendWatch SQLite database.
 *
 * The exported ZIP includes transactions, budgets, goals, recurring bills,
 * settings and merchant-learning rules. It is NOT encrypted; warn users to
 * store it privately and never send it to strangers.
 */
public final class LocalBackupManager {
    private static final String DATABASE_FILE = "spendwatch_uae.db";
    private static final String ENTRY_NAME = "databases/spendwatch_uae.db";
    private static final long MAX_DATABASE_BYTES = 80L * 1024L * 1024L;
    private LocalBackupManager() {}

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    /** Call this off the main thread. */
    public static void exportBackup(Context context, Uri destination) throws Exception {
        SpendDatabase db = SpendDatabase.getInstance(context);
        File dbFile = context.getDatabasePath(DATABASE_FILE);
        synchronized (db) {
            SQLiteDatabase readable = db.getWritableDatabase();
            // Ensure any pending WAL content is reflected in the primary DB file.
            try (Cursor cursor = readable.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    throw new IOException("Database is busy; please retry backup");
                }
            }
            db.close();
            if (!dbFile.isFile()) throw new IOException("SpendWatch database not found");
            try (OutputStream out = context.getContentResolver().openOutputStream(destination, "w")) {
                if (out == null) throw new IOException("Cannot open selected backup location");
                try (ZipOutputStream zip = new ZipOutputStream(out)) {
                    zip.putNextEntry(new ZipEntry("README.txt"));
                    zip.write(("SpendWatch offline database backup.\\n"
                            + "Contains sensitive financial records. This backup is NOT encrypted.\\n"
                            + "Do not share it or store it publicly.\\n")
                            .replace("\\n", "\n").getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                    zip.putNextEntry(new ZipEntry(ENTRY_NAME));
                    try (FileInputStream input = new FileInputStream(dbFile)) {
                        copy(input, zip);
                    }
                    zip.closeEntry();
                }
            }
        }
    }

    /** Call this off the main thread; the entire existing database is replaced only after validation. */
    public static void importBackup(Context context, Uri source) throws Exception {
        SpendDatabase db = SpendDatabase.getInstance(context);
        File candidate = new File(context.getCacheDir(), "spendwatch-import-" + System.nanoTime() + ".db");
        File candidateWal = new File(candidate.getAbsolutePath() + "-wal");
        File candidateShm = new File(candidate.getAbsolutePath() + "-shm");

        try {
            boolean found = false;
            try (InputStream stream = context.getContentResolver().openInputStream(source)) {
                if (stream == null) throw new IOException("Cannot read selected file");
                try (ZipInputStream zip = new ZipInputStream(stream)) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        File target = null;
                        if (!entry.isDirectory()) {
                            if (name.equals(ENTRY_NAME) || name.equals("database/" + DATABASE_FILE)
                                    || name.equals(DATABASE_FILE)) {
                                if (found) throw new IOException("Backup includes duplicate databases");
                                found = true;
                                target = candidate;
                            } else if (name.equals(ENTRY_NAME + "-wal")) {
                                target = candidateWal;
                            } else if (name.equals(ENTRY_NAME + "-shm")) {
                                target = candidateShm;
                            }
                        }
                        if (target != null) {
                            long bytes = 0;
                            try (FileOutputStream output = new FileOutputStream(target)) {
                                byte[] buffer = new byte[16 * 1024];
                                int read;
                                while ((read = zip.read(buffer)) != -1) {
                                    bytes += read;
                                    if (bytes > MAX_DATABASE_BYTES)
                                        throw new IOException("Backup exceeds allowed database size");
                                    output.write(buffer, 0, read);
                                }
                                output.getFD().sync();
                            }
                        }
                        zip.closeEntry();
                    }
                }
            }
            if (!found) throw new IOException("Not a SpendWatch backup: database missing");
            validateDatabase(candidate);

            synchronized (db) {
                File destination = context.getDatabasePath(DATABASE_FILE);
                File previous = new File(destination.getAbsolutePath() + ".pre-import");
                File wal = new File(destination.getAbsolutePath() + "-wal");
                File shm = new File(destination.getAbsolutePath() + "-shm");

                db.close();
                if (previous.exists() && !previous.delete())
                    throw new IOException("Cannot replace previous safety copy");

                boolean hadOriginal = destination.isFile();
                if (hadOriginal && !destination.renameTo(previous))
                    throw new IOException("Cannot protect existing database");
                boolean installed = false;
                try {
                    if (!candidate.renameTo(destination))
                        throw new IOException("Cannot install selected backup");

                    // Old sidecars refer to the previous database, not the imported one.
                    if (wal.exists() && !wal.delete())
                        throw new IOException("Cannot clear previous WAL file");
                    if (shm.exists() && !shm.delete())
                        throw new IOException("Cannot clear previous shared-memory file");

                    // This also applies any forward migrations from an older database.
                    db.getWritableDatabase();
                    installed = true;
                } finally {
                    if (!installed) {
                        db.close();
                        if (destination.exists()) destination.delete();
                        if (hadOriginal && !previous.renameTo(destination))
                            throw new IOException("Restore failed. Previous database is retained at "
                                    + previous.getAbsolutePath());
                    }
                }
            }
        } finally {
            if (candidate.exists()) candidate.delete();
            if (candidateWal.exists()) candidateWal.delete();
            if (candidateShm.exists()) candidateShm.delete();
        }
    }

    private static void validateDatabase(File file) throws IOException {
        if (!file.isFile() || file.length() < 100)
            throw new IOException("Invalid or empty database backup");
        SQLiteDatabase database = null;
        try {
            database = SQLiteDatabase.openDatabase(
                    file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);

            try (Cursor result = database.rawQuery("PRAGMA integrity_check", null)) {
                if (!result.moveToFirst() || !"ok".equalsIgnoreCase(result.getString(0)))
                    throw new IOException("Database integrity verification failed");
            }
            // Older Android debug installs can have outstanding WAL data. Opening
            // the sidecars alongside the DB replays it; the checkpoint safely folds
            // all committed transactions into the primary file before import.
            try (Cursor checkpoint = database.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                if (checkpoint.moveToFirst() && checkpoint.getInt(0) != 0)
                    throw new IOException("Backup WAL is busy");
            }
            int version = database.getVersion();
            if (version < 1 || version > 6)
                throw new IOException("Unsupported database version: " + version);
            try (Cursor table = database.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='transactions'",
                    null)) {
                if (!table.moveToFirst())
                    throw new IOException("Backup does not contain SpendWatch transactions");
            }
        } catch (RuntimeException ex) {
            throw new IOException("Invalid SpendWatch backup", ex);
        } finally {
            if (database != null) database.close();
        }
    }
}
