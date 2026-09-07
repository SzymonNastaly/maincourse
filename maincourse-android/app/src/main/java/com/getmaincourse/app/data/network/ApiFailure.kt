package com.getmaincourse.app.data.network

class ApiFailure(
    val status: Int?,
    message: String,
) : Exception(message)
