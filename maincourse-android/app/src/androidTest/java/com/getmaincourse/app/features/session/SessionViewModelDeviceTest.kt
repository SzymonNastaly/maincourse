package com.getmaincourse.app.features.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.images.SessionImages
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.SessionEvents
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockWebServer
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
}
