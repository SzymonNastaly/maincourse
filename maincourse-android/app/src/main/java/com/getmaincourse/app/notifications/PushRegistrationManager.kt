package com.getmaincourse.app.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.getmaincourse.app.data.model.DeviceTokenRequest
import com.getmaincourse.app.data.model.NotificationOpenedRequest
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.session.SessionProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PushRegistrationManager(
    private val context: Context,
    private val service: MainCourseService,
    private val sessionProvider: SessionProvider,
    private val store: PushRegistrationStore,
    private val applicationScope: CoroutineScope,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun notificationsEnabled(): Boolean {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimePermissionGranted && notificationManager.areNotificationsEnabled()
    }

    fun canAskPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !notificationsEnabled() &&
            !store.permissionAsked

    fun markPermissionAsked() {
        store.permissionAsked = true
    }

    fun handleRegistered(installationId: String) {
        if (store.currentToken != installationId) {
            store.currentToken = installationId
            store.uploadedToken = null
            store.uploadedAtMillis = 0L
        }
        applicationScope.launch { synchronizeIfAllowed() }
    }

    suspend fun synchronizeIfAllowed() {
        if (!firebaseConfigured() || !notificationsEnabled() || sessionProvider.session.value == null) return

        try {
            FirebaseMessaging.getInstance().register().await()
            val installationId = FirebaseInstallations.getInstance().id.await()
            store.currentToken = installationId
            if (!uploadDue(installationId)) return

            service.registerDeviceToken(
                DeviceTokenRequest(
                    token = installationId,
                    provider = PROVIDER,
                    environment = "production",
                    timeZone = ZoneId.systemDefault().id,
                ),
            )
            store.uploadedToken = installationId
            store.uploadedAtMillis = clock.millis()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // Registration is best effort and will be retried on a later app start/resume.
        }
    }

    suspend fun unregisterCurrent() {
        val token = store.uploadedToken ?: store.currentToken
        if (token != null && sessionProvider.session.value != null) {
            try {
                service.deleteDeviceToken(token, PROVIDER)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Invalidating the FCM token below prevents further delivery even if Rails is unreachable.
            }
        }
        invalidateLocalToken()
    }

    suspend fun invalidateLocalToken() {
        if (firebaseConfigured()) {
            try {
                FirebaseMessaging.getInstance().unregister().await()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Local cleanup must continue even when Firebase is unavailable.
            }
        }
        store.clearRegistration()
    }

    fun markOpened(deliveryId: Long) {
        applicationScope.launch {
            try {
                service.markNotificationOpened(deliveryId, NotificationOpenedRequest())
            } catch (_: Throwable) {
                // Analytics must never block notification navigation.
            }
        }
    }

    private fun uploadDue(token: String): Boolean {
        if (store.uploadedToken != token) return true
        val age = Duration.ofMillis((clock.millis() - store.uploadedAtMillis).coerceAtLeast(0L))
        return age >= REGISTRATION_HEARTBEAT
    }

    private fun firebaseConfigured(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            when {
                task.isSuccessful -> continuation.resume(task.result)
                task.isCanceled -> continuation.cancel()
                else -> continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase task failed"))
            }
        }
    }

    private companion object {
        const val PROVIDER = "fcm"
        val REGISTRATION_HEARTBEAT: Duration = Duration.ofDays(30)
    }
}
