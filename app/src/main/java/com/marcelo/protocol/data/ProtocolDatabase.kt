package com.marcelo.protocol.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.marcelo.protocol.model.DayType
import com.marcelo.protocol.model.OfficeDayPlan
import com.marcelo.protocol.model.ParkingStatus
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
                date         TEXT NOT NULL,
                item_id      TEXT NOT NULL,
                completed_at TEXT NOT NULL,
                PRIMARY KEY (date, item_id)
            )
        """)
        db.execSQL("""
            CREATE TABLE schedule (
                day_of_week TEXT NOT NULL PRIMARY KEY,
                day_type    TEXT NOT NULL
            )
        """)
        db.execSQL("""
            CREATE TABLE week_plans (
                week_start  TEXT NOT NULL,
                day_of_week TEXT NOT NULL,
                day_type    TEXT,
                parking     TEXT NOT NULL DEFAULT 'UNPLANNED',
                PRIMARY KEY (week_start, day_of_week)
            )
        """)
        db.execSQL("""
            CREATE TABLE settings (
                key   TEXT NOT NULL PRIMARY KEY,
                value TEXT NOT NULL
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // -- Checklist --

    suspend fun completedItems(date: LocalDate): Map<String, LocalTime> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, LocalTime>()
        readableDatabase.rawQuery(
            "SELECT item_id, completed_at FROM checklist_completions WHERE date = ?",
            arrayOf(date.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result[cursor.getString(0)] = LocalTime.parse(cursor.getString(1))
            }
        }
        result
    }

    suspend fun setCompletion(date: LocalDate, itemId: String, time: LocalTime) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "checklist_completions", null,
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

    // -- Schedule --

    suspend fun getSchedule(): Map<DayOfWeek, DayType> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<DayOfWeek, DayType>()
        readableDatabase.rawQuery("SELECT day_of_week, day_type FROM schedule", null).use { cursor ->
            while (cursor.moveToNext()) {
                result[DayOfWeek.valueOf(cursor.getString(0))] = DayType.valueOf(cursor.getString(1))
            }
        }
        result
    }

    suspend fun setScheduleDay(day: DayOfWeek, type: DayType) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "schedule", null,
            ContentValues().apply {
                put("day_of_week", day.name)
                put("day_type", type.name)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // -- Week Plans --

    suspend fun getWeekPlan(weekStart: LocalDate): Map<DayOfWeek, OfficeDayPlan> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<DayOfWeek, OfficeDayPlan>()
        readableDatabase.rawQuery(
            "SELECT day_of_week, day_type, parking FROM week_plans WHERE week_start = ?",
            arrayOf(weekStart.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val day = DayOfWeek.valueOf(cursor.getString(0))
                val dayType = cursor.getString(1)?.let { DayType.valueOf(it) }
                val parking = ParkingStatus.valueOf(cursor.getString(2))
                result[day] = OfficeDayPlan(dayType, parking)
            }
        }
        result
    }

    suspend fun setWeekPlanDay(weekStart: LocalDate, day: DayOfWeek, plan: OfficeDayPlan) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "week_plans", null,
            ContentValues().apply {
                put("week_start", weekStart.toString())
                put("day_of_week", day.name)
                put("day_type", plan.dayType?.name)
                put("parking", plan.parkingStatus.name)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    // -- Settings --

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery(
            "SELECT value FROM settings WHERE key = ?",
            arrayOf(key),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            "settings", null,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }
}
