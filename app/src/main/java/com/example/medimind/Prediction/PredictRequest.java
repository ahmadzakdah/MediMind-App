package com.example.medimind.Prediction;

import java.util.List;
import java.util.Map;

public class PredictRequest {
    public List<String> symptoms;
    public Map<String, Object> vitals;

    public PredictRequest(List<String> symptoms, Map<String, Object> vitals) {
        this.symptoms = symptoms;
        this.vitals = vitals;
    }
}
