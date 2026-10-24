package edu.uestc.eams.helper.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import edu.uestc.eams.helper.MainActivity
import edu.uestc.eams.helper.R
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.prefs.CourseReminderPreferences
import edu.uestc.eams.helper.data.prefs.CourseReminderSentPreferences
import edu.uestc.eams.helper.notification.CourseNotificationHelper
import edu.uestc.eams.helper.notification.CourseReminderAlarmScheduler
import edu.uestc.eams.helper.notification.CourseReminderPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Pairing credentials stay in noBackupFilesDir, encrypted using this installation's Keystore key. */
object ServerNotifications {
    private const val ALIAS = "eams_server_binding_v1"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).followRedirects(false).build()
    private val receiveLock = Mutex()
    @Volatile private var receiving: Call? = null

    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "server-binding-v1.json"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun read(context: Context): JSONObject {
        val raw = try { file(context).openRead().use { it.readBytes().toString(Charsets.UTF_8) } }
        catch (e: java.io.FileNotFoundException) { return JSONObject().put("mode", "local") }
        val envelope = JSONObject(raw)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
        return JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
    }

    @Synchronized private fun write(context: Context, state: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val envelope = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(cipher.doFinal(state.toString().toByteArray()), Base64.NO_WRAP))
        val f = file(context)
        val stream = f.startWrite()
        try { stream.write(envelope.toString().toByteArray()); f.finishWrite(stream) }
        catch (e: Exception) { f.failWrite(stream); throw e }
    }

    fun mode(context: Context): String = try { read(context).optString("mode", "local") } catch (e: Exception) { "blocked" }
    fun local(context: Context) = mode(context) == "local"

    fun selectedMode(context: Context): String = runCatching {
        val state = read(context)
        ServerModePolicy.confirmed(state.optString("mode", "local"), state.optString("last_confirmed_mode"))
    }.getOrDefault("server")

    fun restoreLocalOffline(context: Context) {
        context.stopService(Intent(context, ServerNotificationService::class.java))
        cancelReceive()
        synchronized(this) {
            val state = read(context)
            state.put("mode", "local").put("last_confirmed_mode", "local")
                .put("remote_unconfirmed", state.has("token"))
            state.remove("target_mode")
            write(context, state)
        }
        CourseReminderAlarmScheduler.reschedule(context)
    }

    fun validateOrigin(raw: String, localDev: Boolean): String {
        return ServerOrigin.validate(raw, localDev)
    }

    private fun api(
        context: Context,
        state: JSONObject,
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        receipt: Boolean = false,
    ): JSONObject {
        val origin = validateOrigin(state.getString("server"), state.optBoolean("local_dev"))
        val request = Request.Builder().url(origin + path)
        if (state.has("token")) request.header("Authorization", "Bearer ${state.getString("token")}")
        if (method != "GET") {
            request.method(
                method,
                (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()),
            )
        }
        val call = client.newCall(request.build())
        if (receipt) receiving = call
        try {
            return call.execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    forgetAfterRevocation(context)
                    error("绑定已失效，请重新扫码")
                }
                check(response.isSuccessful) { "服务器 HTTP ${response.code}" }
                JSONObject(response.body?.string() ?: error("服务器返回空内容"))
            }
        } finally {
            if (receiving === call) receiving = null
        }
    }

    fun cancelReceive() { receiving?.cancel() }

    suspend fun pair(context: Context, origin: String, code: String, localDev: Boolean) =
        withContext(Dispatchers.IO) {
            if (read(context).has("token")) {
                runCatching { unbind(context) }.onFailure { forgetAfterRevocation(context) }
            }
            val state =
                JSONObject()
                    .put("server", validateOrigin(origin, localDev))
                    .put("local_dev", localDev)
            val info =
                JSONObject()
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("brand", Build.BRAND)
                    .put("model", Build.MODEL)
                    .put("android_release", Build.VERSION.RELEASE)
                    .put("sdk_int", Build.VERSION.SDK_INT)
                    .put("app_version", edu.uestc.eams.helper.BuildConfig.VERSION_NAME)
                    .put("app_version_code", edu.uestc.eams.helper.BuildConfig.VERSION_CODE)
            val result =
                api(
                    context,
                    state,
                    "/devices/pair",
                    "POST",
                    JSONObject()
                        .put("code", code)
                        .put("name", Build.MODEL.take(80))
                        .put("device_info", info),
                )
            state
                .put("device_id", result.getString("device_id"))
                .put("token", result.getString("token"))
                .put("mode", "switching")
                .put("cursor", 0)
            write(context, state)
            CourseReminderAlarmScheduler.cancelAll(context)
            switchMode(context, "server")
        }

    suspend fun switchMode(context: Context, mode: String) = withContext(Dispatchers.IO) {
        require(mode == "local" || mode == "server")
        val state = read(context)
        if (!state.has("token")) {
            require(mode == "local")
            synchronized(this@ServerNotifications) {
                write(context, state.put("mode", "local").put("last_confirmed_mode", "local"))
            }
            return@withContext
        }
        val previous = ServerModePolicy.confirmed(state.optString("mode"), state.optString("last_confirmed_mode"))
        synchronized(this@ServerNotifications) {
            write(context, state.put("last_confirmed_mode", previous).put("target_mode", mode).put("mode", "switching"))
        }
        cancelReceive()
        CourseReminderAlarmScheduler.cancelAll(context)
        try {
            if (mode == "server") upload(context)
            val result = api(context, state, "/devices/me/mode", "PUT", JSONObject().put("mode", mode))
            check(result.getString("mode") == mode) { "服务器没有确认模式切换" }
            synchronized(this@ServerNotifications) {
                val current = read(context)
                check(current.optString("token") == state.optString("token")) { "绑定已变更，请重新选择模式" }
                current.put("mode", mode).put("last_confirmed_mode", mode).put("remote_unconfirmed", false)
                current.remove("target_mode")
                if (mode == "local") current.put("cursor", result.getLong("cursor"))
                write(context, current)
            }
        } catch (e: Exception) {
            synchronized(this@ServerNotifications) {
                val current = read(context)
                if (current.optString("token") == state.optString("token")) {
                    current.put("mode", previous).put("last_confirmed_mode", previous).put("remote_unconfirmed", true)
                    current.remove("target_mode")
                    write(context, current)
                }
            }
            if (local(context)) CourseReminderAlarmScheduler.reschedule(context)
            else if (this@ServerNotifications.mode(context) == "server") runCatching { startReceiving(context) }
            throw e
        }
    }

    suspend fun upload(context: Context): Int = withContext(Dispatchers.IO) {
        val state = read(context)
        check(state.has("token")) { "请先扫码绑定" }
        val cache = AcademicCache(context)
        val now = System.currentTimeMillis() / 1000
        val courses = JSONArray()
        val seen = HashSet<String>()
        CourseReminderPlanner.listTriggers(cache, cache.loadTimetableMeta(), LocalDate.now(), 60, now, 7).forEach { t ->
            val id = CourseNotificationHelper.reminderKey(t.course, t.date)
            require(id.length <= 300) { "课程标识过长" }
            if (seen.add(id)) {
                courses.put(
                    JSONObject()
                        .put("id", id)
                        .put("course_name", t.course.courseName)
                        .put("room", t.course.room)
                        .put("start_at", t.startEpochSec),
                )
            }
        }
        api(
            context,
            state,
            "/devices/me/courses",
            "PUT",
            JSONObject()
                .put("courses", courses)
                .put("lead_minutes", CourseReminderPreferences(context).leadMinutes),
        )
        courses.length()
    }

    suspend fun receiveOnce(context: Context): Int = receiveLock.withLock {
        withContext(Dispatchers.IO) {
            val state = read(context)
            if (state.optString("mode") != "server") return@withContext 0
            val page =
                api(
                    context,
                    state,
                    "/devices/me/events?after=${state.optLong("cursor")}",
                    receipt = true,
                )
            check(page.getString("mode") == "server") { "服务器已暂停，请确认模式" }
            val events = page.getJSONArray("events")
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                synchronized(this@ServerNotifications) {
                    val current = read(context)
                    if (current.optString("mode") != "server" ||
                        current.optString("token") != state.optString("token")
                    ) {
                        return@withContext 0
                    }
                    if (event.getLong("seq") <= current.optLong("cursor")) return@synchronized
                    check(showEvent(context, event)) { "通知未显示，请允许通知并检查渠道设置" }
                    write(
                        context,
                        current
                            .put("cursor", event.getLong("seq"))
                            .put("last_sync", System.currentTimeMillis()),
                    )
                }
            }
            context.getSharedPreferences("server_receipt_status", Context.MODE_PRIVATE).edit()
                .putLong("last_sync", System.currentTimeMillis())
                .putString("error", "")
                .apply()
            events.length()
        }
    }

    private fun showEvent(context: Context, event: JSONObject): Boolean {
        if (!CourseNotificationHelper.hasPostNotificationPermission(context)) return false
        CourseNotificationHelper.ensureChannel(context)
        val key = event.getString("id")
        val sent = CourseReminderSentPreferences(context)
        if (sent.wasSentToday(key)) return true
        val test = event.optString("kind") == "test"
        val title = if (test) event.getString("title") else "服务器上课提醒：${event.getString("course_name") }"
        val body = if (test) event.getString("body") else {
            val start = event.getLong("start_at")
            val time = Instant.ofEpochSecond(start).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
            "$time 开课 · ${event.optString("room").ifBlank { "教室待定" }}" +
                if (start <= System.currentTimeMillis() / 1000) "\n离线补取，已过开课时间" else ""
        }
        val id = CourseNotificationHelper.notifyIdForKey(key)
        val click = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, CourseNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(click)
            .setAutoCancel(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())
        sent.markSentToday(key)
        return true
    }

    suspend fun unbind(context: Context) = withContext(Dispatchers.IO) {
        cancelReceive()
        CourseReminderAlarmScheduler.cancelAll(context)
        val state = read(context)
        if (state.has("token")) {
            runCatching { api(context, state, "/devices/me", "DELETE") }
        }
        write(context, JSONObject().put("mode", "local"))
        CourseReminderAlarmScheduler.reschedule(context)
    }

    fun forgetAfterRevocation(context: Context) {
        cancelReceive()
        write(context, JSONObject().put("mode", "local"))
        CourseReminderAlarmScheduler.reschedule(context)
    }

    fun startReceiving(context: Context) {
        if (mode(context) != "server") return
        ContextCompat.startForegroundService(context, Intent(context, ServerNotificationService::class.java))
    }
}
