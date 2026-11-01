package com.duitku.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.duitku.app.R

/** Saluran notifikasi (wajib dibuat sebelum menampilkan notifikasi di Android 8+). */
object NotificationChannels {
    const val BUDGET = "budget"
    const val REMINDER = "reminder"
    const val DUE = "due"
    const val DATA = "data"
    const val RECURRING = "recurring"

    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(BUDGET, context.getString(R.string.channel_budget), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_budget_desc) },
            NotificationChannel(REMINDER, context.getString(R.string.channel_reminder), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_reminder_desc) },
            NotificationChannel(DUE, context.getString(R.string.channel_due), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_due_desc) },
            NotificationChannel(DATA, context.getString(R.string.channel_data), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_data_desc) },
            NotificationChannel(RECURRING, context.getString(R.string.channel_recurring), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_recurring_desc) },
        )
        manager.createNotificationChannels(channels)
    }
}
