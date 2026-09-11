package com.getmaincourse.app

import android.app.Application
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.ShoppingListRepository
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.images.SessionImages
import com.getmaincourse.app.data.network.AuthInterceptor
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.SessionEvents
import com.getmaincourse.app.data.session.EncryptedSessionStore
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.features.session.SessionViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class MainCourseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(application: Application) {
    val database = MainCourseDatabase.open(application)
    val sessionStore = EncryptedSessionStore(application)
    val sessionProvider = SessionProvider()
    val sessionEvents = SessionEvents()
    private val authenticatedClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .addInterceptor(AuthInterceptor(sessionProvider, sessionEvents))
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    val service: MainCourseService = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(authenticatedClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MainCourseService::class.java)
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
            baseUrl = BuildConfig.API_BASE_URL,
        )
    }
}
