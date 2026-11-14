package com.whats.krdrx

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class ArchivedNotification(
    val id: Long,
    val sender: String,
    val message: String,
    val receivedAt: Long
)

class NotificationArchiveDb(context: Context) : SQLiteOpenHelper(context, "whats_krdrx_archive.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE archive (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender TEXT NOT NULL,
            message TEXT NOT NULL,
            received_at INTEGER NOT NULL,
            fingerprint TEXT NOT NULL UNIQUE,
            package_name TEXT NOT NULL DEFAULT ''
        )""")
        db.execSQL("CREATE INDEX idx_archive_recent ON archive(sender,message,package_name,received_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE archive ADD COLUMN package_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_archive_recent ON archive(sender,message,package_name,received_at)")
        }
    }

    fun add(sender: String, message: String, time: Long, packageName: String) {
        if (message.isBlank()) return
        val safeSender = sender.take(250)
        val safeMessage = message.take(4000)
        val db = writableDatabase
        db.beginTransaction()
        try {
            // WhatsApp can repost/update the same notification several times.
            // Suppress identical sender + body events arriving within 60 seconds.
            val duplicate = db.rawQuery(
                """SELECT 1 FROM archive
                   WHERE sender = ? AND message = ? AND package_name = ?
                     AND received_at BETWEEN ? AND ?
                   LIMIT 1""",
                arrayOf(safeSender, safeMessage, packageName, (time - 60_000L).toString(), (time + 2_000L).toString())
            ).use { it.moveToFirst() }
            if (!duplicate) {
                val fingerprint = "$packageName|$time|$safeSender|$safeMessage".hashCode().toString()
                db.execSQL(
                    "INSERT OR IGNORE INTO archive(sender,message,received_at,fingerprint,package_name) VALUES(?,?,?,?,?)",
                    arrayOf(safeSender, safeMessage, time, fingerprint, packageName)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun all(): List<ArchivedNotification> {
        val rows = mutableListOf<ArchivedNotification>()
        readableDatabase.rawQuery(
            "SELECT id,sender,message,received_at FROM archive ORDER BY received_at DESC LIMIT 500", null
        ).use { c ->
            while (c.moveToNext()) rows += ArchivedNotification(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return rows
    }

    fun clearAll() { writableDatabase.delete("archive", null, null) }
}

class WhatsAppNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.whatsapp" && sbn.packageName != "com.whatsapp.w4b") return
        val extras = sbn.notification?.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: "WhatsApp"
        val message = sequenceOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        ).filterNotNull().firstOrNull { it.isNotBlank() } ?: return
        runCatching {
            NotificationArchiveDb(applicationContext).use { it.add(sender, message, sbn.postTime, sbn.packageName) }
        }
    }
}

fun isNotificationAccessEnabled(context: Context): Boolean {
    val expected = ComponentName(context, WhatsAppNotificationListener::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
}
