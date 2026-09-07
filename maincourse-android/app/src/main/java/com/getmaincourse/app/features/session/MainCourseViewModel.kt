package com.getmaincourse.app.features.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.session.SessionStore
import java.time.Clock

class MainCourseViewModel(
    api: MainCourseApi,
    sessionStore: SessionStore,
    catalogRepository: CatalogRepository,
    baseUrl: String,
    clock: Clock,
    imageCleanup: suspend () -> Unit,
) : ViewModel() {
    private val controller = SessionController(
        api = api,
        sessionStore = sessionStore,
        catalogRepository = catalogRepository,
        baseUrl = baseUrl,
        clock = clock,
        scope = viewModelScope,
        imageCleanup = imageCleanup,
    )

    val state = controller.state

    fun restore() = controller.restore()
    fun signIn(request: SignInRequest) = controller.signIn(request)
    fun signUp(request: SignUpRequest) = controller.signUp(request)
    fun switchCookbook(id: Long) = controller.switchCookbook(id)
    fun refresh() = controller.refresh()
    fun openRecipe(id: Long) = controller.openRecipe(id)
    fun closeRecipe() = controller.closeRecipe()
    fun logout() = controller.logout()
    fun reset() = controller.reset()
    fun checkExpiry() = controller.checkExpiry()
}
