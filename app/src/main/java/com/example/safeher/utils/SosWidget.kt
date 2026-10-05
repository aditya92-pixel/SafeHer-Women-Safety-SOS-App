package com.example.safeher.utils

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.safeher.R

class SosWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TRIGGER_SOS = "com.example.safeher.ACTION_WIDGET_TRIGGER_SOS"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, SosWidget::class.java).apply {
                action = ACTION_TRIGGER_SOS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.widget_sos).apply {
                setOnClickPendingIntent(R.id.widget_sos_btn, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TRIGGER_SOS) {
            val contacts = PreferencesHelper.getContacts(context)
            LocationHelper.sendSosWithLocation(
                context,
                contacts,
                "[WIDGET SOS] Emergency trigger activated from Home Screen!"
            )
            CameraHelper.capturePhotosOnSos(context)
        }
    }
}
