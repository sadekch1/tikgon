@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.tikgon.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tikgon.Prefs
import com.example.tikgon.Repo

@Composable
fun AppRoot() {
    val th by Prefs.theme.collectAsState()
    AppTheme(th) {
        val fatal by Repo.fatal.collectAsState()
        val crash by Repo.lastCrash.collectAsState()
        val clip = LocalClipboardManager.current

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MainFlow()
            if (fatal.isNotEmpty()) {
                Text(
                    fatal + "\n(اضغط للنسخ)", color = Color.White, fontSize = 12.sp,
                    maxLines = 16,
                    modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(12.dp)
                        .clip(RoundedCornerShape(12.dp)).background(RedErr)
                        .clickable { clip.setText(AnnotatedString(fatal)) }.padding(12.dp)
                )
            }
        }
        if (crash.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { Repo.lastCrash.value = "" },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("سبب إغلاق سابق") },
                text = {
                    Text(crash, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState()),
                        color = MaterialTheme.colorScheme.onSurface)
                },
                confirmButton = { TextButton(onClick = { clip.setText(AnnotatedString(crash)) }) { Text("نسخ") } },
                dismissButton = { TextButton(onClick = { Repo.lastCrash.value = "" }) { Text("إغلاق") } }
            )
        }
    }
}

/** التدفق: ربط الرقم ← اختيار محادثة البوت ← الخلاصة */
@Composable
fun MainFlow() {
    val conn by Repo.conn.collectAsState()
    val pairing by Repo.pairingActive.collectAsState()
    val showLogin = pairing || conn == "needpair" || conn == "loggedout" || conn == "pairing" ||
        (conn == "idle" && !Repo.isRegistered())
    val chatJid by Prefs.chatJid.collectAsState()

    when {
        showLogin -> LoginScreen()
        chatJid.isEmpty() -> ChatPickerScreen()
        else -> TikGonRoot()
    }
}

// ───────────────────────── الشاشة الرئيسية ─────────────────────────
/**
 * الخلاصة تبقى مركّبة دائماً في الخلفية (حتى لا يضيع مكان المستخدم عند التنقّل
 * بين التبويبات)، والصفحات الأخرى تُرسم فوقها، وشريط التنقّل زجاجي سفلي
 * يختفي تماماً في وضع ملء الشاشة النظيف.
 */
@Composable
fun TikGonRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showHint by remember { mutableStateOf(false) }
    var cleanMode by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // الخلاصة: دائماً في التركيب؛ تُوقَف وتُحفظ أماكنها عند مغادرة تبويبها
        FeedScreen(active = tab == 0, onCleanMode = { cleanMode = it })

        // الصفحات الأخرى فوق الخلاصة بخلفية معتمة من السمة
        if (tab != 0) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (tab) {
                    1 -> DiscoverPage()
                    3 -> InboxPage()
                    4 -> ProfilePage(onOpenFeed = { tab = 0 })
                }
            }
        }

        AnimatedVisibility(
            visible = !cleanMode,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TikGonNavBar(tab = tab, onTab = { i ->
                if (i == 2) showHint = true else tab = i
            })
        }
    }

    if (showHint) {
        AlertDialog(
            onDismissRequest = { showHint = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("بدء خلاصة جديدة") },
            text = {
                Text(
                    "TikGon يستقبل فقط ولا يرسل أوامر.\n\n" +
                        "لبدء خلاصة: افتح واتساب وأرسل للبوت:\n.redgif <كلمات البحث>\n\n" +
                        "ولإيقافها: .stopredgif",
                    color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp
                )
            },
            confirmButton = { TextButton(onClick = { showHint = false }) { Text("فهمت") } }
        )
    }
}

@Composable
private fun TikGonNavBar(tab: Int, onTab: (Int) -> Unit) {
    val theme = LocalAppTheme.current
    val items = listOf(
        "الرئيسة" to "🏠",
        "اكتشف" to "🧭",
        "" to "＋",
        "الوارد" to "💬",
        "أنا" to "👤"
    )
    Box(
        Modifier.fillMaxWidth()
            .background(theme.surface.copy(alpha = 0.88f))
            .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.07f))
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, (label, icon) ->
                if (i == 2) {
                    // زر المنتصف البارز بتدرّج لون التمييز
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "plusPress")
                        Box(
                            Modifier.offset(y = (-8).dp).size(48.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(theme.accent, theme.accent2))
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable(interactionSource = interaction, indication = null) { onTab(2) },
                            contentAlignment = Alignment.Center
                        ) { Text("＋", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                } else {
                    NavItem(
                        icon = icon, label = label,
                        selected = tab == i,
                        modifier = Modifier.weight(1f)
                    ) { onTab(i) }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: String,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, label = "navPress")
    val indicatorW by animateFloatAsState(if (selected) 20f else 0f, label = "navDot")
    Column(
        modifier.height(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 21.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            label, fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.width(indicatorW.dp).height(3.dp).clip(RoundedCornerShape(3.dp))
                .background(if (selected) accent else Color.Transparent)
        )
    }
}

// ───────────────────────── تسجيل الدخول (ربط برقم الهاتف) ─────────────────────────
@Composable
fun LoginScreen() {
    val code by Repo.code.collectAsState()
    val err by Repo.errorMsg.collectAsState()
    val conn by Repo.conn.collectAsState()
    val pairing by Repo.pairingActive.collectAsState()
    val clipboard = LocalClipboardManager.current
    var phone by rememberSaveable { mutableStateOf("") }
    val theme = LocalAppTheme.current
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(theme.accent, theme.accent2))),
            contentAlignment = Alignment.Center
        ) { Text("▶", fontSize = 42.sp, color = Color.White) }

        Spacer(Modifier.height(16.dp))
        Text("TikGon", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        SubText("اربط رقمك مرة واحدة وتُحفظ الجلسة — التطبيق يستقبل فقط ولا يرسل شيئاً")
        Spacer(Modifier.height(28.dp))

        AppCard {
            if (code.isEmpty()) {
                SectionTitle("ربط الواتساب")
                Spacer(Modifier.height(4.dp))
                SubText("أدخل رقمك مع رمز الدولة بدون +")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مثال: 213562396663") },
                    singleLine = true,
                    enabled = !pairing,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { Repo.pair(phone) },
                    enabled = phone.length >= 8 && !pairing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(if (pairing) "جارٍ طلب الرمز…" else "طلب رمز الربط", fontWeight = FontWeight.Bold) }

                if (pairing) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { Repo.cancelPairing() }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
                }
            } else {
                SectionTitle("رمز الربط")
                Spacer(Modifier.height(12.dp))
                Text(
                    code, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = accent,
                    letterSpacing = 4.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 18.dp)
                )
                Spacer(Modifier.height(14.dp))
                SubText("1) افتح واتساب ← الأجهزة المرتبطة")
                SubText("2) اختر «ربط جهاز»")
                SubText("3) اختر «الربط برقم الهاتف بدلاً من ذلك»")
                SubText("4) أدخل الرمز أعلاه")
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(code.replace("-", ""))) },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    ) { Text("نسخ الرمز") }
                    OutlinedButton(
                        onClick = { Repo.cancelPairing() },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)
                    ) { Text("إلغاء") }
                }
                Spacer(Modifier.height(8.dp))
                SubText("الحالة: " + connText(conn))
            }
            if (err.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(err, color = RedErr, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(16.dp))
        val logs by Repo.logs.collectAsState()
        val up by Repo.engineUp.collectAsState()
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التشخيص", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                Pill(if (up) "المحرك يعمل" else "المحرك لم يبدأ", if (up) GreenOk else RedErr)
            }
            Spacer(Modifier.height(6.dp))
            SubText("الحالة: " + connText(conn))
            logs.takeLast(10).forEach { SubText(it) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { Repo.diag() }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) { Text("🔍 فحص الاتصال بواتساب") }
        }
    }
}

fun connText(c: String) = when (c) {
    "open" -> "متصل"
    "connecting" -> "جارٍ الاتصال…"
    "reconnecting" -> "إعادة الاتصال…"
    "pairing" -> "بانتظار إدخال الرمز"
    "replaced" -> "فُتحت جلسة أخرى بنفس الحساب"
    "needpair", "loggedout" -> "غير مسجّل"
    else -> "تشغيل المحرك…"
}
