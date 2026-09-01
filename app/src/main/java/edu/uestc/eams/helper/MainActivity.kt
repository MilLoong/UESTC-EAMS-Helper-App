package edu.uestc.eams.helper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import edu.uestc.eams.helper.ui.compose.MainScreen
import edu.uestc.eams.helper.ui.theme.UestcHelperTheme
import edu.uestc.eams.helper.worker.CourseNotificationWorker

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        intent?.getIntExtra(CourseNotificationWorker.EXTRA_OPEN_TAB, -1)?.takeIf { it >= 0 }

        setContent {
            val app = application as EamsHelperApp
            val themeKey by app.themePreferences.theme.collectAsState()
            UestcHelperTheme(themeKey = themeKey) {
                MainScreen()
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
