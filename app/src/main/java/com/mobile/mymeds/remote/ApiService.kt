package com.mobile.mymeds.remote

import com.mobile.mymeds.models.ApiResponse
import com.mobile.mymeds.models.*

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*



interface ApiService  {
    @Multipart
    @POST("register/")
    suspend fun register(
        @PartMap data: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part profilePicture: MultipartBody.Part?,
        @Part idPicture: MultipartBody.Part?): Response<ApiResponse>

    @POST("login/")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("profile/{email}")
    suspend fun getProfile(@Path("email") userEmail: String): Response<UserProfileResponse>

    @Multipart
    @POST("profile/update")
    suspend fun updateProfile(
        @PartMap data: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part profilePicture: MultipartBody.Part? = null
    ): Response<UserProfileResponse>
}