package com.example.medimind.ui.HelperClasses;

import java.util.ArrayList;
import java.util.HashMap;

public class PredictionSummaryPayload {
    public ArrayList<String> symptoms = new ArrayList<>();

    public String disease1;
    public String prob1;

    public String disease2;
    public String prob2;

    public String disease3;
    public String prob3;
    public HashMap<String, Object> vitals = new HashMap<>();
    public int age;
    public String gender;
}
