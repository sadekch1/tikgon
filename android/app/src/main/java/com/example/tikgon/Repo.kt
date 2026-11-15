package com.example.tikgon

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** مصدر الحقيقة الوحيد: يستقبل أحداث Node ويحتفظ بحالة الواجهة والخلاصة */
object Repo {
    lateinit var app: Context
    private var inited = false
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var client: NodeClient? = null
    var port: Int = 0
    var token: String = ""

    // ── حالة الاتصال (كما في WA Hub) ──
    val conn = MutableStateFlow("idle")
    val code = MutableStateFlow("")
    val errorMsg = MutableStateFlow("")
    val engineUp = MutableStateFlow(false)
    val fatal = MutableStateFlow("")
    val lastCrash = MutableStateFlow("")
    val pairingActive = MutableStateFlow(false)
    val logs = MutableStateFlow<List<String>>(emptyList())

    // ── حالة TikGon ──
    val videos = MutableStateFlow<List<FeedVideo>>(emptyList())
    val session = MutableStateFlow(SessionState())
    val chats = MutableStateFlow<List<ChatEntry>>(emptyList())
    val dlJobs = MutableStateFlow<List<DlJob>>(emptyList())
    /** طلب من صفحة «أنا»: اقفز بالخلاصة إلى هذا الملف (مسار) ثم يُمسح الطلب */
    val focusPath = MutableStateFlow("")

    private var breakNotified = false

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        Prefs.init(app)
        val (list, s) = FeedStore.snapshot(app)
        videos.value = list
        session.value = s
        FeedStore.sweep(app).also { videos.value = it }
        checkLastCrash()
        watchBreakReady()
    }

    /** إشعار صامت مرة واحدة عندما تكتمل دفعة الاستراحة (10 جديدة) — بلا صوت ولا فتح تلقائي */
    private fun watchBreakReady() {
        scope.launch {
            var wasReady = false
            while (true) {
                val ready = isBreakReady()
                if (ready && !wasReady && !breakNotified) {
                    breakNotified = true
                    Notifier.batchReady(app)
                }
                if (!ready) breakNotified = false
                wasReady = ready
                kotlinx.coroutines.delay(2000)
            }
        }
    }

    fun isBreakReady(): Boolean {
        val s = session.value
        if (s.breakBaseline < 0) return false
        return videos.value.size - s.breakBaseline >= 10
    }

    fun enterBreak() {
        session.value = FeedStore.setBreak(app, videos.value.size.toLong())
    }

    fun exitBreak() {
        session.value = FeedStore.setBreak(app, -1L)
    }

    // ───────────── تشخيص أعطال المحرك (من WA Hub كما هو) ─────────────
    private fun reasonName(r: Int): String = when (r) {
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "انهيار أصلي (native crash)"
        ApplicationExitInfo.REASON_CRASH -> "انهيار"
        ApplicationExitInfo.REASON_EXIT_SELF -> "خروج ذاتي exit()"
        ApplicationExitInfo.REASON_SIGNALED -> "إشارة قتل"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "نفاد الذاكرة"
        ApplicationExitInfo.REASON_ANR -> "عدم استجابة"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "فشل التهيئة"
        else -> "reason=$r"
    }

    private fun traceStrings(e: ApplicationExitInfo): String = try {
        e.traceInputStream?.use { ins ->
            val buf = ByteArray(300_000)
            var n = 0
            while (n < buf.size) {
                val r = ins.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            val txt = String(buf, 0, n, Charsets.ISO_8859_1)
            Regex("[\\x20-\\x7E]{8,}").findAll(txt).map { it.value }
                .filter { it.contains("libnode") || it.contains("abort", true) || it.contains("Fatal", true) ||
                    it.contains("Check failed") || it.contains("v8::") || it.contains("node::") || it.contains("SIG") }
                .distinct().take(12).joinToString("\n")
        } ?: ""
    } catch (_: Throwable) { "" }

    fun describeNodeExit(): String {
        val sb = StringBuilder()
        try {
            val f = File(app.filesDir, "wa/node-start-error.txt")
            if (f.exists()) { sb.append(f.readText().take(1200)); f.delete() }
        } catch (_: Throwable) {}
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val am = app.getSystemService(ActivityManager::class.java)
                val e = am.getHistoricalProcessExitReasons(app.packageName, 0, 8)
                    .firstOrNull { it.processName?.endsWith(":node") == true }
                if (e != null) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append("${reasonName(e.reason)} • status=${e.status} ${e.description ?: ""}")
                    if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                        val t = traceStrings(e)
                        if (t.isNotEmpty()) sb.append("\n").append(t)
                    }
                }
            } catch (_: Throwable) {}
        }
        if (sb.isEmpty()) sb.append("لا توجد معلومات من النظام")
        return sb.toString()
    }

    private fun checkLastCrash() {
        try {
            val sb = StringBuilder()
            val f = File(app.filesDir, "crash.txt")
            if (f.exists()) { sb.append(f.readText().take(3000)); f.delete() }
            if (Build.VERSION.SDK_INT >= 30) {
                val am = app.getSystemService(ActivityManager::class.java)
                val e = am.getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull()
                val sp = app.getSharedPreferences("tikgon", Context.MODE_PRIVATE)
                if (e != null && e.timestamp > sp.getLong("exitSeen", 0L)) {
                    sp.edit().putLong("exitSeen", e.timestamp).apply()
                    val bad = setOf(
                        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                        ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_EXIT_SELF,
                        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE, ApplicationExitInfo.REASON_LOW_MEMORY,
                        ApplicationExitInfo.REASON_ANR
                    )
                    if (e.reason in bad) {
                        sb.append("\nسبب إنهاء العملية: reason=${e.reason} status=${e.status} ${e.description ?: ""}")
                    }
                }
            }
            lastCrash.value = sb.toString()
        } catch (_: Throwable) {}
    }

    // ───────────── الاتصال بالمحرك ─────────────
    fun startClient(p: Int, t: String) {
        if (client != null) return
        port = p; token = t
        client = NodeClient(p, t, { engineUp.value = false }) { onEvent(it) }.also { it.start() }
    }

    fun send(o: JSONObject) { client?.send(o) }

    fun stopClient() {
        try { client?.stop() } catch (_: Exception) {}
        client = null
        port = 0
        token = ""
        engineUp.value = false
        conn.value = "idle"
        code.value = ""
    }

    fun isRegistered(): Boolean {
        return try {
            val f = File(app.filesDir, "wa/auth/creds.json")
            f.exists() && Regex("\"registered\"\\s*:\\s*true").containsMatchIn(f.readText())
        } catch (_: Exception) { false }
    }

    fun pushConfig() {
        send(
            JSONObject().put("cmd", "config")
                .put("targetJid", Prefs.chatJid.value)
                .put("target", Prefs.targetDigits.value)
        )
    }

    fun requestChats() = send(JSONObject().put("cmd", "listChats"))

    fun pair(phone: String) {
        errorMsg.value = ""
        code.value = ""
        pairingActive.value = true
        send(JSONObject().put("cmd", "pair").put("phone", phone))
    }

    fun cancelPairing() {
        pairingActive.value = false
        code.value = ""
        send(JSONObject().put("cmd", "logout"))
    }

    fun logout() = send(JSONObject().put("cmd", "logout"))
    fun diag() { addLog("…بدء الفحص"); send(JSONObject().put("cmd", "diag")) }

    // ───────────── أفعال الخلاصة (من الواجهة) ─────────────
    fun markWatched(path: String) {
        videos.value = FeedStore.markWatched(app, path)
    }

    fun toggleSaved(path: String) {
        videos.value = FeedStore.toggleSaved(app, path)
    }

    private fun addLog(s: String) {
        logs.update { (it + s).takeLast(150) }
    }

    // ───────────── معالجة الأحداث ─────────────
    fun onEvent(e: JSONObject) {
        when (e.optString("t")) {
            "snapshot" -> {
                engineUp.value = true
                val f = e.optString("fatal")
                if (f.isNotEmpty()) fatal.value = f
                else if (fatal.value.startsWith("المحرك توقف")) fatal.value = ""
                conn.value = e.optString("conn")
                code.value = e.optString("code")
                val arr = e.optJSONArray("jobs") ?: JSONArray()
                dlJobs.value = (0 until arr.length()).map { parseDlJob(arr.getJSONObject(it)) }
                if (conn.value == "idle") send(JSONObject().put("cmd", "connect"))
                pushConfig()
            }
            "state" -> {
                val c = e.optString("conn")
                conn.value = c
                if (c == "open") { pairingActive.value = false; code.value = ""; errorMsg.value = "" }
                if (c == "needpair" || c == "loggedout") { pairingActive.value = false; code.value = "" }
            }
            "code" -> code.value = e.optString("code")
            "error" -> { errorMsg.value = e.optString("msg"); pairingActive.value = false }
            "log" -> addLog(e.optString("msg"))
            "queued" -> {
                val j = parseDlJob(e)
                dlJobs.update { l -> l.filter { it.id != j.id } + j }
            }
            "start" -> updateDlJob(e.optString("id")) { it.copy(status = "active", done = 0) }
            "progress" -> updateDlJob(e.optString("id")) {
                it.copy(done = e.optLong("done"), size = if (e.optLong("total") > 0) e.optLong("total") else it.size)
            }
            "fail" -> {
                if (e.optBoolean("final")) updateDlJob(e.optString("id")) {
                    it.copy(status = "failed", error = e.optString("error"))
                }
            }
            "expired" -> updateDlJob(e.optString("id")) { it.copy(status = "expired", error = e.optString("error")) }
            "canceled" -> dlJobs.update { l -> l.filter { it.id != e.optString("id") } }
            "video" -> onVideo(e)
            "session_start" -> {
                val (list, s) = FeedStore.startSession(app, e.optString("query"), e.optString("quality"))
                videos.value = list
                session.value = s
                addLog("بدأت خلاصة: ${e.optString("query")}")
            }
            "session_end" -> {
                val (_, s) = FeedStore.endSession(app)
                session.value = s
                addLog("انتهت الجلسة (${e.optString("reason")})")
            }
            "chats" -> {
                val arr = e.optJSONArray("chats") ?: JSONArray()
                chats.value = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    ChatEntry(o.optString("jid"), o.optString("name"), o.optLong("ts"), o.optString("preview"))
                }
            }
        }
    }

    private fun onVideo(e: JSONObject) {
        val path = e.optString("path")
        if (path.isEmpty()) return
        dlJobs.update { l -> l.filter { it.name != e.optString("name") } }
        val s = session.value
        val v = FeedVideo(
            path = path,
            name = e.optString("name"),
            size = e.optLong("size"),
            caption = e.optString("caption"),
            query = e.optString("query"),
            index = e.optInt("index"),
            ts = e.optLong("timestamp"),
            sessionId = if (s.active) s.id else 0L
        )
        val (list, ns) = FeedStore.addVideo(app, v)
        videos.value = list
        session.value = ns
    }

    private fun parseDlJob(o: JSONObject) = DlJob(
        id = o.optString("id"),
        name = o.optString("name"),
        size = o.optLong("size"),
        done = o.optLong("done"),
        status = o.optString("status", "queued"),
        error = o.optString("error")
    )

    private fun updateDlJob(id: String, f: (DlJob) -> DlJob) =
        dlJobs.update { l -> l.map { if (it.id == id) f(it) else it } }
}
