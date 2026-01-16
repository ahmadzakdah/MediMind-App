package com.example.medimind.network;

import android.os.Handler;
import android.os.Looper;

import com.example.medimind.Suggestion.DiseaseProb;
import com.example.medimind.Suggestion.SuggestApi;
import com.example.medimind.Suggestion.SuggestRequest;

import java.util.ArrayList;
import java.util.List;

import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;

public class SuggestionManager {

    public interface Listener {
        void onSuccess(List<String> suggestions);
        void onError(String message);
    }

    private final SuggestApi api;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private Call<List<String>> inFlight;

    public SuggestionManager(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(SuggestApi.class);
    }

    public void scheduleSuggest(List<String> selectedSymptoms,
                                List<DiseaseProb> topDiseases,
                                int limit,
                                long debounceMs,
                                Listener listener) {

        cancel();
        runnable = () -> doSuggest(selectedSymptoms, topDiseases, limit, listener);
        handler.postDelayed(runnable, debounceMs);
    }

    public void cancel() {
        if (runnable != null) handler.removeCallbacks(runnable);
        if (inFlight != null) inFlight.cancel();
    }

    private void doSuggest(List<String> selectedSymptoms,
                           List<DiseaseProb> topDiseases,
                           int limit,
                           Listener listener) {

        if (selectedSymptoms == null || selectedSymptoms.size() < 2) {
            if (listener != null) listener.onSuccess(new ArrayList<>());
            return;
        }

        SuggestRequest body = new SuggestRequest(
                new ArrayList<>(selectedSymptoms),
                topDiseases,
                Math.max(1, Math.min(limit, 50))
        );

        inFlight = api.suggest(body);
        inFlight.enqueue(new Callback<List<String>>() {
            @Override public void onResponse(Call<List<String>> call, Response<List<String>> response) {
                if (listener == null) return;

                if (!response.isSuccessful() || response.body() == null) {
                    listener.onError("Suggest failed (" + response.code() + ")");
                    return;
                }
                listener.onSuccess(response.body());
            }

            @Override public void onFailure(Call<List<String>> call, Throwable t) {
                if (listener == null) return;
                if (call.isCanceled()) return;
                listener.onError("Network error: " + t.getMessage());
            }
        });
    }
}
