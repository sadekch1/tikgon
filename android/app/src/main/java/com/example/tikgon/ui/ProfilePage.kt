package com.example.tikgon.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
    val dispRot by Prefs.dispRotate.collectAsState()
    val themeIdx by Prefs.theme.collectAsState()

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        PageHeader("أنا")
        Spacer(Modifier.height(18.dp))

        // ── المحفوظة ──
        SectionTitle("❤️ المحفوظة (${saved.size})")
        Spacer(Modifier.height(4.dp))
        SubText("المحفوظة بالقلب لا يمسّها حذف العشر دقائق. اضغط أي فيديو للقفز إليه في الخلاصة.")
        Spacer(Modifier.height(10.dp))
        if (saved.isEmpty()) {
            AppCard { SubText("لا شيء محفوظ بعد — اضغط ❤️ أثناء المشاهدة لحفظ فيديو نهائياً.") }
        } else {
            saved.chunked(3).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(18.dp))

        // ── المظهر: السمات ──
        AppCard {
            SectionTitle("🎨 المظهر")
            Spacer(Modifier.height(4.dp))
            SubText("اختر سمة — تُطبَّق فوراً على كل التطبيق وتُحفظ لجلستك القادمة.")
            Spacer(Modifier.height(10.dp))
            themeDefs.forEachIndexed { i, t ->
                ThemeRow(t, selected = themeIdx == i) {
                    Prefs.set(Prefs.theme, "theme", i)
                }
                if (i < themeDefs.size - 1) Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ── محادثة البوت ──
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

        // ── وضع العرض ──
        AppCard {
            SectionTitle("وضع العرض")
            Spacer(Modifier.height(6.dp))
            SettingSwitch("قفل التدوير الأفقي 🔄", dispRot) {
                Prefs.set(Prefs.dispRotate, "dispRotate", it)
            }
            SubText(
                "التدوير يدوي فقط: زر 🔄 في الخلاصة يبدّل بين العمودي والأفقي ويبقى اختيارك محفوظاً لكل فيديو وجلسة — لا يوجد أي تدوير تلقائي.\n" +
                    "وزر ⛶ يدخل وضع ملء شاشة نظيف: تختفي كل الأزرار وأشرطة النظام، ولمسة واحدة تُظهر الطبقة مؤقتاً."
            )
        }
        Spacer(Modifier.height(12.dp))

        // ── الحساب ──
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
            SubText("مكانك في الخلاصة محفوظ دائماً: ارجع من أي صفحة أو أعد فتح التطبيق وستجد نفسك عند نفس الفيديو ونفس الثانية.")
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun ThemeRow(t: AppThemeDef, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // عيّنات ألوان السمة: خلفية / سطح / تمييز / تمييز ثانٍ
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            Swatch(t.bg)
            Swatch(t.surface2)
            Swatch(t.accent)
            Swatch(t.accent2)
        }
        Spacer(Modifier.size(12.dp))
        Text(
            t.name, Modifier.weight(1f), fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (selected) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) { Text("✓", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Swatch(c: Color) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(c)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
    )
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
        Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1F))
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
        Box(
            Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) { Text("❤️", fontSize = 11.sp) }
    }
}
