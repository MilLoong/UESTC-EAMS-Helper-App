package edu.uestc.eams.helper.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.uestc.eams.helper.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Foreground event polling, matching RainClassroom's receipt service; no local course timer. */
class ServerNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private var networkRegistered = false
    private lateinit var connectivity: ConnectivityManager
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { reconnect() }
        override fun onLost(network: Network) { reconnect() }
    }
    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { reconnect() }
    }

    private fun reconnect() { ServerNotifications.cancelReceive(); signal.trySend(Unit) }
    private fun notification(text: String): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("server_receipts", "服务器通知接收状态", NotificationManager.IMPORTANCE_LOW))
        val click = PendingIntent.getActivity(this, 91000, Intent(this, edu.uestc.eams.helper.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "server_receipts").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("教务助手：服务器通知模式").setContentText(text).setOngoing(true).setContentIntent(click).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(91000, notification("正在同步服务器提醒；息屏可能延迟"))
        if (ServerNotifications.mode(this) != "server") { stopSelf(); return START_NOT_STICKY }
        if (!started) {
            started = true
            val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_USER_PRESENT) }
            ContextCompat.registerReceiver(this, screen, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            connectivity = getSystemService(ConnectivityManager::class.java)
            try { connectivity.registerDefaultNetworkCallback(network); networkRegistered = true } catch (e: RuntimeException) { }
            scope.launch {
                var failures = 0
                try {
                    while (isActive && ServerNotifications.mode(this@ServerNotificationService) == "server") {
                        var waitSeconds = 10L
                        try {
                            val count = ServerNotifications.receiveOnce(this@ServerNotificationService)
                            failures = 0
                            if (count == 100) waitSeconds = 0
                            getSystemService(NotificationManager::class.java).notify(91000, notification("服务器事件已同步；正在等待新通知"))
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            waitSeconds = minOf(60L, 2L shl minOf(failures++, 5))
                            getSharedPreferences("server_receipt_status", MODE_PRIVATE).edit().putString("error", e.message ?: "同步失败").apply()
                            getSystemService(NotificationManager::class.java).notify(91000, notification("连接暂不可用，稍后重试；服务器可能仍在运行"))
                        }
                        if (waitSeconds > 0) withTimeoutOrNull(waitSeconds * 1000) { signal.receive() }
                    }
                } finally { stopSelf() }
            }
        } else reconnect()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        getSharedPreferences("server_receipt_status", MODE_PRIVATE).edit()
            .putString("error", "系统限制后台同步时长，请打开应用恢复并补取提醒").apply()
        stopSelf()
    }

    override fun onDestroy() {
        if (started) unregisterReceiver(screen)
        if (networkRegistered) connectivity.unregisterNetworkCallback(network)
        ServerNotifications.cancelReceive()
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

class ServerReceiptWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        return try {
            ServerNotifications.receiveOnce(applicationContext)
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.retry() }
    }
}

class ServerUploadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (ServerNotifications.mode(applicationContext) != "server") return Result.success()
        return try {
            ServerNotifications.upload(applicationContext)
            applicationContext.getSharedPreferences("server_receipt_status", Context.MODE_PRIVATE).edit().putString("config_error", "").apply()
            Result.success()
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            applicationContext.getSharedPreferences("server_receipt_status", Context.MODE_PRIVATE).edit()
                .putString("config_error", "课表或提醒设置尚未同步，服务器仍使用旧计划").apply()
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            if (ServerNotifications.mode(context) != "server") return
            val work = androidx.work.OneTimeWorkRequestBuilder<ServerUploadWorker>()
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("server_course_config",
                androidx.work.ExistingWorkPolicy.REPLACE, work)
        }
    }
}
