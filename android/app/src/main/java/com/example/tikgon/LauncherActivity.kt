package com.example.tikgon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.tikgon.ui.AppRoot

/**
 * نقطة الدخول:
 *  ① ربط الرقم مرة واحدة (رمز ربط — الجلسة محفوظة)
 *  ② اختيار محادثة البوت مرة واحدة
 *  ③ بعدها يفتح TikGon على الخلاصة مباشرة
 */
class LauncherActivity : ComponentActivity() {

    private val askPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(applicationContext)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // محرك واتساب يعمل دائماً في الخلفية (التنزيلات لا تتوقف بإغلاق الواجهة)
        ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))

        // مسار سريع: رقم مربوط ومحادثة مختارة → الخلاصة مباشرة
        if (Repo.isRegistered() && Prefs.chatJid.value.isNotEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent { AppRoot() }
    }
}
