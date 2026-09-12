package com.getmaincourse.app.notifications

import android.annotation.SuppressLint
import com.getmaincourse.app.MainCourseApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Android lint has not yet learned the FID-based onRegistered replacement for onNewToken.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class MainCourseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        (application as MainCourseApplication).container.pushRegistrationManager.handleRegistered(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        NotificationPresenter.show(
            context = this,
            title = notification.title.orEmpty(),
            body = notification.body.orEmpty(),
            data = message.data,
        )
    }
}
