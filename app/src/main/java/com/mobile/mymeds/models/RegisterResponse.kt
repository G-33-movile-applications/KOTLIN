package com.mobile.mymeds.models

import okhttp3.MultipartBody
import okhttp3.RequestBody

data class RegisterResponse(
    val name: RequestBody,
    val email: RequestBody,
    val password: RequestBody,
    val phone_number: RequestBody?,
    val profile_picture: MultipartBody.Part?,
    val id_picture: MultipartBody.Part,
    val address: RequestBody,
    val city: RequestBody,
    val state: RequestBody,
    val zip_code: RequestBody
)
