package com.example.tikgon

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CH_ONGOING = "ongoing"
    private const val CH_EVENTS = "events"
    private const val CH_READY = "ready"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_ONGOING, "حالة التحميل", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "التنبيهات", NotificationManager.IMPORTANCE_DEFAULT))
            // قناة صامتة تماماً: لا صوت ولا اهتزاز (إشعار «الدفعة جاهزة» أثناء الاستراحة)
            val ready = NotificationChannel(CH_READY, "جاهزية الدفعات", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ready)
        }
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun ongoing(ctx: Context, title: String, text: String): Notification =
        NotificationCompat.Builder(ctx, CH_ONGOING)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()

    fun updateOngoing(ctx: Context, title: String, text: String) {
        if (!canNotify(ctx)) return
        NotificationManagerCompat.from(ctx).notify(1, ongoing(ctx, title, text))
    }

    fun event(ctx: Context, id: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_EVENTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    /** إشعار صامت: يظهر في درج الإشعارات فقط — لا يوقظ التطبيق ولا يشغّل شيئاً */
    fun batchReady(ctx: Context) {
        if (!canNotify(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_READY)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("TikGon")
            .setContentText("✅ الدفعة جاهزة — 10 فيديوهات جديدة بانتظارك")
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        NotificationManagerCompat.from(ctx).notify(7, n)
    }

    private fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
