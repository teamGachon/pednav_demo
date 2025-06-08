package com.example.modeltest;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface NaverMapAPI {
    @GET("v1/driving")
    Call<ResultPath> getPath(
            @Header("X-NCP-APIGW-API-KEY-ID") String apiKeyID,
            @Header("X-NCP-APIGW-API-KEY") String apiKey,
            @Query("start") String start,
            @Query("goal") String goal
    );
}