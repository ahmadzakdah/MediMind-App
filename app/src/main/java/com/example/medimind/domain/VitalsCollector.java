package com.example.medimind.domain;

import android.widget.EditText;

import java.util.HashMap;
import java.util.Map;

public class VitalsCollector {

    private final EditText etSysBP, etDiaBP, etHR, etRR, etBloodSugar, etTemp, etSpO2;

    public VitalsCollector(EditText etSysBP, EditText etDiaBP, EditText etHR,
                           EditText etRR, EditText etBloodSugar, EditText etTemp, EditText etSpO2) {
        this.etSysBP = etSysBP;
        this.etDiaBP = etDiaBP;
        this.etHR = etHR;
        this.etRR = etRR;
        this.etBloodSugar = etBloodSugar;
        this.etTemp = etTemp;
        this.etSpO2 = etSpO2;
    }

    // ✅ يرجع بس القيم اللي المستخدم كتبها (للـ Summary)
    public Map<String, Object> collectEnteredOnly() {
        Map<String, Object> map = new HashMap<>();
        putIntIfPresent(map, "bp_sys", etSysBP);
        putIntIfPresent(map, "bp_dia", etDiaBP);
        putIntIfPresent(map, "hr", etHR);
        putIntIfPresent(map, "rr", etRR);
        putIntIfPresent(map, "blood_sugar", etBloodSugar);
        putDoubleIfPresent(map, "temp_c", etTemp);
        putIntIfPresent(map, "spo2", etSpO2);
        return map;
    }

    // ✅ يرجع vitals كاملة للـ Predict (defaults + entered)
    public Map<String, Object> buildForPredict(int age, String genderText, Map<String, Object> enteredOnly) {
        Map<String, Object> vit = new HashMap<>();

        // defaults
        vit.put("Age", age > 0 ? age : 22);
        vit.put("Gender", genderTo01(genderText)); // 1 male / 0 female
        vit.put("bp_sys", 120);
        vit.put("bp_dia", 80);
        vit.put("hr", 80);
        vit.put("rr", 18);
        vit.put("blood_sugar", 100);
        vit.put("temp_c", 37.0);
        vit.put("spo2", 98);

        // override with entered
        if (enteredOnly != null) vit.putAll(enteredOnly);

        return vit;
    }

    private int genderTo01(String g) {
        if (g == null) return 1;
        String x = g.trim().toLowerCase();
        if (x.equals("female") || x.equals("f") || x.equals("0")) return 0;
        return 1; // default male
    }

    private void putIntIfPresent(Map<String, Object> map, String key, EditText et) {
        if (et == null) return;
        try {
            String v = et.getText().toString().trim();
            if (v.isEmpty()) return;
            map.put(key, Integer.parseInt(v));
        } catch (Exception ignored) {}
    }

    private void putDoubleIfPresent(Map<String, Object> map, String key, EditText et) {
        if (et == null) return;
        try {
            String v = et.getText().toString().trim();
            if (v.isEmpty()) return;
            map.put(key, Double.parseDouble(v));
        } catch (Exception ignored) {}
    }
}
