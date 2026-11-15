package com.example.tikgon

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    private lateinit var sp: SharedPreferences

    val theme = MutableStateFlow(1)            // أمولد أسود (يناسب واجهة TikTok)
    val accent = MutableStateFlow(5)           // وردي TikTok
    val chatJid = MutableStateFlow("")         // المحادثة المراقبة (JID كامل)
    val chatName = MutableStateFlow("")
    val targetDigits = MutableStateFlow("")    // بديل يدوي: أرقام رقم البوت
    // وضع العرض — يُضبط مرة ويبقى محفوظاً ويُطبَّق على كل فيديو وجلسة
    val dispFullscreen = MutableStateFlow(false)  // ملء شاشة غامر (إخفاء الأشرطة والواجهة)
    val dispRotate = MutableStateFlow(false)      // قفل التدوير الأفقي يدوياً
    val autoRotate = MutableStateFlow(true)       // تدوير تلقائي للفيديوهات الأفقية فقط

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("tikgon", Context.MODE_PRIVATE)
        theme.value = sp.getInt("theme", 1)
        accent.value = sp.getInt("accent", 5)
        chatJid.value = sp.getString("chatJid", "") ?: ""
        chatName.value = sp.getString("chatName", "") ?: ""
        targetDigits.value = sp.getString("targetDigits", "") ?: ""
        dispFullscreen.value = sp.getBoolean("dispFullscreen", false)
        dispRotate.value = sp.getBoolean("dispRotate", false)
        autoRotate.value = sp.getBoolean("autoRotate", true)
    }

    fun <T : Any> set(flow: MutableStateFlow<T>, key: String, v: T) {
        flow.value = v
        val e = sp.edit()
        when (v) {
            is String -> e.putString(key, v)
            is Int -> e.putInt(key, v)
            is Boolean -> e.putBoolean(key, v)
        }
        e.apply()
        if (key == "chatJid" || key == "targetDigits") Repo.pushConfig()
    }
}
