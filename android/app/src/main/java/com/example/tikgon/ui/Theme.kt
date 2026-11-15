package com.example.tikgon.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat

/**
 * سمة متكاملة: لون تمييز (accent) + تدرّجه الثاني، وألوان صفحات التطبيق.
 * الخلاصة نفسها تبقى سوداء دائماً، لكنها تستخدم لون تمييز السمة في
 * شريط التقديم والأزرار النشطة والشرائح والقلب.
 */
class AppThemeDef(
    val name: String,
    val accent: Color,
    val accent2: Color,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val sub: Color,
    val dark: Boolean
)

val themeDefs = listOf(
    // ٠ — منتصف الليل: أسود خالص + أحمر وردي بأسلوب TikTok
    AppThemeDef(
        "منتصف الليل", Color(0xFFFE2C55), Color(0xFFFF7A9E),
        Color(0xFF000000), Color(0xFF101014), Color(0xFF1E1E26),
        Color(0xFFFFFFFF), Color(0xFF9BA0AE), true
    ),
    // ١ — بنفسجي: بنفسجي عميق + وردي
    AppThemeDef(
        "بنفسجي", Color(0xFFA259FF), Color(0xFFFF5CA8),
        Color(0xFF0D0616), Color(0xFF180B2A), Color(0xFF261447),
        Color(0xFFF6F1FF), Color(0xFFB3A4D6), true
    ),
    // ٢ — محيط: أزرق غامق + فيروزي
    AppThemeDef(
        "محيط", Color(0xFF2DD4BF), Color(0xFF38BDF8),
        Color(0xFF03161D), Color(0xFF082630), Color(0xFF0F3644),
        Color(0xFFEBFBFF), Color(0xFF93B8C4), true
    ),
    // ٣ — زمرد: أخضر غامق + زمردي وذهبي
    AppThemeDef(
        "زمرد", Color(0xFF34D399), Color(0xFFE3B341),
        Color(0xFF04120B), Color(0xFF0A2114), Color(0xFF133723),
        Color(0xFFEDFDF3), Color(0xFF9DBFA9), true
    ),
    // ٤ — نهاري: سمة فاتحة للصفحات (الخلاصة تبقى سوداء)
    AppThemeDef(
        "نهاري", Color(0xFFFE2C55), Color(0xFFFF8A5C),
        Color(0xFFF4F5FA), Color(0xFFFFFFFF), Color(0xFFE9ECF4),
        Color(0xFF14161C), Color(0xFF5D6472), false
    )
)

/** السمة الحالية — تصل إليها مكونات الخلاصة لألوان التمييز والتدرّجات */
val LocalAppTheme = staticCompositionLocalOf { themeDefs[0] }

val GreenOk = Color(0xFF22C55E)
val AmberWarn = Color(0xFFF59E0B)
val RedErr = Color(0xFFEF4444)

@Composable
fun AppTheme(themeIdx: Int, content: @Composable () -> Unit) {
    val t = themeDefs[themeIdx.coerceIn(0, themeDefs.size - 1)]

    val scheme = if (t.dark) {
        darkColorScheme(
            primary = t.accent, onPrimary = Color.White, secondary = t.accent2,
            background = t.bg, onBackground = t.text,
            surface = t.surface, onSurface = t.text,
            surfaceVariant = t.surface2, onSurfaceVariant = t.sub,
            outline = t.sub.copy(alpha = 0.45f),
            primaryContainer = t.accent.copy(alpha = 0.22f), onPrimaryContainer = t.accent,
            error = RedErr
        )
    } else {
        lightColorScheme(
            primary = t.accent, onPrimary = Color.White, secondary = t.accent2,
            background = t.bg, onBackground = t.text,
            surface = t.surface, onSurface = t.text,
            surfaceVariant = t.surface2, onSurfaceVariant = t.sub,
            outline = t.sub.copy(alpha = 0.5f),
            primaryContainer = t.accent.copy(alpha = 0.14f), onPrimaryContainer = t.accent,
            error = RedErr
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as Activity).window
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !t.dark
                isAppearanceLightNavigationBars = !t.dark
            }
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalAppTheme provides t
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
