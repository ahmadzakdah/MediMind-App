package com.example.medimind.Suggestion;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SuggestApi {
    @POST("suggest")
    Call<List<String>> suggest(@Body SuggestRequest body);
}
