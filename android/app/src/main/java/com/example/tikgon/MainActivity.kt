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

/** الواجهة الرئيسية: الخلاصة بأسلوب TikTok + الصفحات الجانبية */
class MainActivity : ComponentActivity() {

    private val askPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(applicationContext)
        FeedStore.sweep(applicationContext)   // حذف فوري لما انتهت دقائقه العشر

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            askPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        ContextCompat.startForegroundService(this, Intent(this, DownloadService::class.java))
        setContent { AppRoot() }
    }
}
