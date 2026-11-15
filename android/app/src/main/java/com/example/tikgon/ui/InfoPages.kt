package com.example.tikgon.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tikgon.Prefs
import com.example.tikgon.Repo

/** «اكتشف»: حالة الجلسة الحالية وشرح طريقة العمل */
@Composable
fun DiscoverPage() {
    val session by Repo.session.collectAsState()
    val videos by Repo.videos.collectAsState()
    val conn by Repo.conn.collectAsState()
    val chatName by Prefs.chatName.collectAsState()
    val inSession = if (session.id != 0L) videos.count { it.sessionId == session.id } else 0
    val unwatched = videos.count { it.watchedAt == 0L }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("اكتشف", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(14.dp))

        AppCard {
            SectionTitle("الجلسة الحالية")
            Spacer(Modifier.height(8.dp))
            if (session.id == 0L) {
                SubText("لا توجد جلسة بعد. ابدأ من واتساب بأمر .redgif")
            } else {
                SubText("البحث: ${session.query}")
                SubText("الجودة: ${session.quality.ifEmpty { "—" }}")
                SubText(
                    "الحالة: " + when {
                        session.breakBaseline >= 0 -> "🌙 في استراحة (تُجهَّز دفعة جديدة)"
                        session.active && !session.unlocked -> "⏳ تُجهَّز ($inSession من 10)"
                        session.active -> "▶ جارية"
                        else -> "🏁 منتهية"
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            RowSpacer {
                Stat("المنزّل", "${videos.size}", Modifier.weight(1f))
                Stat("غير مشاهَد", "$unwatched", Modifier.weight(1f))
                Stat("المحفوظ", "${videos.count { it.saved }}", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))

        AppCard {
            SectionTitle("كيف يعمل TikGon؟")
            Spacer(Modifier.height(6.dp))
            SubText("1) أرسل للبوت في واتساب: .redgif <كلمات البحث>")
            SubText("2) اختر الجودة مرة واحدة — تُطبَّق على كل فيديوهات الخلاصة")
            SubText("3) البوت يضخّ الفيديوهات، وTikGon ينزّلها في الخلفية ويعرضها هنا")
            SubText("4) العرض يبدأ بعد اكتمال 10 فيديوهات، وبلمستك أنت فقط — لا تشغيل تلقائي أبداً")
            SubText("5) الفيديو المشاهَد يُحذف بعد 10 دقائق (إلا ما تحفظه بالقلب ❤️)")
            SubText("6) للإيقاف من واتساب: .stopredgif")
            Spacer(Modifier.height(6.dp))
            SubText("المحادثة المراقبة: ${chatName.ifEmpty { "—" }}")
            SubText("الاتصال: " + connText(conn))
        }
    }
}

/** «الوارد»: حالة المحرك والتحميلات الجارية والسجل */
@Composable
fun InboxPage() {
    val conn by Repo.conn.collectAsState()
    val jobs by Repo.dlJobs.collectAsState()
    val logs by Repo.logs.collectAsState()
    val up by Repo.engineUp.collectAsState()
    val videos by Repo.videos.collectAsState()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("الوارد", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(14.dp))

        AppCard {
            SectionTitle("المحرك")
            Spacer(Modifier.height(6.dp))
            SubText("الحالة: " + connText(conn))
            SubText(if (up) "المحرك يعمل في الخلفية" else "المحرك لم يبدأ بعد")
            SubText("فيديوهات في الصندوق: ${videos.size}")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { Repo.diag() }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("🔍 فحص الاتصال بواتساب") }
        }
        Spacer(Modifier.height(12.dp))

        AppCard {
            SectionTitle("التحميلات")
            Spacer(Modifier.height(6.dp))
            val live = jobs.filter { it.status == "queued" || it.status == "active" }
            val failed = jobs.filter { it.status == "failed" || it.status == "expired" }
            if (live.isEmpty() && failed.isEmpty()) {
                SubText("لا تحميلات جارية الآن — الفيديوهات الجديدة تُنزَّل فور وصولها.")
            }
            live.forEach { j ->
                Text(j.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                if (j.size > 0) {
                    LinearProgressIndicator(
                        progress = { (j.done.toFloat() / j.size).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SubText("${fmtSize(j.done)} من ${fmtSize(j.size)}")
                } else {
                    SubText(fmtSize(j.done))
                }
                Spacer(Modifier.height(6.dp))
            }
            failed.forEach { j ->
                Text("❌ ${j.name}", fontSize = 13.sp, color = RedErr)
                if (j.error.isNotEmpty()) SubText(j.error)
            }
        }
        Spacer(Modifier.height(12.dp))

        AppCard {
            SectionTitle("السجل")
            Spacer(Modifier.height(6.dp))
            logs.takeLast(30).forEach { SubText(it) }
        }
    }
}
