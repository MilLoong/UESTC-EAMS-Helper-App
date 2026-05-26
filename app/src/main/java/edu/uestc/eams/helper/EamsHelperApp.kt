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
import edu.uestc.eams.helper.worker.CourseNotificationWorker
import edu.uestc.eams.helper.worker.TimetableSyncWorker
import java.util.concurrent.TimeUnit

/** 应用级依赖：网络客户端、登录模块、数据仓库与本地缓存。 */
class EamsHelperApp : Application() {

    val cookieJar = InMemoryCookieJar()

    private val okHttpClient by lazy { ApiConstants.buildOkHttp(cookieJar) }

    private val sessionCookieStorage by lazy { SessionCookieStorage(this) }

    val casRepository by lazy { CasLoginRepository(okHttpClient, cookieJar) }

    val academicCache by lazy { AcademicCache(this) }

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
        scheduleCourseNotifications()
        scheduleDailyTimetableSync()
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

    private fun scheduleCourseNotifications() {
        val request =
            PeriodicWorkRequestBuilder<CourseNotificationWorker>(15, TimeUnit.MINUTES)
                .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CourseNotificationWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
