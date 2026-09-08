package com.getmaincourse.app.features.auth

import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.getmaincourse.app.features.session.GoogleAuthenticationAttempt
import com.getmaincourse.app.features.session.MainCourseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class GoogleAuthenticationLauncher(
    private val activity: ComponentActivity,
    private val viewModel: MainCourseViewModel,
    private val provider: GoogleCredentialProvider = AndroidGoogleCredentialProvider(activity),
) : DefaultLifecycleObserver {
    private var unresolvedAttempt: GoogleAuthenticationAttempt? = null
    private var chooserJob: Job? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    fun launch() {
        if (chooserJob?.isActive == true) return
        val attempt = viewModel.beginGoogleAuthentication() ?: return
        unresolvedAttempt = attempt
        chooserJob = activity.lifecycleScope.launch {
            try {
                val idToken = provider.credential(attempt.nonce)
                if (viewModel.finishGoogleAuthentication(attempt, idToken)) {
                    unresolvedAttempt = null
                } else if (unresolvedAttempt == attempt) {
                    unresolvedAttempt = null
                }
            } catch (_: GoogleSignInCancelledException) {
                cancel(attempt)
            } catch (failure: CancellationException) {
                cancel(attempt)
                throw failure
            } catch (failure: GoogleSignInException) {
                cancel(attempt, failure.message)
            } catch (_: Throwable) {
                cancel(attempt, GOOGLE_UNAVAILABLE_MESSAGE)
            } finally {
                chooserJob = null
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unresolvedAttempt?.let(viewModel::cancelGoogleAuthentication)
        unresolvedAttempt = null
        chooserJob?.cancel()
        chooserJob = null
        activity.lifecycle.removeObserver(this)
    }

    private fun cancel(attempt: GoogleAuthenticationAttempt, error: String? = null) {
        if (unresolvedAttempt == attempt) unresolvedAttempt = null
        viewModel.cancelGoogleAuthentication(attempt, error)
    }

    private companion object {
        const val GOOGLE_UNAVAILABLE_MESSAGE = "Google sign-in is unavailable. Please try again."
    }
}
