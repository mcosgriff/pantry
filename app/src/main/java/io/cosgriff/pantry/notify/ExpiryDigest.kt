package io.cosgriff.pantry.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cosgriff.pantry.MainActivity
import io.cosgriff.pantry.R
import io.cosgriff.pantry.data.PantryRepository
import io.cosgriff.pantry.data.SettingsRepository
import io.cosgriff.pantry.domain.ExpiryDigest
import io.cosgriff.pantry.domain.PantrySettings
import io.cosgriff.pantry.domain.expiryDigest
import io.cosgriff.pantry.domain.nextDigest
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * The daily expiry digest (#27): one inexact alarm a day at the digest time, then at most one notification
 * counting what expires soon and what has expired, which opens Use soon.
 *
 * An AlarmManager alarm rather than WorkManager: WorkManager's manifest would add RECEIVE_BOOT_COMPLETED,
 * WAKE_LOCK, ACCESS_NETWORK_STATE and FOREGROUND_SERVICE; an inexact alarm needs no permission of its own
 * (not SCHEDULE_EXACT_ALARM), and only RECEIVE_BOOT_COMPLETED to set it again after a restart. The clock is
 * read here, through the injected Clock, as in a ViewModel (#52).
 */

/** Sets, moves or cancels the alarm that sends the digest. */
@Singleton
class DigestScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    /** The next digest for [settings]: an alarm at its time, or none while both alerts are off. */
    fun schedule(settings: PantrySettings) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DigestReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (!settings.digestOn) {
            alarms.cancel(alarm)
            return
        }
        val at = nextDigest(ZonedDateTime.now(clock), settings.dailyDigestAt)
        // Inexact: Android may send it a few minutes late to save battery, which a daily digest can afford.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), alarm)
    }
}

/** Builds and posts the digest notification. */
@Singleton
class DigestNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pantry: PantryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Posts today's digest, or takes yesterday's away when there is nothing to tell. */
    suspend fun post() {
        val prefs = settings.settings().first()
        val manager = NotificationManagerCompat.from(context)
        val digest = expiryDigest(pantry.items().first(), LocalDate.now(clock), prefs)
        if (digest.isEmpty) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!context.mayNotify()) return
        createChannel(context)
        // mayNotify checks POST_NOTIFICATIONS on Android 13 and later, where it exists.
        @SuppressLint("MissingPermission")
        manager.notify(NOTIFICATION_ID, notification(digest))
    }

    private fun notification(digest: ExpiryDigest) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_timer)
        .setContentTitle(context.getString(R.string.notify_title))
        .setContentText(text(digest))
        .setContentIntent(openUseSoon())
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    private fun text(digest: ExpiryDigest): String {
        val res = context.resources
        val parts = listOfNotNull(
            digest.expiringSoon.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.notify_expiring_soon, it, it) },
            digest.expired.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.notify_expired, it, it) },
        )
        return if (parts.size == 2) context.getString(R.string.notify_both, parts[0], parts[1]) else parts.single()
    }

    private fun openUseSoon(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_USE_SOON, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "expiry_digest"
        private const val NOTIFICATION_ID = 27

        /** The one channel, "Expiration alerts", so Android's settings name what the notification is. */
        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = context.getString(R.string.notify_channel_body)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}

/**
 * Whether Android lets the app post notifications: the POST_NOTIFICATIONS permission on Android 13 and later,
 * before that whether notifications are on for the app.
 */
fun Context.mayNotify(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DigestEntryPoint {
    fun scheduler(): DigestScheduler
    fun notifier(): DigestNotifier
    fun settings(): SettingsRepository
}

private fun Context.digest(): DigestEntryPoint = EntryPointAccessors.fromApplication(applicationContext, DigestEntryPoint::class.java)

/** Runs [work] off the main thread, keeping the receiver alive until it ends. */
private fun BroadcastReceiver.inBackground(work: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}

/** The digest alarm: posts today's digest and sets tomorrow's. Not exported; only the app's alarm reaches it. */
class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val digest = context.digest()
        inBackground {
            digest.notifier().post()
            digest.scheduler().schedule(digest.settings().settings().first())
        }
    }
}

/**
 * Sets the alarm again when Android drops or misplaces it: after a restart (Android clears alarms), an app
 * update, or a change of time or time zone (the digest keeps its wall time).
 */
class DigestRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ON) return
        val digest = context.digest()
        inBackground { digest.scheduler().schedule(digest.settings().settings().first()) }
    }

    private companion object {
        val RESCHEDULE_ON = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
