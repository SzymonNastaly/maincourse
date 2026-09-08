package com.getmaincourse.app.features.auth

object GoogleSignInConfiguration {
    // Public Rails-verified audience shared with GOOGLE_SERVER_CLIENT_ID in
    // hauptgang-ios/project.yml and the Rails google.client_id credential.
    // This is not the separate package/signature-bound Android OAuth client ID.
    const val SERVER_CLIENT_ID =
        "1048887933015-tga3ld77ufb2jfgugh5b5ddgo854uoto.apps.googleusercontent.com"
}
