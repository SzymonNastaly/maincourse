package com.getmaincourse.app.features.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.cache.CookbookEntity
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.RecipeEntity
import com.getmaincourse.app.data.cache.SelectedCookbookEntity
import com.getmaincourse.app.data.images.SessionImages
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.SessionEvents
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(AndroidJUnit4::class)
class SessionViewModelDeviceTest {
    @Test
    fun missingSessionClearsRealRoomDatabaseAndShowsAuthentication() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, MainCourseDatabase::class.java).build()
        val server = MockWebServer().apply { start() }
        try {
            val dao = database.catalogDao()
            dao.upsertCookbooks(
                listOf(CookbookEntity(USER_ID, COOKBOOK_ID, listPosition = 0, cookbookJson = "{}")),
            )
            dao.selectCookbook(SelectedCookbookEntity(USER_ID, COOKBOOK_ID))
            dao.upsertRecipes(
                listOf(RecipeEntity(USER_ID, COOKBOOK_ID, RECIPE_ID, listPosition = 0, summaryJson = "{}")),
            )
            assertTrue(dao.observeCookbooks(USER_ID).first().isNotEmpty())
            assertTrue(dao.observeRecipes(USER_ID, COOKBOOK_ID).first().isNotEmpty())
            val service = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(MainCourseService::class.java)
            val viewModel = SessionViewModel(
                service = service,
                sessionStore = EmptySessionStore,
                sessionProvider = SessionProvider(),
                sessionEvents = SessionEvents(),
                database = database,
                images = SessionImages(context, server.url("/").toString()),
                baseUrl = server.url("/").toString(),
            )

            viewModel.restore().join()

            assertTrue(viewModel.state.value is SessionUiState.SignedOut)
            assertTrue(dao.observeCookbooks(USER_ID).first().isEmpty())
            assertTrue(dao.observeRecipes(USER_ID, COOKBOOK_ID).first().isEmpty())
            assertNull(dao.selectedCookbookId(USER_ID))
        } finally {
            database.close()
            server.shutdown()
        }
    }

    private object EmptySessionStore : SessionStore {
        override suspend fun read(): StoredSession? = null
        override suspend fun write(session: StoredSession) = Unit
        override suspend fun clear() = Unit
    }

    private companion object {
        const val USER_ID = 101L
        const val COOKBOOK_ID = 202L
        const val RECIPE_ID = 303L
    }
}
