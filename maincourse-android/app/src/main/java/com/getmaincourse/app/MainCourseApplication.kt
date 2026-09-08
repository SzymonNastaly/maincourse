package com.getmaincourse.app

import android.app.Application
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.RoomCatalogStore
import com.getmaincourse.app.data.images.RecipeImageOwner
import com.getmaincourse.app.data.images.SessionImages
import com.getmaincourse.app.data.images.clearImageResources
import com.getmaincourse.app.data.network.RetrofitMainCourseApi
import com.getmaincourse.app.data.onboarding.AtomicOnboardingStore
import com.getmaincourse.app.data.session.EncryptedSessionStore
import com.getmaincourse.app.features.session.CatalogRepository
import com.getmaincourse.app.features.session.MainCourseViewModel
import com.getmaincourse.app.features.auth.GoogleCredentialSessionCleaner
import java.time.Clock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class MainCourseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    private val database = MainCourseDatabase.open(application)
    private val sessionStore = EncryptedSessionStore(application)
    private val api = RetrofitMainCourseApi(BuildConfig.API_BASE_URL.toHttpUrl(), OkHttpClient())
    private val onboardingStore = AtomicOnboardingStore(application, BuildConfig.API_BASE_URL)
    private val googleCredentialSessionCleaner = GoogleCredentialSessionCleaner(application)
    private val catalogRepository = CatalogRepository(
        api = api,
        store = RoomCatalogStore(database),
    )
    val images = SessionImages(application, BuildConfig.API_BASE_URL)
    private val recipeImages = RecipeImageOwner(application)

    val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainCourseViewModel::class.java))
            return MainCourseViewModel(
                api = api,
                sessionStore = sessionStore,
                catalogRepository = catalogRepository,
                onboardingStore = onboardingStore,
                baseUrl = BuildConfig.API_BASE_URL,
                clock = Clock.systemUTC(),
                imageCleanup = {
                    clearImageResources(
                        clearDisplayImages = images::clear,
                        clearStagedImages = recipeImages::clear,
                    )
                },
                resolvePreparedImage = recipeImages::resolve,
                prepareRecipeImage = { userId, uri -> recipeImages.prepare(userId, uri.toUri()) },
                discardRecipeImage = recipeImages::discard,
                credentialStateCleanup = googleCredentialSessionCleaner::clear,
            ) as T
        }
    }
}
