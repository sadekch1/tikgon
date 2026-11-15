package com.example.tikgon

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * حفظ حالة الخلاصة في feed.json (بجانب مجلد wa):
 *  - قائمة الفيديوهات وبياناتها (مشاهَد؟ محفوظ؟ لأي جلسة؟)
 *  - حالة الجلسة الحالية وبوابة العشرة ووضع الاستراحة
 * الحذف بعد 10 دقائق من المشاهدة يعتمد على watchedAt المحفوظ هنا،
 * وينفّذه sweep() عند كل بدء للتطبيق ودورياً من خدمة المحرك —
 * فيصمد الجدول أمام إعادة تشغيل التطبيق أو الجهاز.
 */
object FeedStore {
    const val DELETE_AFTER_MS = 10L * 60 * 1000

    private var loaded = false
    private val items = ArrayList<FeedVideo>()
    private var session = SessionState()

    private fun storeFile(ctx: Context) = File(ctx.filesDir, "feed.json")
    fun inboxDir(ctx: Context) = File(ctx.filesDir, "wa/inbox")

    @Synchronized
    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = storeFile(ctx)
            if (f.exists()) {
                val root = JSONObject(f.readText())
                val s = root.optJSONObject("session")
                if (s != null) {
                    session = SessionState(
                        id = s.optLong("id"),
                        query = s.optString("query"),
                        quality = s.optString("quality"),
                        active = s.optBoolean("active"),
                        unlocked = s.optBoolean("unlocked"),
                        breakBaseline = if (s.has("breakBaseline")) s.optLong("breakBaseline") else -1L
                    )
                }
                val arr = root.optJSONArray("items") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    items.add(
                        FeedVideo(
                            path = o.getString("path"),
                            name = o.optString("name"),
                            size = o.optLong("size"),
                            caption = o.optString("caption"),
                            query = o.optString("query"),
                            index = o.optInt("index"),
                            ts = o.optLong("ts"),
                            sessionId = o.optLong("sessionId"),
                            watchedAt = o.optLong("watchedAt"),
                            saved = o.optBoolean("saved")
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        reconcile(ctx)
    }

    /** مطابقة السجل مع القرص: إسقاط ما حُذف، وتبنّي ملفات inbox المجهولة (نُزّلت بينما العملية ميتة) */
    @Synchronized
    private fun reconcile(ctx: Context) {
        items.removeAll { !File(it.path).exists() }
        val known = items.map { it.path }.toHashSet()
        val dir = inboxDir(ctx)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".mp4") } ?: emptyArray()
        for (f in files.sortedBy { it.name }) {
            if (f.absolutePath in known) continue
            val tsFromName = f.name.substringBefore('_').toLongOrNull() ?: f.lastModified()
            items.add(
                FeedVideo(
                    path = f.absolutePath, name = f.name, size = f.length(),
                    caption = "", query = "", index = 0,
                    ts = tsFromName,
                    sessionId = if (session.active && tsFromName >= session.id) session.id else 0L
                )
            )
        }
        items.sortWith(compareBy({ it.ts }, { it.index }))
    }

    @Synchronized
    private fun persist(ctx: Context) {
        try {
            val arr = JSONArray()
            for (v in items) {
                arr.put(
                    JSONObject()
                        .put("path", v.path).put("name", v.name).put("size", v.size)
                        .put("caption", v.caption).put("query", v.query).put("index", v.index)
                        .put("ts", v.ts).put("sessionId", v.sessionId)
                        .put("watchedAt", v.watchedAt).put("saved", v.saved)
                )
            }
            val s = JSONObject()
                .put("id", session.id).put("query", session.query).put("quality", session.quality)
                .put("active", session.active).put("unlocked", session.unlocked)
                .put("breakBaseline", session.breakBaseline)
            storeFile(ctx).writeText(JSONObject().put("session", s).put("items", arr).toString())
        } catch (_: Exception) {}
    }

    @Synchronized
    fun snapshot(ctx: Context): Pair<List<FeedVideo>, SessionState> {
        load(ctx)
        return items.toList() to session
    }

    @Synchronized
    fun addVideo(ctx: Context, v: FeedVideo): Pair<List<FeedVideo>, SessionState> {
        load(ctx)
        if (items.none { it.path == v.path }) {
            items.add(v)
            items.sortWith(compareBy({ it.ts }, { it.index }))
        }
        // بوابة العشرة: تُفتح لحظة اكتمال 10 فيديوهات من الجلسة الحالية وتبقى مفتوحة
        if (session.active && !session.unlocked && session.id != 0L &&
            items.count { it.sessionId == session.id } >= 10
        ) {
            session = session.copy(unlocked = true)
        }
        persist(ctx)
        return items.toList() to session
    }

    @Synchronized
    fun startSession(ctx: Context, query: String, quality: String): Pair<List<FeedVideo>, SessionState> {
        load(ctx)
        session = SessionState(
            id = System.currentTimeMillis(), query = query, quality = quality,
            active = true, unlocked = false, breakBaseline = -1L
        )
        persist(ctx)
        return items.toList() to session
    }

    @Synchronized
    fun endSession(ctx: Context): Pair<List<FeedVideo>, SessionState> {
        load(ctx)
        if (session.active) {
            session = session.copy(active = false)
            persist(ctx)
        }
        return items.toList() to session
    }

    @Synchronized
    fun markWatched(ctx: Context, path: String): List<FeedVideo> {
        load(ctx)
        val i = items.indexOfFirst { it.path == path }
        if (i >= 0 && items[i].watchedAt == 0L) {
            items[i] = items[i].copy(watchedAt = System.currentTimeMillis())
            persist(ctx)
        }
        return items.toList()
    }

    @Synchronized
    fun toggleSaved(ctx: Context, path: String): List<FeedVideo> {
        load(ctx)
        val i = items.indexOfFirst { it.path == path }
        if (i >= 0) {
            items[i] = items[i].copy(saved = !items[i].saved)
            persist(ctx)
        }
        return items.toList()
    }

    @Synchronized
    fun setBreak(ctx: Context, baseline: Long): SessionState {
        load(ctx)
        session = session.copy(breakBaseline = baseline)
        persist(ctx)
        return session
    }

    /** حذف كل فيديو مشاهَد مرّت عليه 10 دقائق (عدا المحفوظ بالقلب). يُستدعى عند البدء ودورياً. */
    @Synchronized
    fun sweep(ctx: Context): List<FeedVideo> {
        load(ctx)
        val now = System.currentTimeMillis()
        val dead = items.filter { it.watchedAt > 0 && !it.saved && now - it.watchedAt >= DELETE_AFTER_MS }
        if (dead.isNotEmpty()) {
            dead.forEach { try { File(it.path).delete() } catch (_: Exception) {} }
            items.removeAll(dead.toSet())
            persist(ctx)
        }
        return items.toList()
    }
}
