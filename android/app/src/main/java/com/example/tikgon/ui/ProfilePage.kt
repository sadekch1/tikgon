package com.example.tikgon.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tikgon.FeedVideo
import com.example.tikgon.Prefs
import com.example.tikgon.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** «أنا»: المحفوظة بالقلب (لا تُحذف أبداً) + الإعدادات */
@Composable
fun ProfilePage(onOpenFeed: () -> Unit) {
    val videos by Repo.videos.collectAsState()
    val saved = videos.filter { it.saved }
    val conn by Repo.conn.collectAsState()
    val chatName by Prefs.chatName.collectAsState()
    val chatJid by Prefs.chatJid.collectAsState()
    val dispFs by Prefs.dispFullscreen.collectAsState()
    val dispRot by Prefs.dispRotate.collectAsState()
    val autoRot by Prefs.autoRotate.collectAsState()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("أنا", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(14.dp))

        // ── المحفوظة ──
        SectionTitle("❤️ المحفوظة (${saved.size})")
        SubText("المحفوظة بالقلب لا يمسّها حذف العشر دقائق. اضغط أي فيديو للقفز إليه في الخلاصة.")
        Spacer(Modifier.height(10.dp))
        if (saved.isEmpty()) {
            AppCard { SubText("لا شيء محفوظ بعد — اضغط ❤️ أثناء المشاهدة لحفظ فيديو نهائياً.") }
        } else {
            saved.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { v ->
                        Box(Modifier.weight(1f)) {
                            SavedThumb(v) {
                                Repo.focusPath.value = v.path
                                onOpenFeed()
                            }
                        }
                    }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── الإعدادات ──
        AppCard {
            SectionTitle("محادثة البوت")
            Spacer(Modifier.height(4.dp))
            SubText("الحالية: ${chatName.ifEmpty { chatJid }}")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { Prefs.set(Prefs.chatJid, "chatJid", "") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("تغيير المحادثة") }
        }
        Spacer(Modifier.height(12.dp))

        AppCard {
            SectionTitle("وضع العرض (يُحفظ ويُطبَّق دائماً)")
            Spacer(Modifier.height(6.dp))
            SettingSwitch("ملء الشاشة الغامر افتراضياً", dispFs) {
                Prefs.set(Prefs.dispFullscreen, "dispFullscreen", it)
            }
            SettingSwitch("قفل التدوير الأفقي", dispRot) {
                Prefs.set(Prefs.dispRotate, "dispRotate", it)
            }
            SettingSwitch("تدوير تلقائي للفيديوهات الأفقية فقط", autoRot) {
                Prefs.set(Prefs.autoRotate, "autoRotate", it)
            }
            SubText("نفس الزرّين (⛶ و🔄) موجودان أسفل يمين المشغّل — تضبطهما مرة وتبقى كما هي لكل فيديو وجلسة.")
        }
        Spacer(Modifier.height(12.dp))

        AppCard {
            SectionTitle("الحساب")
            Spacer(Modifier.height(4.dp))
            SubText("الحالة: " + connText(conn))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { Repo.logout() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("تسجيل الخروج / إعادة الربط", color = RedErr) }
        }
        Spacer(Modifier.height(12.dp))

        AppCard {
            SectionTitle("كيف يعمل؟")
            Spacer(Modifier.height(4.dp))
            SubText("ابدأ خلاصة من واتساب: .redgif <كلمات البحث> ثم اختر الجودة مرة واحدة.")
            SubText("أوقفها: .stopredgif")
            SubText("TikGon يستقبل فقط: لا يرسل أوامر ولا يشغّل أي فيديو وحده أبداً.")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SavedThumb(v: FeedVideo, onClick: () -> Unit) {
    val thumb by produceState<Bitmap?>(null, v.path) {
        value = withContext(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(v.path)
                    r.getFrameAtTime(0)
                } finally {
                    r.release()
                }
            } catch (_: Exception) { null }
        }
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.75f).background(Color(0xFF1A1A1F))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("🎞", fontSize = 22.sp)
        }
        Text(
            "❤️", fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
        )
    }
}
