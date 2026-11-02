package dev.budfix

import android.bluetooth.BluetoothProfile
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

        // A restart + reconnect is already running and will report its own result.
        if (Store.fixing(c) && reason != "manual") return Result.success()

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

/** Restarts Bluetooth, then makes sure the buds actually come back with audio. */
class FixWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(NonCancellable) {
        val c = applicationContext
        Store.setFixing(c, true)
        try {
            Notifier.clearStuck(c)
            if (BtRestart.restart(c)) {
                Store.target(c)?.let { reconnect(c, it) }
            }
        } finally {
            Store.setFixing(c, false)
        }
        Result.success()
    }

    private suspend fun reconnect(c: Context, address: String) {
        // Give the phone's own auto-reconnect a chance first.
        if (waitForAudio(c, address, 10_000)) return fixed(c, address)

        for (attempt in 1..3) {
            Store.log(c, "Buds didn't come back on their own; reconnecting (attempt $attempt of 3)")
            val accepted = Reconnect.request(c, address)

            if (!accepted && Store.tapFallback(c)) {
                val name = Checker.check(c, address)?.name ?: break
                if (!TapService.tapDevice(c, name)) {
                    Store.log(c, "Tap fallback skipped (service off, or phone locked)")
                }
            }

            if (waitForAudio(c, address, 25_000)) return fixed(c, address)

            // Media connected, but Android kept audio on the phone: point it at the buds.
            val s = Checker.check(c, address)
            if (s != null && s.a2dp == BluetoothProfile.STATE_CONNECTED && !s.routed) {
                Reconnect.makeActive(c, address)
                if (waitForAudio(c, address, 5_000)) return fixed(c, address)
            }
        }

        val name = Checker.check(c, address)?.name ?: "Your buds"
        Store.log(c, "Gave up: $name didn't reconnect")
        Notifier.result(
            c, "$name didn't reconnect",
            "Bluetooth was restarted, but reconnecting didn't work. Tap the buds in Bluetooth settings. The Bud Fix log shows what was tried."
        )
    }

    private suspend fun waitForAudio(c: Context, address: String, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (Checker.check(c, address)?.routed == true) return true
            delay(2_000)
        }
        return false
    }

    private suspend fun fixed(c: Context, address: String) {
        val name = Checker.check(c, address)?.name ?: "Your buds"
        Store.log(c, "Fixed: $name reconnected with audio")
        Notifier.result(c, "$name fixed", "Reconnected, and audio is going to the buds.")
    }
}
