package me.capcom.smsgateway.services

import android.content.Context
import android.util.Log
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.workers.RegistrationWorker
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Stub PushService — Firebase removed.
 * Uses local HTTP mode only (Ktor server on device).
 * No FCM dependency needed.
 */
class PushService : KoinComponent {

    companion object : KoinComponent {
        fun register(context: Context) {
            val logger = get<LogsService>()

            logger.insert(
                priority = LogEntry.Priority.INFO,
                module = PushService::class.java.simpleName,
                message = "FCM disabled — local mode only (TopDescoberta)"
            )

            RegistrationWorker.start(context, null, false)
        }
    }
}
