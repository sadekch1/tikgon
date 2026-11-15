package com.example.tikgon

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.util.UUID

/**
 * خدمة في المقدمة: تُبقي محرك Node حياً حتى مع إغلاق الواجهة والشاشة،
 * فتستمر تحميلات الخلاصة في الخلفية (استقبال فقط — لا تشغيل تلقائي أبداً).
 * وتنفّذ أيضاً كنس الحذف المؤجّل (مشاهَد + 10 دقائق) كل 30 ثانية.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Repo.init(applicationContext)
        Notifier.ensureChannels(this)

        val n = Notifier.ongoing(this, "TikGon", "جارٍ تشغيل المحرك…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        if (Repo.port == 0) {
            try {
                val p = ServerSocket(0).use { it.localPort }
                Repo.startClient(p, UUID.randomUUID().toString())
                launchEngine()
            } catch (e: Throwable) {
                Repo.fatal.value = "تعذّر تشغيل المحرك: ${e.javaClass.simpleName}: ${e.message}"
            }
        }

        // مراقب: لو توقف المحرك نعرض السبب ونعيد تشغيله (بحد أقصى 3 مرات كل دقيقتين)
        scope.launch {
            var downSince = 0L
            val restarts = ArrayList<Long>()
            while (true) {
                delay(3000)
                if (Repo.engineUp.value) { downSince = 0L; continue }
                val now = System.currentTimeMillis()
                if (downSince == 0L) { downSince = now; continue }
                if (now - downSince < 12_000) continue
                Repo.fatal.value = "المحرك توقف — " + Repo.describeNodeExit()
                restarts.removeAll { now - it > 120_000 }
                if (restarts.size < 3) {
                    restarts.add(now)
                    launchEngine()
                    downSince = now
                } else {
                    Repo.fatal.value += "\nتوقفت المحاولات التلقائية."
                    downSince = Long.MAX_VALUE / 2
                }
            }
        }

        // كنس الحذف المؤجّل: فيديو مشاهَد + 10 دقائق يُحذف (إلا المحفوظ)
        scope.launch {
            while (true) {
                try { Repo.videos.value = FeedStore.sweep(applicationContext) } catch (_: Exception) {}
                delay(30_000)
            }
        }

        // تحديث الإشعار المستمر بحالة الاتصال والخلاصة
        scope.launch {
            combine(Repo.videos, Repo.conn) { v, c -> v to c }.conflate().collect { (vids, c) ->
                val unwatched = vids.count { it.watchedAt == 0L }
                val text = when {
                    c == "open" && Repo.session.value.active ->
                        "خلاصة «${Repo.session.value.query}» — ${vids.size} فيديو • $unwatched غير مشاهَد"
                    c == "open" -> "متصل — بانتظار خلاصة جديدة من واتساب"
                    c == "reconnecting" || c == "connecting" -> "جارٍ الاتصال…"
                    c == "needpair" || c == "loggedout" -> "سجّل الدخول من التطبيق"
                    else -> "جارٍ التشغيل…"
                }
                Notifier.updateOngoing(this@DownloadService, "TikGon", text)
                delay(1500)
            }
        }
    }

    private fun launchEngine() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, NodeService::class.java).putExtra("port", Repo.port).putExtra("token", Repo.token)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { stopService(Intent(this, NodeService::class.java)) } catch (_: Exception) {}
        Repo.stopClient()
        scope.cancel()
        super.onDestroy()
    }
}
