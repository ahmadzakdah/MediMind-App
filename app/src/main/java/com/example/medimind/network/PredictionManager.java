package com.example.medimind.network;

import android.os.Handler;
import android.os.Looper;

import com.example.medimind.Prediction.PredictApi;
import com.example.medimind.Prediction.PredictRequest;
import com.example.medimind.Prediction.PredictResponseItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PredictionManager {

    public interface Listener {
        void onLoading();
        void onSuccess(List<PredictResponseItem> items);
        void onError(String message);
    }

    private final PredictApi api;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    private Call<List<PredictResponseItem>> inFlight;

    public PredictionManager(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl) // لازم ينتهي بـ /
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(PredictApi.class);
    }

    public void schedulePredict(List<String> symptoms, Map<String, Object> vitals, long debounceMs, Listener listener) {
        cancel();
        runnable = () -> doPredict(symptoms, vitals, listener);
        handler.postDelayed(runnable, debounceMs);
    }

    public void cancel() {
        if (runnable != null) handler.removeCallbacks(runnable);
        if (inFlight != null) inFlight.cancel();
    }

    private void doPredict(List<String> symptoms, Map<String, Object> vitals, Listener listener) {
        if (symptoms == null || symptoms.size() < 2) return;
        if (vitals == null) {
            if (listener != null) listener.onError("Vitals map is null");
            return;
        }

        //  Required vitals columns (match server)
        putDefault(vitals, "Age", 22);
        putDefault(vitals, "Gender", 1);
        putDefault(vitals, "bp_sys", 120);
        putDefault(vitals, "bp_dia", 80);
        putDefault(vitals, "hr", 80);
        putDefault(vitals, "rr", 18);
        putDefault(vitals, "blood_sugar", 100);
        putDefault(vitals, "temp_c", 37.0);
        putDefault(vitals, "spo2", 98);

        //  Ensure numeric types for Age/Gender at least
        Object age = vitals.get("Age");
        if (!(age instanceof Number)) vitals.put("Age", 22);

        Object gender = vitals.get("Gender");
        if (!(gender instanceof Number)) vitals.put("Gender", 1);

        if (listener != null) listener.onLoading();

        PredictRequest body = new PredictRequest(new ArrayList<>(symptoms), vitals);

        inFlight = api.predict(body);
        inFlight.enqueue(new Callback<List<PredictResponseItem>>() {
            @Override
            public void onResponse(Call<List<PredictResponseItem>> call, Response<List<PredictResponseItem>> response) {
                if (listener == null) return;

                if (!response.isSuccessful() || response.body() == null) {
                    String msg = "Prediction failed (" + response.code() + ")";
                    try {
                        if (response.errorBody() != null) msg += " - " + response.errorBody().string();
                    } catch (Exception ignored) {}
                    listener.onError(msg);
                    return;
                }
                listener.onSuccess(response.body());
            }

            @Override
            public void onFailure(Call<List<PredictResponseItem>> call, Throwable t) {
                if (listener == null) return;
                if (call.isCanceled()) return;
                listener.onError("Network error: " + t.getMessage());
            }
        });
    }

    private void putDefault(Map<String, Object> vitals, String key, Object value) {
        if (!vitals.containsKey(key) || vitals.get(key) == null) {
            vitals.put(key, value);
        }
    }
}
