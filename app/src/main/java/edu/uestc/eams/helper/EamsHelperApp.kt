package edu.uestc.eams.helper

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import edu.uestc.eams.helper.data.auth.CasLoginRepository
import edu.uestc.eams.helper.data.local.AcademicCache
import edu.uestc.eams.helper.data.network.ApiConstants
import edu.uestc.eams.helper.data.network.InMemoryCookieJar
import edu.uestc.eams.helper.data.repository.UestcRepository
import edu.uestc.eams.helper.data.session.SessionCookieStorage
import edu.uestc.eams.helper.data.prefs.ThemePreferences
import edu.uestc.eams.helper.notification.CourseReminderAlarmScheduler
import edu.uestc.eams.helper.worker.CourseNotificationWorker
import edu.uestc.eams.helper.worker.TimetableSyncWorker
import java.util.concurrent.TimeUnit

/** Application 级依赖注入入口。 */
class EamsHelperApp : Application() {

    val cookieJar = InMemoryCookieJar()

    private val okHttpClient by lazy { ApiConstants.buildOkHttp(cookieJar) }

    private val sessionCookieStorage by lazy { SessionCookieStorage(this) }

    val casRepository by lazy { CasLoginRepository(okHttpClient, cookieJar) }

    val academicCache by lazy { AcademicCache(this) }
    val themePreferences by lazy { ThemePreferences(this) }

    val uestcRepository by lazy {
        UestcRepository(
            client = okHttpClient,
            jar = cookieJar,
            casRepository = casRepository,
            sessionStorage = sessionCookieStorage,
            cache = academicCache,
        )
    }

    override fun onCreate() {
        super.onCreate()
        sessionCookieStorage.restoreInto(cookieJar)
        // 精确闹钟负责到点提醒；WorkManager 仅作兜底补挂闹钟
        CourseReminderAlarmScheduler.reschedule(this)
        scheduleCourseNotificationFallback()
        scheduleDailyTimetableSync()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "server_notification_receipts", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<edu.uestc.eams.helper.server.ServerReceiptWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    private fun scheduleDailyTimetableSync() {
        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val request =
            PeriodicWorkRequestBuilder<TimetableSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TimetableSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun scheduleCourseNotificationFallback() {
        val request =
            PeriodicWorkRequestBuilder<CourseNotificationWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CourseNotificationWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
