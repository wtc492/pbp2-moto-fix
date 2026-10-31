package dev.budfix

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object Jobs {
    /** Seconds to wait after a connection event, so a normal connection has time to finish. */
    const val SETTLE_SECONDS = 8L

    fun scheduleCheck(c: Context, reason: String, delaySeconds: Long = SETTLE_SECONDS) {
        val req = OneTimeWorkRequestBuilder<CheckWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setInputData(workDataOf("reason" to reason))
            .build()
        // REPLACE: a burst of events (link up, then media up) collapses into one check.
        WorkManager.getInstance(c).enqueueUniqueWork("check", ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelCheck(c: Context) {
        WorkManager.getInstance(c).cancelUniqueWork("check")
    }

    fun fixNow(c: Context) {
        val req = OneTimeWorkRequestBuilder<FixWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(c).enqueueUniqueWork("fix", ExistingWorkPolicy.KEEP, req)
    }
}

class CheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        val address = Store.target(c) ?: return Result.success()
        val reason = inputData.getString("reason") ?: "event"

        val status = Checker.check(c, address)
        if (status == null) {
            Store.log(c, "[$reason] Bluetooth is off or unavailable")
            return Result.success()
        }

        Store.log(c, "[$reason] ${status.describe()}${if (status.phantom) "  <- STUCK" else ""}")

        if (!status.phantom) {
            Notifier.clearStuck(c)
            return Result.success()
        }

        if (Store.autoFix(c) && Store.tryReserveAutoFix(c)) {
            Store.log(c, "Auto-fix: restarting Bluetooth")
            // Run the restart as its own job. Turning Bluetooth off disconnects the buds,
            // and that disconnect cancels this check job, so a restart run inline here
            // would be killed before it turned Bluetooth back on.
            Jobs.fixNow(c)
        } else {
            if (Store.autoFix(c)) Store.log(c, "Auto-fix paused (2 restarts in the last 10 min); notifying instead")
            Notifier.stuck(c, status.name)
        }
        return Result.success()
    }
}

class FixWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Notifier.clearStuck(applicationContext)
        BtRestart.restart(applicationContext)
        return Result.success()
    }
}
