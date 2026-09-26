package com.getmaincourse.app

import android.app.Application
import android.util.Log
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.ShoppingListRepository
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.images.SessionImages
import com.getmaincourse.app.data.network.ApiCallFactory
import com.getmaincourse.app.data.network.ApiErrorCallAdapterFactory
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.SessionEvents
import com.getmaincourse.app.data.onboarding.OnboardingPreferences
import com.getmaincourse.app.data.session.EncryptedSessionStore
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.features.session.SessionViewModel
import com.getmaincourse.app.features.auth.PreAuthViewModel
import com.getmaincourse.app.notifications.NotificationPresenter
import com.getmaincourse.app.notifications.PushRegistrationManager
import com.getmaincourse.app.notifications.PushRegistrationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MainCourseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationPresenter.createChannels(this)
    }
}

class AppContainer(application: Application) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = MainCourseDatabase.open(application)
    val sessionStore = EncryptedSessionStore(application)
    val onboardingPreferences = OnboardingPreferences(application)
    val sessionProvider = SessionProvider()
    val sessionEvents = SessionEvents()
    private val authenticatedClient = ApiCallFactory(
        sessionProvider,
        sessionEvents,
        logFailure = { Log.w("MainCourseNetwork", it) },
    )
    private val json = Json { ignoreUnknownKeys = true }
    val service: MainCourseService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .callFactory(authenticatedClient)
        .addCallAdapterFactory(ApiErrorCallAdapterFactory { resource, arguments -> application.getString(resource, *arguments) })
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MainCourseService::class.java)
    val pushRegistrationManager = PushRegistrationManager(
        context = application,
        service = service,
        sessionProvider = sessionProvider,
        store = PushRegistrationStore(application),
        applicationScope = applicationScope,
    )
    val cookbookRepository = CookbookRepository(database, service, json)
    val recipeRepository = RecipeRepository(database, service, json)
    val shoppingListRepository = ShoppingListRepository(database, service, json)
    val images = SessionImages(application, BuildConfig.API_BASE_URL)
    val sessionViewModelFactory = simpleViewModelFactory {
        SessionViewModel(
            service = service,
            sessionStore = sessionStore,
            sessionProvider = sessionProvider,
            sessionEvents = sessionEvents,
            database = database,
            images = images,
            onboardingDeviceId = onboardingPreferences::pendingDeviceId,
            clearOnboardingDeviceId = onboardingPreferences::clearPendingDeviceId,
            synchronizePush = pushRegistrationManager::synchronizeIfAllowed,
            unregisterPush = pushRegistrationManager::unregisterCurrent,
            invalidatePush = pushRegistrationManager::invalidateLocalToken,
            baseUrl = BuildConfig.API_BASE_URL,
        )
    }
    val preAuthViewModelFactory = simpleViewModelFactory {
        PreAuthViewModel(
            preferences = onboardingPreferences,
            submitOnboarding = { service.submitOnboarding(it) },
        )
    }
}
