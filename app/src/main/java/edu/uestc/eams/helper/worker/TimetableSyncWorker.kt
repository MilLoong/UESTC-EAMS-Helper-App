package edu.uestc.eams.helper.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.uestc.eams.helper.EamsHelperApp

/**
 * 每天自动拉取当前教学周课表并写入本地缓存（需有效登录会话）。
 */
class TimetableSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? EamsHelperApp ?: return Result.success()
        val repo = app.uestcRepository
        repo.syncCurrentWeekTimetableIfNeeded()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "timetable_daily_sync"
    }
}
