@file:OptIn(ExperimentalFoundationApi::class, UnstableApi::class)

package com.example.tikgon.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
fun FeedScreen(active: Boolean, onCleanMode: (Boolean) -> Unit) {
    val videos by Repo.videos.collectAsState()
    val session by Repo.session.collectAsState()
    val conn by Repo.conn.collectAsState()
    val sessionCount = if (session.id != 0L) videos.count { it.sessionId == session.id } else 0

    when {
        session.breakBaseline >= 0 -> BreakScreen(session)
        videos.isEmpty() -> IdleScreen(conn)
        session.active && !session.unlocked && sessionCount < 10 ->
            WaitingScreen(session, sessionCount, conn)
        else -> FeedPager(videos, session, active, onCleanMode)
    }
}

@Composable
private fun ThemedBackdrop(content: @Composable () -> Unit) {
    val theme = LocalAppTheme.current
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(theme.bg, theme.surface2))
        )
    ) { content() }
}

// ───────────────────────── لا خلاصة بعد ─────────────────────────
@Composable
private fun IdleScreen(conn: String) {
    val chatName by Prefs.chatName.collectAsState()
    ThemedBackdrop {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🎬", fontSize = 44.sp) }
            Spacer(Modifier.height(18.dp))
            Text("لا توجد خلاصة بعد", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(10.dp))
            Text(
                "TikGon يستقبل فقط — الخلاصة تبدأ من واتساب:\n\n" +
                    "1) افتح واتساب وأرسل للبوت:\n.redgif <كلمات البحث>\n\n" +
                    "2) اختر الجودة مرة واحدة (HD أو SD)\n\n" +
                    "3) الفيديوهات تصل إلى هنا وتُنزَّل تلقائياً في الخلفية",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))
            Pill("الحالة: " + connText(conn), if (conn == "open") GreenOk else AmberWarn)
            if (chatName.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SubText("المحادثة المراقبة: $chatName", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────── بوابة العشرة ─────────────────────────
@Composable
private fun WaitingScreen(session: SessionState, count: Int, conn: String) {
    val theme = LocalAppTheme.current
    val accent = MaterialTheme.colorScheme.primary
    ThemedBackdrop {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("⏳", fontSize = 44.sp) }
            Spacer(Modifier.height(18.dp))
            Text(
                "تُجهَّز خلاصة «${session.query}»…",
                fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Text("$count من 10", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = accent)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth(0.72f).height(9.dp).clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth((count / 10f).coerceIn(0f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Brush.horizontalGradient(listOf(accent, theme.accent2)))
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "يبدأ العرض بعد اكتمال 10 فيديوهات منزّلة — حتى لا تنتظر أي فيديو بعد البداية.\n" +
                    "التحميل مستمر في الخلفية حتى لو أغلقت التطبيق، ولن يبدأ أي تشغيل وحده.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
                fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp
            )
            Spacer(Modifier.height(18.dp))
            Pill("الحالة: " + connText(conn), if (conn == "open") GreenOk else AmberWarn)
        }
    }
}

// ───────────────────────── شاشة الاستراحة ─────────────────────────
@Composable
private fun BreakScreen(session: SessionState) {
    val videos by Repo.videos.collectAsState()
    val theme = LocalAppTheme.current
    val accent = MaterialTheme.colorScheme.primary
    val arrived = (videos.size - session.breakBaseline).toInt().coerceAtLeast(0)
    val ready = arrived >= 10
    ThemedBackdrop {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🌙", fontSize = 44.sp) }
            Spacer(Modifier.height(18.dp))
            Text("استراحة", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            if (ready) {
                Text(
                    "✅ الدفعة جاهزة — $arrived فيديو جديد بانتظارك",
                    color = GreenOk, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Button(onClick = { Repo.exitBreak() },
                    modifier = Modifier.fillMaxWidth(0.8f).height(52.dp),
                    shape = RoundedCornerShape(16.dp)) {
                    Text("عودة للخلاصة", fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    "جارٍ تجهيز دفعة جديدة… وصل $arrived من 10",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    fontSize = 15.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.fillMaxWidth(0.72f).height(9.dp).clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier.fillMaxWidth((arrived / 10f).coerceIn(0f, 1f)).fillMaxHeight()
                            .clip(RoundedCornerShape(9.dp))
                            .background(Brush.horizontalGradient(listOf(accent, theme.accent2)))
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "ستصلك إشارة صامتة عند الجاهزية. لن يفتح التطبيق ولن يبدأ أي فيديو وحده.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    fontSize = 12.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                OutlinedButton(onClick = { Repo.exitBreak() }, modifier = Modifier.fillMaxWidth(0.8f),
                    shape = RoundedCornerShape(16.dp)) {
                    Text("متابعة الآن بما هو متاح")
                }
            }
        }
    }
}

// ───────────────────────── الخلاصة (مصفّح عمودي + مشغّل واحد) ─────────────────────────
@Composable
private fun FeedPager(
    videos: List<FeedVideo>,
    session: SessionState,
    active: Boolean,
    onCleanMode: (Boolean) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = ctx as? Activity
    val view = LocalView.current

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

    // استعادة المكان: صفحة الفيديو المحفوظ عند أول تركيب (حتى بعد إعادة تشغيل التطبيق)
    val restoredPage = remember {
        val p = Prefs.feedLastPath.value
        val idx = pages.indexOfFirst { it is FeedPage.VideoPage && it.v.path == p }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(initialPage = restoredPage) { pageCountState }

    val player = remember {
        ExoPlayer.Builder(ctx).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    var startedByUser by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var loadedPath by remember { mutableStateOf("") }
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var bufMs by remember { mutableLongStateOf(0L) }
    // موضع الاستعادة: يُطبَّق مرة واحدة على الفيديو الذي كنّا عليه
    var pendingSeekPath by remember { mutableStateOf(Prefs.feedLastPath.value) }
    var pendingSeekMs by remember { mutableLongStateOf(Prefs.feedLastPos.value) }

    /** حفظ المكان الحالي (الفيديو + موضع التشغيل) في التخزين الدائم */
    fun savePlace() {
        val page = pagesState.getOrNull(pagerState.currentPage) as? FeedPage.VideoPage ?: return
        val pos = if (loadedPath == page.v.path) player.currentPosition.coerceAtLeast(0L) else 0L
        Prefs.saveFeedPlace(page.v.path, pos)
    }

    // ── وضع ملء الشاشة النظيف: لا أشرطة نظام ولا أي عنصر واجهة ──
    var cleanMode by remember { mutableStateOf(false) }
    var tempOverlay by remember { mutableStateOf(false) }
    val overlayVisible = !cleanMode || tempOverlay

    fun enterClean() {
        cleanMode = true
        tempOverlay = false
        onCleanMode(true)
    }

    fun exitClean() {
        cleanMode = false
        tempOverlay = false
        onCleanMode(false)
    }

    // إخفاء تلقائي للطبقة المؤقتة بعد ٢٫٥ ثانية أثناء التشغيل
    LaunchedEffect(tempOverlay, isPlaying) {
        if (cleanMode && tempOverlay && isPlaying) {
            delay(2500)
            tempOverlay = false
        }
    }
    BackHandler(enabled = cleanMode) { exitClean() }

    // أشرطة النظام تتبع الوضع النظيف
    LaunchedEffect(cleanMode) {
        val act = activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(act.window, view)
        if (cleanMode) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) savePlace()
            }
        }
        player.addListener(listener)
        onDispose {
            savePlace()
            player.removeListener(listener)
            player.release()
        }
    }

    // مغادرة التطبيق توقف التشغيل دائماً وتحفظ المكان — لا شيء يعمل صوتياً في الخلفية
    val lifecycleOwner = ctx as? ComponentActivity
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) {
                player.pause()
                savePlace()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(obs)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(obs) }
    }

    // تنظيف عند مغادرة الخلاصة نهائياً: إظهار الأشرطة، فكّ التوجيه، إبلاغ الغلاف
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (activity != null) {
                WindowInsetsControllerCompat(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            onCleanMode(false)
        }
    }

    // عند الانتقال لتبويب آخر: إيقاف + حفظ المكان + خروج من الوضع النظيف
    LaunchedEffect(active) {
        if (!active) {
            player.pause()
            savePlace()
            if (cleanMode) exitClean()
        }
    }

    // التدوير يدوي فقط (زر 🔄): عمودي ⇄ أفقي، محفوظ ويُعاد تطبيقه دائماً
    val dispRot by Prefs.dispRotate.collectAsState()
    LaunchedEffect(dispRot, active) {
        val act = activity ?: return@LaunchedEffect
        act.requestedOrientation = when {
            !active -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            dispRot -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
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
                    if (page.v.path == pendingSeekPath && pendingSeekMs > 0) {
                        player.seekTo(pendingSeekMs)
                        pendingSeekPath = ""
                    }
                    if (startedByUser && !userPaused) player.play() else player.pause()
                }
                savePlace()
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

    // تحديث مواضع شريط التقديم كل نصف ثانية + حفظ دوري للمكان أثناء التشغيل
    LaunchedEffect(player) {
        while (true) {
            posMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            durMs = if (d > 0) d else 0L
            bufMs = player.bufferedPosition.coerceAtLeast(0L)
            delay(500)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(4000)
            savePlace()
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

    fun onVideoTap() {
        if (cleanMode) tempOverlay = !tempOverlay else togglePlay()
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
                overlayVisible = overlayVisible,
                cleanMode = cleanMode,
                posMs = posMs,
                durMs = durMs,
                bufMs = bufMs,
                session = session,
                onTap = { onVideoTap() },
                onSeek = { target -> player.seekTo(target) },
                onToggleFullscreen = { if (cleanMode) exitClean() else enterClean() },
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
    overlayVisible: Boolean,
    cleanMode: Boolean,
    posMs: Long,
    durMs: Long,
    bufMs: Long,
    session: SessionState,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit
) {
    val theme = LocalAppTheme.current
    val accent = MaterialTheme.colorScheme.primary
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

        // طبقة اللمس: في الوضع العادي بدء/إيقاف، وفي النظيف إظهار/إخفاء الطبقة
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTap() }
        )

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // تدرّجات حماية القراءة أعلى وأسفل الفيديو
                Box(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(300.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                            )
                        )
                )

                // شريحة الجلسة أعلى المنتصف
                Row(
                    Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 10.dp)
                ) {
                    GlassChip(
                        "🎞 " + v.query.ifEmpty { "TikGon" } +
                            if (session.quality.isNotEmpty()) " • ${session.quality}" else ""
                    )
                }

                // مؤشر التشغيل الكبير — لا يظهر إلا بلمسة المستخدم أصلاً (لا تشغيل تلقائي)
                if (isCurrent && !isPlaying) {
                    Box(
                        Modifier.align(Alignment.Center).size(78.dp).clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("▶", fontSize = 34.sp, color = Color.White) }
                }

                // عمود الأفعال الجانبي (زجاجي، مع إحساس ضغط)
                Column(
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 10.dp, bottom = if (cleanMode) 64.dp else 138.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeartButton(saved = v.saved) { Repo.toggleSaved(v.path) }
                    RailButton("⬇", "تنزيل") { onSaveToGallery() }
                    RailButton("↗", "مشاركة") { onShare() }
                    RailButton("⛶", "الشاشة", active = cleanMode, accent = accent) { onToggleFullscreen() }
                    RailButton("🔄", "تدوير", active = dispRot, accent = accent) {
                        Prefs.set(Prefs.dispRotate, "dispRotate", !dispRot)
                    }
                }

                // معلومات أسفل اليسار + شرائح زجاجية
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 84.dp, bottom = if (cleanMode) 66.dp else 132.dp)
                ) {
                    Text(
                        "@" + v.query.ifEmpty { "tikgon" },
                        fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White
                    )
                    if (v.caption.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(v.caption, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 3)
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (v.index > 0) GlassChip("🎞 ${v.index}")
                        if (session.quality.isNotEmpty()) GlassChip(session.quality)
                        GlassChip(fmtSize(v.size))
                    }
                }

                // شريط التقديم/التقديم اليدوي — فوق شريط التنقّل، ويختفي في الوضع النظيف
                if (isCurrent) {
                    SeekBar(
                        posMs = posMs, durMs = durMs, bufMs = bufMs,
                        accent = accent, onSeek = onSeek,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .padding(bottom = if (cleanMode) 4.dp else 72.dp)
                    )
                }
            }
        }
    }
}

// ───────────────────────── شريط التقديم (قابل للسحب) ─────────────────────────
@Composable
private fun SeekBar(
    posMs: Long,
    durMs: Long,
    bufMs: Long,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableStateOf(0f) }
    val frac = when {
        dragging -> dragFrac
        durMs > 0 -> (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    Column(modifier) {
        // فقاعة الوقت أثناء السحب (صف محجوز دائماً حتى لا تقفز الواجهة)
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            if (dragging && durMs > 0) {
                Text(
                    "${fmtTime((dragFrac * durMs).toLong())} / ${fmtTime(durMs)}",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(26.dp)
                .pointerInput(durMs) {
                    detectTapGestures { off ->
                        if (durMs > 0 && size.width > 0) {
                            onSeek((off.x / size.width * durMs).toLong().coerceIn(0L, durMs))
                        }
                    }
                }
                .pointerInput(durMs) {
                    detectDragGestures(
                        onDragStart = { off ->
                            dragging = true
                            if (size.width > 0) dragFrac = (off.x / size.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            if (size.width > 0) {
                                dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                        onDragEnd = {
                            if (durMs > 0) onSeek((dragFrac * durMs).toLong().coerceIn(0L, durMs))
                            dragging = false
                        },
                        onDragCancel = { dragging = false }
                    )
                }
        ) {
            val w = size.width
            val cy = size.height / 2f
            val trackH = if (dragging) 5.dp.toPx() else 3.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, cy), Offset(w, cy), trackH, StrokeCap.Round)
            if (durMs > 0) {
                val bf = (bufMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)
                if (bf > 0f) {
                    drawLine(Color.White.copy(alpha = 0.45f), Offset(0f, cy), Offset(w * bf, cy), trackH, StrokeCap.Round)
                }
                if (frac > 0f) {
                    drawLine(accent, Offset(0f, cy), Offset(w * frac, cy), trackH, StrokeCap.Round)
                }
                drawCircle(accent, radius = if (dragging) 8.dp.toPx() else 6.dp.toPx(), center = Offset(w * frac, cy))
                drawCircle(Color.White, radius = if (dragging) 3.5.dp.toPx() else 2.5.dp.toPx(), center = Offset(w * frac, cy))
            }
        }
    }
}

// ───────────────────────── أزرار السكة الجانبية ─────────────────────────
@Composable
private fun RailButton(
    emoji: String,
    label: String,
    active: Boolean = false,
    accent: Color = Color.Transparent,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.8f else 1f, label = "railPress")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(
                    if (active && accent != Color.Transparent) accent.copy(alpha = 0.92f)
                    else Color.Black.copy(alpha = 0.42f)
                )
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 21.sp, color = Color.White) }
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun HeartButton(saved: Boolean, onClick: () -> Unit) {
    val anim = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(saved) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        anim.snapTo(0.55f)
        anim.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        )
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.8f else 1f, label = "heartPress")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(48.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(interactionSource = interaction, indication = null) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (saved) "❤️" else "🤍",
                fontSize = 24.sp,
                modifier = Modifier.graphicsLayer { scaleX = anim.value; scaleY = anim.value }
            )
        }
        Spacer(Modifier.height(3.dp))
        Text("حفظ", fontSize = 10.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

// ───────────────────────── بطاقة الحافة داخل الخلاصة ─────────────────────────
@Composable
private fun BreakCardContent(remaining: Int, onBreak: () -> Unit, onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.align(Alignment.Center).padding(24.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🌙", fontSize = 50.sp)
            Spacer(Modifier.height(12.dp))
            Text("اقتربت من الحافة", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(
                "تبقّى $remaining فيديو غير مشاهَد. خذ استراحة بينما تُجهَّز دفعة جديدة — التحميل مستمر في الخلفية ولن يبدأ أي شيء وحده.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp,
                textAlign = TextAlign.Center, lineHeight = 21.sp
            )
            Spacer(Modifier.height(22.dp))
            Button(onClick = onBreak, modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text("🌙 استراحة", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)) {
                Text("متابعة", color = Color.White)
            }
        }
    }
}
