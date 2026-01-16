package com.example.medimind.Suggestion;

import java.util.List;

public class SuggestRequest {
    public List<String> selectedSymptoms;
    public List<DiseaseProb> topDiseases; // optional
    public int limit;

    public SuggestRequest(List<String> selectedSymptoms, List<DiseaseProb> topDiseases, int limit) {
        this.selectedSymptoms = selectedSymptoms;
        this.topDiseases = topDiseases;
        this.limit = limit;
    }
}
