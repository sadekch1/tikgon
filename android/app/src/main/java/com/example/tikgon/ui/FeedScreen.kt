@file:OptIn(ExperimentalFoundationApi::class, UnstableApi::class)

package com.example.tikgon.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.tikgon.FeedVideo
import com.example.tikgon.Gallery
import com.example.tikgon.Prefs
import com.example.tikgon.Repo
import com.example.tikgon.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface FeedPage {
    data class VideoPage(val v: FeedVideo) : FeedPage
    data object BreakPage : FeedPage
}

@Composable
fun FeedScreen(onOpenProfile: () -> Unit) {
    val videos by Repo.videos.collectAsState()
    val session by Repo.session.collectAsState()
    val conn by Repo.conn.collectAsState()
    val sessionCount = if (session.id != 0L) videos.count { it.sessionId == session.id } else 0

    when {
        session.breakBaseline >= 0 -> BreakScreen(session)
        videos.isEmpty() -> IdleScreen(conn)
        session.active && !session.unlocked && sessionCount < 10 ->
            WaitingScreen(session, sessionCount, conn)
        else -> FeedPager(videos, session)
    }
}

// ───────────────────────── لا خلاصة بعد ─────────────────────────
@Composable
private fun IdleScreen(conn: String) {
    val chatName by Prefs.chatName.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎬", fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text("لا توجد خلاصة بعد", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            "TikGon يستقبل فقط — الخلاصة تبدأ من واتساب:\n\n" +
                "1) افتح واتساب وأرسل للبوت:\n.redgif <كلمات البحث>\n\n" +
                "2) اختر الجودة مرة واحدة (HD أو SD)\n\n" +
                "3) الفيديوهات تصل إلى هنا وتُنزَّل تلقائياً في الخلفية",
            color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Pill("الحالة: " + connText(conn), if (conn == "open") GreenOk else AmberWarn)
        if (chatName.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SubText("المحادثة المراقبة: $chatName", Color.White.copy(alpha = 0.6f))
        }
    }
}

// ───────────────────────── بوابة العشرة ─────────────────────────
@Composable
private fun WaitingScreen(session: SessionState, count: Int, conn: String) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⏳", fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text(
            "تُجهَّز خلاصة «${session.query}»…",
            fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text("$count من 10", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (count / 10f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(0.7f),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "يبدأ العرض بعد اكتمال 10 فيديوهات منزّلة — حتى لا تنتظر أي فيديو بعد البداية.\n" +
                "التحميل مستمر في الخلفية حتى لو أغلقت التطبيق، ولن يبدأ أي تشغيل وحده.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Pill("الحالة: " + connText(conn), if (conn == "open") GreenOk else AmberWarn)
    }
}

// ───────────────────────── شاشة الاستراحة ─────────────────────────
@Composable
private fun BreakScreen(session: SessionState) {
    val videos by Repo.videos.collectAsState()
    val arrived = (videos.size - session.breakBaseline).toInt().coerceAtLeast(0)
    val ready = arrived >= 10
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌙", fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text("استراحة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        if (ready) {
            Text(
                "✅ الدفعة جاهزة — $arrived فيديو جديد بانتظارك",
                color = GreenOk, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { Repo.exitBreak() }, modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)) {
                Text("عودة للخلاصة", fontWeight = FontWeight.Bold)
            }
        } else {
            Text(
                "جارٍ تجهيز دفعة جديدة… وصل $arrived من 10",
                color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { (arrived / 10f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.7f),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "ستصلك إشارة صامتة عند الجاهزية. لن يفتح التطبيق ولن يبدأ أي فيديو وحده.",
                color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { Repo.exitBreak() }, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("متابعة الآن بما هو متاح", color = Color.White)
            }
        }
    }
}

// ───────────────────────── الخلاصة (مصفّح عمودي + مشغّل واحد) ─────────────────────────
@Composable
private fun FeedPager(videos: List<FeedVideo>, session: SessionState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val remaining = videos.count { it.watchedAt == 0L }
    var breakDismissed by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(remaining) { if (remaining > 3) breakDismissed = false }

    val pages = remember(videos, breakDismissed) {
        val list = ArrayList<FeedPage>()
        videos.forEach { list.add(FeedPage.VideoPage(it)) }
        if (remaining <= 3 && videos.isNotEmpty() && !breakDismissed) {
            val lastUnwatched = videos.indexOfLast { it.watchedAt == 0L }
            val pos = if (lastUnwatched >= 0) lastUnwatched + 1 else list.size
            list.add(pos.coerceIn(0, list.size), FeedPage.BreakPage)
        }
        list
    }

    val pagesState by rememberUpdatedState(pages)
    val pageCountState by rememberUpdatedState(pages.size)
    val pagerState = rememberPagerState { pageCountState }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    var startedByUser by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentLandscape by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loadedPath by remember { mutableStateOf("") }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    currentLandscape = videoSize.width > videoSize.height
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // مغادرة التطبيق/الشاشة توقف التشغيل دائماً — لا شيء يعمل في الخلفية صوتياً
    val lifecycleOwner = ctx as? ComponentActivity
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) player.pause() }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }

    // عند تغيّر الصفحة: علّم ما قبلها كمُشاهَد، وجهّز الحالي (إطار أول إن لم يبدأ المستخدم)
    LaunchedEffect(pagerState.currentPage) {
        val pgs = pagesState
        pgs.forEachIndexed { i, p ->
            if (i < pagerState.currentPage && p is FeedPage.VideoPage) Repo.markWatched(p.v.path)
        }
        when (val page = pgs.getOrNull(pagerState.currentPage)) {
            is FeedPage.VideoPage -> {
                if (page.v.path != loadedPath) {
                    loadedPath = page.v.path
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(page.v.path))))
                    player.prepare()
                    if (startedByUser && !userPaused) player.play() else player.pause()
                }
            }
            FeedPage.BreakPage -> player.pause()
            null -> Unit
        }
    }

    // قفزة من صفحة المحفوظة
    val focus by Repo.focusPath.collectAsState()
    LaunchedEffect(focus) {
        if (focus.isNotEmpty()) {
            val idx = pagesState.indexOfFirst { it is FeedPage.VideoPage && it.v.path == focus }
            if (idx >= 0) pagerState.scrollToPage(idx)
            Repo.focusPath.value = ""
        }
    }

    // شريط التقدم
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    // وضع العرض المحفوظ: ملء الشاشة / التدوير — يُطبَّق تلقائياً على كل فيديو
    val dispFs by Prefs.dispFullscreen.collectAsState()
    val dispRot by Prefs.dispRotate.collectAsState()
    val autoRot by Prefs.autoRotate.collectAsState()
    val activity = ctx as? Activity
    val view = LocalView.current
    LaunchedEffect(dispFs, dispRot, autoRot, currentLandscape) {
        val act = activity ?: return@LaunchedEffect
        act.requestedOrientation = when {
            dispRot -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            autoRot && currentLandscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val controller = WindowInsetsControllerCompat(act.window, view)
        if (dispFs) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (activity != null) {
                WindowInsetsControllerCompat(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
            userPaused = true
        } else {
            startedByUser = true
            userPaused = false
            player.play()
        }
    }

    VerticalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) { pageIdx ->
        when (val page = pages.getOrNull(pageIdx)) {
            is FeedPage.VideoPage -> VideoPageContent(
                v = page.v,
                isCurrent = pageIdx == pagerState.currentPage,
                player = player,
                isPlaying = isPlaying,
                progress = progress,
                session = session,
                onTogglePlay = { togglePlay() },
                onSaveToGallery = {
                    scope.launch {
                        try {
                            val name = withContext(Dispatchers.IO) {
                                Gallery.saveCopy(ctx, File(page.v.path), "tikgon_${page.v.index}_${page.v.ts}.mp4")
                            }
                            Toast.makeText(ctx, "حُفظ في المعرض: $name", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "فشل الحفظ: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onShare = {
                    try {
                        val intent = Gallery.shareIntent(ctx, File(page.v.path))
                        ctx.startActivity(android.content.Intent.createChooser(intent, "مشاركة الفيديو"))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "تعذّرت المشاركة: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            FeedPage.BreakPage -> BreakCardContent(
                remaining = remaining,
                onBreak = { Repo.enterBreak() },
                onContinue = { breakDismissed = true }
            )
            null -> Box(Modifier.fillMaxSize().background(Color.Black))
        }
    }
}

@Composable
private fun VideoPageContent(
    v: FeedVideo,
    isCurrent: Boolean,
    player: ExoPlayer,
    isPlaying: Boolean,
    progress: Float,
    session: SessionState,
    onTogglePlay: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit
) {
    val dispFs by Prefs.dispFullscreen.collectAsState()
    val dispRot by Prefs.dispRotate.collectAsState()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> view.player = if (isCurrent) player else null },
            modifier = Modifier.fillMaxSize()
        )

        // طبقة اللمس: بدء/إيقاف — لا تشغيل إلا بلمسة المستخدم
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTogglePlay() }
        )

        if (isCurrent && !isPlaying) {
            Box(
                Modifier.align(Alignment.Center).size(76.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) { Text("▶", fontSize = 36.sp, color = Color.White) }
        }

        // عمود الأفعال الجانبي
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RailButton(if (v.saved) "❤️" else "🤍", "حفظ") { Repo.toggleSaved(v.path) }
            RailButton("⬇️", "تنزيل") { onSaveToGallery() }
            RailButton("↗️", "مشاركة") { onShare() }
        }

        // معلومات أسفل اليسار
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 84.dp, bottom = 92.dp)
        ) {
            Text(
                "@" + v.query.ifEmpty { "tikgon" },
                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White
            )
            if (v.caption.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(v.caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 3)
            }
            if (v.index > 0 || session.quality.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "🎞 ${v.index}" + if (session.quality.isNotEmpty()) " • ${session.quality}" else "",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

        // زرّا العرض (أسفل اليمين) — حالتهما محفوظة وتسري على كل الجلسات
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 74.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ViewButton("⛶", dispFs) { Prefs.set(Prefs.dispFullscreen, "dispFullscreen", !dispFs) }
            ViewButton("🔄", dispRot) { Prefs.set(Prefs.dispRotate, "dispRotate", !dispRot) }
        }

        // شريط التقدم
        if (isCurrent) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(3.dp).background(Color(0xFFFE2C55))
                )
            }
        }
    }
}

@Composable
private fun RailButton(emoji: String, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 23.sp) }
        Text(label, fontSize = 11.sp, color = Color.White)
    }
}

@Composable
private fun ViewButton(emoji: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (active) Color(0xFFFE2C55).copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = 18.sp, color = Color.White) }
}

// ───────────────────────── بطاقة الحافة داخل الخلاصة ─────────────────────────
@Composable
private fun BreakCardContent(remaining: Int, onBreak: () -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🌙", fontSize = 54.sp)
        Spacer(Modifier.height(14.dp))
        Text("اقتربت من الحافة", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(10.dp))
        Text(
            "تبقّى $remaining فيديو غير مشاهَد. خذ استراحة بينما تُجهَّز دفعة جديدة — التحميل مستمر في الخلفية ولن يبدأ أي شيء وحده.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Button(onClick = onBreak, modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)) {
            Text("🌙 استراحة", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth(0.8f)) {
            Text("متابعة", color = Color.White)
        }
    }
}
