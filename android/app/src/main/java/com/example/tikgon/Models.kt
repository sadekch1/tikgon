package com.example.tikgon

/** فيديو واحد في الخلاصة — ملفه داخل wa/inbox وبياناته محفوظة في feed.json */
data class FeedVideo(
    val path: String,          // المسار الكامل للملف (مفتاح فريد)
    val name: String,
    val size: Long,
    val caption: String,
    val query: String,         // كلمة البحث كما جاءت من تعليق البوت
    val index: Int,            // رقم الفيديو داخل الجلسة عند البوت (0 إن لم يُعرف)
    val ts: Long,              // زمن رسالة الواتساب (ms)
    val sessionId: Long,       // جلسة البدء التي ينتمي إليها (0 = بلا جلسة)
    val watchedAt: Long = 0L,  // 0 = غير مشاهَد؛ وإلا زمن تجاوزه بالتمرير
    val saved: Boolean = false // محفوظ بالقلب: لا يُحذف أبداً
)

/** حالة جلسة الخلاصة الحالية (من علامات البوت النصية) */
data class SessionState(
    val id: Long = 0L,               // زمن بدء الجلسة (ms)، 0 = لا جلسة
    val query: String = "",
    val quality: String = "",
    val active: Boolean = false,     // وصلت علامة بدء ولم تصل علامة إيقاف/نهاية
    val unlocked: Boolean = false,   // فُتحت بوابة العشرة لهذه الجلسة
    val breakBaseline: Long = -1L    // إجمالي المنزّل لحظة دخول الاستراحة؛ -1 = لسنا في استراحة
)

/** محادثة في قائمة الاختيار */
data class ChatEntry(
    val jid: String,
    val name: String,
    val ts: Long,
    val preview: String
)

/** مهمة تحميل جارية/منتظرة عند المحرك (لعرض الحالة فقط) */
data class DlJob(
    val id: String,
    val name: String,
    val size: Long,
    val done: Long = 0L,
    val status: String = "queued",   // queued | active | failed | expired
    val error: String = ""
)
