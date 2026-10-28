// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.nag

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marcelo.protocol.MainActivity
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.data.NagResolution
import com.marcelo.protocol.data.NagState
import com.marcelo.protocol.data.ScheduleRepository
import com.marcelo.protocol.model.ChecklistItem
import com.marcelo.protocol.model.NagLevel
import com.marcelo.protocol.model.checklistFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Escalating, persistent reminders for checklist items whose [NagLevel] is not
 * NONE.
 *
 * Lifecycle per (date, item):
 *  1. An exact alarm fires at the item's due time ([ACTION_FIRE]).
 *  2. If the item isn't done/skipped, an ongoing notification is posted with
 *     Done / Snooze / Skip actions, and the next re-alert is scheduled per
 *     [NagPolicy.realertDelay].
 *  3. Swiping the notification away (possible on Android 14+, even for ongoing
 *     notifications) silently re-posts it — dismissing is not acknowledging.
 *  4. Done (from notification, full-screen alert, or the app), Skip, or the
 *     [NagPolicy.GIVE_UP_AFTER] cutoff resolves the nag.
 *
 * All state lives in the `nag_state` table so it survives process death and
 * reboots; [rescheduleAll] rebuilds alarms from it.
 */
object NagEngine {

    const val CHANNEL_NAG = "nag"
    const val CHANNEL_NAG_ALARM = "nag_alarm"

    const val ACTION_FIRE = "com.marcelo.protocol.nag.FIRE"
    const val ACTION_DONE = "com.marcelo.protocol.nag.DONE"
    const val ACTION_SNOOZE = "com.marcelo.protocol.nag.SNOOZE"
    const val ACTION_SKIP = "com.marcelo.protocol.nag.SKIP"
    const val ACTION_DISMISSED = "com.marcelo.protocol.nag.DISMISSED"
    const val ACTION_REFRESH = "com.marcelo.protocol.nag.REFRESH"

    const val EXTRA_DATE = "date"
    const val EXTRA_ITEM = "item"

    private val mutex = Mutex()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NAG, "Nagging reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders that keep re-alerting until you act on them"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_NAG_ALARM, "Alarm reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Important items: alarm sound and full-screen alert"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setBypassDnd(true) // Only honoured if the user grants DND access.
            }
        )
    }

    // -- Entry points ---------------------------------------------------------

    /** Rebuild every alarm from persisted state. Idempotent; call freely. */
    suspend fun rescheduleAll(context: Context) = mutex.withLock {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        // Yesterday covers late items (e.g. 23:00 bed) still nagging after midnight.
        for (date in listOf(today.minusDays(1), today, today.plusDays(1))) {
            for (item in nagItems(context, date)) {
                syncOne(context, date, item, now)
            }
        }
        scheduleMidnightRefresh(context)
    }

    suspend fun fire(context: Context, date: LocalDate, itemId: String) = mutex.withLock {
        val item = nagItems(context, date).find { it.id == itemId } ?: return@withLock
        val db = (context.applicationContext as ProtocolApp).db
        val now = LocalDateTime.now()
        var state = db.getNagState(date, itemId)
        if (state.resolved != null) return@withLock
        if (itemId in db.completedItems(date)) {
            resolve(context, state, NagResolution.DONE); return@withLock
        }
        if (now.isAfter(dueAt(date, item).plus(NagPolicy.GIVE_UP_AFTER))) {
            resolve(context, state, NagResolution.GAVE_UP); return@withLock
        }
        state = state.copy(
            fireCount = state.fireCount + 1,
            nextFireAt = now.plus(NagPolicy.realertDelay(state.fireCount + 1)),
        )
        db.putNagState(state)
        post(context, item, state, alert = true)
        setAlarm(context, date, item, state.nextFireAt!!)
    }

    suspend fun done(context: Context, date: LocalDate, itemId: String) = mutex.withLock {
        val db = (context.applicationContext as ProtocolApp).db
        if (itemId !in db.completedItems(date)) db.setCompletion(date, itemId, LocalDateTime.now())
        resolve(context, db.getNagState(date, itemId), NagResolution.DONE)
    }

    suspend fun skip(context: Context, date: LocalDate, itemId: String) = mutex.withLock {
        val db = (context.applicationContext as ProtocolApp).db
        resolve(context, db.getNagState(date, itemId), NagResolution.SKIPPED)
    }

    suspend fun snooze(context: Context, date: LocalDate, itemId: String) = mutex.withLock {
        val item = nagItems(context, date).find { it.id == itemId } ?: return@withLock
        val db = (context.applicationContext as ProtocolApp).db
        val state = db.getNagState(date, itemId)
        if (state.resolved != null) return@withLock
        if (state.snoozeCount >= NagPolicy.MAX_SNOOZES) {
            // Cap reached: refuse, re-post so the user sees there's no snooze left.
            post(context, item, state, alert = false); return@withLock
        }
        val next = state.copy(
            snoozeCount = state.snoozeCount + 1,
            nextFireAt = LocalDateTime.now().plusMinutes(NagPolicy.SNOOZE_MINUTES),
        )
        db.putNagState(next)
        NotificationManagerCompat.from(context).cancel(notifId(date, itemId))
        setAlarm(context, date, item, next.nextFireAt!!)
    }

    /** The user swiped the notification away. That isn't an acknowledgement. */
    suspend fun dismissed(context: Context, date: LocalDate, itemId: String) = mutex.withLock {
        val item = nagItems(context, date).find { it.id == itemId } ?: return@withLock
        val db = (context.applicationContext as ProtocolApp).db
        val state = db.getNagState(date, itemId)
        if (state.resolved == null && itemId !in db.completedItems(date)) {
            post(context, item, state, alert = false)
        }
    }

    /** Called when the user ticks/unticks an item in the app. */
    suspend fun onCompletionChanged(context: Context, date: LocalDate, itemId: String, completed: Boolean) {
        val db = (context.applicationContext as ProtocolApp).db
        mutex.withLock {
            val state = db.getNagState(date, itemId)
            if (completed) {
                resolve(context, state, NagResolution.DONE)
            } else if (state.resolved == NagResolution.DONE) {
                db.putNagState(state.copy(resolved = null, nextFireAt = null))
            }
        }
        if (!completed) rescheduleAll(context)
    }

    // -- Internals ------------------------------------------------------------

    private suspend fun nagItems(context: Context, date: LocalDate): List<ChecklistItem> {
        val db = (context.applicationContext as ProtocolApp).db
        val type = ScheduleRepository(db).dayTypeFor(date).first()
        return checklistFor(type).filter { it.nag != NagLevel.NONE && it.dueTime != null }
    }

    private fun dueAt(date: LocalDate, item: ChecklistItem): LocalDateTime = date.atTime(item.dueTime!!)

    private suspend fun syncOne(context: Context, date: LocalDate, item: ChecklistItem, now: LocalDateTime) {
        val db = (context.applicationContext as ProtocolApp).db
        val state = db.getNagState(date, item.id)
        if (state.resolved != null) {
            cancelAlarm(context, date, item.id); return
        }
        if (item.id in db.completedItems(date)) {
            resolve(context, state, NagResolution.DONE); return
        }
        val due = dueAt(date, item)
        if (now.isAfter(due.plus(NagPolicy.GIVE_UP_AFTER))) {
            // Never nag about something long gone (e.g. after a long power-off).
            if (state.fireCount > 0) resolve(context, state, NagResolution.GAVE_UP)
            return
        }
        val at = state.nextFireAt ?: due
        setAlarm(context, date, item, if (at.isBefore(now)) now.plusSeconds(5) else at)
    }

    private suspend fun resolve(context: Context, state: NagState, how: NagResolution) {
        val db = (context.applicationContext as ProtocolApp).db
        db.putNagState(state.copy(resolved = how, nextFireAt = null))
        cancelAlarm(context, state.date, state.itemId)
        NotificationManagerCompat.from(context).cancel(notifId(state.date, state.itemId))
    }

    fun notifId(date: LocalDate, itemId: String): Int = "nag/$date/$itemId".hashCode()

    private fun nagIntent(context: Context, action: String, date: LocalDate, itemId: String) =
        Intent(context, NagReceiver::class.java).apply {
            this.action = action
            // Unique data URI => distinct PendingIntents per (action, date, item).
            data = Uri.parse("protocol-nag://$action/$date/$itemId")
            putExtra(EXTRA_DATE, date.toString())
            putExtra(EXTRA_ITEM, itemId)
        }

    private fun broadcast(context: Context, action: String, date: LocalDate, itemId: String) =
        PendingIntent.getBroadcast(
            context, 0, nagIntent(context, action, date, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setAlarm(context: Context, date: LocalDate, item: ChecklistItem, at: LocalDateTime) {
        val am = context.getSystemService(AlarmManager::class.java)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = broadcast(context, ACTION_FIRE, date, item.id)
        try {
            when {
                !canScheduleExact(context) ->
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                // Alarm-clock alarms are exempt from Doze and its rate limits.
                item.nag == NagLevel.ALARM ->
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, openApp(context)), pi)
                // In deep Doze these may be throttled to ~1 per 9 min; acceptable for nags.
                else -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancelAlarm(context: Context, date: LocalDate, itemId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(broadcast(context, ACTION_FIRE, date, itemId))
    }

    private fun scheduleMidnightRefresh(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = LocalDate.now().plusDays(1).atTime(0, 1)
        val intent = Intent(context, NagReceiver::class.java).setAction(ACTION_REFRESH)
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    private fun post(context: Context, item: ChecklistItem, state: NagState, alert: Boolean) {
        val date = state.date
        val due = dueAt(date, item)
        val overdueMin = Duration.between(due, LocalDateTime.now()).toMinutes().coerceAtLeast(0)
        val snoozesLeft = NagPolicy.MAX_SNOOZES - state.snoozeCount
        val isAlarm = item.nag == NagLevel.ALARM
        val text = buildString {
            append("Due ${item.timeHint}")
            if (overdueMin > 0) append(" · overdue ${overdueMin} min")
            if (state.fireCount > 1) append(" · reminder #${state.fireCount}")
        }

        val builder = NotificationCompat.Builder(context, if (isAlarm) CHANNEL_NAG_ALARM else CHANNEL_NAG)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(item.label.replace(Regex("\\s{2,}"), " ").trim())
            .setContentText(text)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(openApp(context))
            .setDeleteIntent(broadcast(context, ACTION_DISMISSED, date, item.id))
            .addAction(0, "Done", broadcast(context, ACTION_DONE, date, item.id))
            .apply {
                if (snoozesLeft > 0) {
                    addAction(0, "Snooze ${NagPolicy.SNOOZE_MINUTES}m ($snoozesLeft left)",
                        broadcast(context, ACTION_SNOOZE, date, item.id))
                }
            }
            .addAction(0, "Skip today", broadcast(context, ACTION_SKIP, date, item.id))

        if (isAlarm && alert) {
            val fs = Intent(context, NagAlarmActivity::class.java).apply {
                data = Uri.parse("protocol-nag://alarm/$date/${item.id}")
                putExtra(EXTRA_DATE, date.toString())
                putExtra(EXTRA_ITEM, item.id)
                putExtra(NagAlarmActivity.EXTRA_TITLE, item.label.trim())
                putExtra(NagAlarmActivity.EXTRA_SUBTITLE, text)
                putExtra(NagAlarmActivity.EXTRA_CAN_SNOOZE, snoozesLeft > 0)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            builder.setFullScreenIntent(
                PendingIntent.getActivity(context, 0, fs, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                true,
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(notifId(date, item.id), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted; the in-app banner surfaces this.
        }
    }
}
