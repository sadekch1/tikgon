package com.example.tikgon

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    private lateinit var sp: SharedPreferences

    /** سمة الواجهة: فهرس في themeDefs (ui/Theme.kt) — تُطبَّق على كل التطبيق فوراً */
    val theme = MutableStateFlow(0)
    val chatJid = MutableStateFlow("")         // المحادثة المراقبة (JID كامل)
    val chatName = MutableStateFlow("")
    val targetDigits = MutableStateFlow("")    // بديل يدوي: أرقام رقم البوت
    // وضع العرض — التدوير يدوي فقط بزر 🔄 (لا تدوير تلقائي إطلاقاً):
    // false = عمودي، true = أفقي. يُضبط مرة ويبقى محفوظاً ويُطبَّق على كل فيديو وجلسة.
    val dispRotate = MutableStateFlow(false)
    // مكان المستخدم في الخلاصة — يُحفظ باستمرار حتى لا يضيع عند التنقّل أو إعادة التشغيل
    val feedLastPath = MutableStateFlow("")    // مسار الفيديو الحالي
    val feedLastPos = MutableStateFlow(0L)     // موضع التشغيل فيه (ms)

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("tikgon", Context.MODE_PRIVATE)
        // ترحيل سمة v1 (٠ داكن / ١ أمولد / ٢ فاتح / ٣ أزرق ليلي) إلى فهارس v2.
        // المفاتيح القديمة الأخرى (accent/autoRotate/dispFullscreen) لم تعد تُقرأ —
        // بقاؤها في التخزين غير ضارّ ولا يسبّب أي انهيار.
        if (!sp.getBoolean("themeV2", false)) {
            val mapped = when (sp.getInt("theme", 1)) {
                2 -> 4      // فاتح ← نهاري
                3 -> 2      // أزرق ليلي ← محيط
                else -> 0   // داكن/أمولد ← منتصف الليل
            }
            theme.value = mapped
            sp.edit().putInt("theme", mapped).putBoolean("themeV2", true).apply()
        } else {
            theme.value = sp.getInt("theme", 0)
        }
        chatJid.value = sp.getString("chatJid", "") ?: ""
        chatName.value = sp.getString("chatName", "") ?: ""
        targetDigits.value = sp.getString("targetDigits", "") ?: ""
        dispRotate.value = sp.getBoolean("dispRotate", false)
        feedLastPath.value = sp.getString("feedLastPath", "") ?: ""
        feedLastPos.value = sp.getLong("feedLastPos", 0L)
    }

    fun <T : Any> set(flow: MutableStateFlow<T>, key: String, v: T) {
        flow.value = v
        val e = sp.edit()
        when (v) {
            is String -> e.putString(key, v)
            is Int -> e.putInt(key, v)
            is Long -> e.putLong(key, v)
            is Boolean -> e.putBoolean(key, v)
        }
        e.apply()
        if (key == "chatJid" || key == "targetDigits") Repo.pushConfig()
    }

    /** حفظ مكان الخلاصة (الفيديو الحالي + موضع التشغيل) — يُستدعى عند كل تغيّر مهم */
    fun saveFeedPlace(path: String, posMs: Long) {
        if (path.isEmpty()) return
        set(feedLastPath, "feedLastPath", path)
        set(feedLastPos, "feedLastPos", posMs.coerceAtLeast(0L))
    }
}
