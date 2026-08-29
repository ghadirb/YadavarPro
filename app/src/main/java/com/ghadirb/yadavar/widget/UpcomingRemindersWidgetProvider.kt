package com.ghadirb.yadavar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.database.AppDatabase
import com.ghadirb.yadavar.ui.MainActivity
import com.ghadirb.yadavar.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UpcomingRemindersWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val dao = AppDatabase.getInstance(context).reminderDao()
            val upcoming = dao.getUpcoming(
                System.currentTimeMillis(),
                System.currentTimeMillis() + WEEK_MS
            ).first().take(3)

            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            val prefs = PreferencesManager(context)
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_upcoming_reminders)
                views.removeAllViews(R.id.widget_list_container)
                if (upcoming.isEmpty()) {
                    views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_no_upcoming))
                } else {
                    val summary = upcoming.joinToString("\n") { "${fmt.format(Date(it.triggerTime))}  ${it.title}" }
                    views.setTextViewText(R.id.widget_empty_text, summary)
                }
                val addIntent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK_ADD, true)
                views.setOnClickPendingIntent(
                    R.id.widget_add,
                    PendingIntent.getActivity(context, 11, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                val focusLabel = if (prefs.focusModeEnabled) context.getString(R.string.widget_focus_on)
                else context.getString(R.string.widget_focus_off)
                views.setTextViewText(R.id.widget_focus, focusLabel)
                val focusIntent = Intent(context, UpcomingRemindersWidgetProvider::class.java).setAction(ACTION_TOGGLE_FOCUS)
                views.setOnClickPendingIntent(
                    R.id.widget_focus,
                    PendingIntent.getBroadcast(context, 12, focusIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                val open = PendingIntent.getActivity(
                    context, 10, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_title, open)
                manager.updateAppWidget(id, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_FOCUS) {
            val prefs = PreferencesManager(context)
            prefs.focusModeEnabled = !prefs.focusModeEnabled
            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                android.content.ComponentName(context, UpcomingRemindersWidgetProvider::class.java)
            )
            onUpdate(context, AppWidgetManager.getInstance(context), ids)
        }
    }

    companion object {
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        const val ACTION_TOGGLE_FOCUS = "com.ghadirb.yadavar.widget.TOGGLE_FOCUS"
    }
}
