package com.example.medimind.Suggestion;

public class DiseaseProb {
    public String disease;
    public double prob; // خليه 0..100 أو 0..1، السيرفر بيفهم الاثنين

    public DiseaseProb(String disease, double prob) {
        this.disease = disease;
        this.prob = prob;
    }
}
