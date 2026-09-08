package com.getmaincourse.app.data.network

class ApiFailure(
    val status: Int?,
    message: String,
    val errorCode: String? = null,
    val limit: Int? = null,
) : Exception(message)
