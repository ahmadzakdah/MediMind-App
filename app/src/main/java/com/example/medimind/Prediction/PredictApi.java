package com.example.medimind.Prediction;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface PredictApi {
    @POST("predict")
    Call<List<PredictResponseItem>> predict(@Body PredictRequest body);
}
