package com.marcelo.protocol.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

class ProtocolDatabase(context: Context) : SQLiteOpenHelper(context, "protocol.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE checklist_completions (
                date       TEXT NOT NULL,
                item_id    TEXT NOT NULL,
                completed_at TEXT NOT NULL,
                PRIMARY KEY (date, item_id)
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    suspend fun completedItems(date: LocalDate): Map<String, LocalTime> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, LocalTime>()
        readableDatabase.rawQuery(
            "SELECT item_id, completed_at FROM checklist_completions WHERE date = ?",
            arrayOf(date.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val itemId = cursor.getString(0)
                val time = LocalTime.parse(cursor.getString(1))
                result[itemId] = time
            }
        }
        result
    }

    suspend fun setCompletion(date: LocalDate, itemId: String, time: LocalTime) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "checklist_completions",
            null,
            ContentValues().apply {
                put("date", date.toString())
                put("item_id", itemId)
                put("completed_at", time.withSecond(0).withNano(0).toString())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun removeCompletion(date: LocalDate, itemId: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete(
            "checklist_completions",
            "date = ? AND item_id = ?",
            arrayOf(date.toString(), itemId),
        )
    }

    suspend fun gymCountForWeek(date: LocalDate): Int = withContext(Dispatchers.IO) {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sunday = monday.plusDays(6)
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM checklist_completions WHERE item_id = 'gym' AND date BETWEEN ? AND ?",
            arrayOf(monday.toString(), sunday.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
