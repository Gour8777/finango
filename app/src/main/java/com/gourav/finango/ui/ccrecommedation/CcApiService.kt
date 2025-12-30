package com.gourav.finango.ui.ccrecommedation

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CcApiService {

    @GET("recommendations/{userId}")
    suspend fun getRecommendations(
        @Path("userId") userId: String,
        @Query("top_k") topK: Int
    ): List<CreditCardRecommendation>
}