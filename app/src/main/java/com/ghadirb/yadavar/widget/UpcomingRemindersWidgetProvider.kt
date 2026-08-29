package com.ghadirb.yadavar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Home-screen widget listing the next 3 upcoming reminders - a "specialized feature"
 * Maliar-Pro's embedded reminder section never had room for, since it wasn't a standalone
 * app with its own launcher identity.
 */
class UpcomingRemindersWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val dao = AppDatabase.getInstance(context).reminderDao()
            val upcoming = dao.getUpcoming(
                System.currentTimeMillis(),
                System.currentTimeMillis() + TimeUnit_DAYS_IN_MILLIS
            ).first().take(3)

            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_upcoming_reminders)
                views.removeAllViews(R.id.widget_list_container)
                if (upcoming.isEmpty()) {
                    views.setTextViewText(R.id.widget_empty_text, context.getString(R.string.widget_no_upcoming))
                } else {
                    val summary = upcoming.joinToString("\n") { "${fmt.format(Date(it.triggerTime))}  ${it.title}" }
                    views.setTextViewText(R.id.widget_empty_text, summary)
                }
                manager.updateAppWidget(id, views)
            }
        }
    }

    companion object {
        private const val TimeUnit_DAYS_IN_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
