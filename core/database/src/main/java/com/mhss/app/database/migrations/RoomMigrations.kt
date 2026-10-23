package com.mhss.app.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    query("PRAGMA table_info($table)").use { c ->
        val nameIndex = c.getColumnIndex("name")
        while (c.moveToNext()) {
            if (c.getString(nameIndex) == column) return true
        }
    }
    return false
}

// Migration 1 → 2
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE note_folders (name TEXT NOT NULL, id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS notes_new (" +
                    "title TEXT NOT NULL, content TEXT NOT NULL, created_date INTEGER NOT NULL, " +
                    "updated_date INTEGER NOT NULL, pinned INTEGER NOT NULL, folder_id INTEGER, " +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "FOREIGN KEY(folder_id) REFERENCES note_folders(id) ON DELETE CASCADE)"
        )
        db.execSQL(
            "INSERT INTO notes_new (title, content, created_date, updated_date, pinned, folder_id, id) " +
                    "SELECT title, content, created_date, updated_date, pinned, folder_id, id FROM notes"
        )
        db.execSQL("DROP TABLE notes")
        db.execSQL("ALTER TABLE notes_new RENAME TO notes")
    }
}

// Migration 2 → 3
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN recurring INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE tasks ADD COLUMN frequency INTEGER NOT NULL DEFAULT 0")
    }
}

// Migration 3 → 4
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN frequency_amount INTEGER NOT NULL DEFAULT 1")
    }
}

// Migration 4 → 5
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        var hasParentId = false
        db.query("PRAGMA table_info(note_folders)").use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (c.getString(nameIndex) == "parentId") {
                    hasParentId = true
                    break
                }
            }
        }
        if (!hasParentId) {
            db.execSQL("ALTER TABLE note_folders ADD COLUMN parentId INTEGER")
        }
    }
}

// Migration 5 → 6
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE tasks ADD COLUMN last_fired_date INTEGER NOT NULL DEFAULT 0"
        )
    }
}

// Migration 6 → 7 (idempotent: order_index kan redan finnas i nyinstallerade databaser)
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("notes", "order_index")) {
            db.execSQL(
                "ALTER TABLE notes ADD COLUMN order_index INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
}

// Migration 8 → 9: bilder i dagboksinlägg (lokala filsökvägar, en per rad)
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("diary", "images")) {
            db.execSQL(
                "ALTER TABLE diary ADD COLUMN images TEXT NOT NULL DEFAULT ''"
            )
        }
    }
}

// Migration 7 → 8: flera vård-pins per anteckning
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!db.hasColumn("notes", "pins")) {
            db.execSQL(
                "ALTER TABLE notes ADD COLUMN pins TEXT NOT NULL DEFAULT ''"
            )
        }
    }
}
