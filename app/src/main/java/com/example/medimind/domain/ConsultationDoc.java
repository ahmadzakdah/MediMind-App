package com.example.medimind.domain;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsultationDoc {
    public String id;
    public long createdAtMillis;

    public int age;
    public String gender;

    public String topDisease;
    public String notes;

    public List<String> symptoms = new ArrayList<>();
    public List<Map<String, Object>> predictedDiseases = new ArrayList<>();

    public Map<String, Object> vitals = new HashMap<>();

    public static ConsultationDoc from(DocumentSnapshot d) {
        ConsultationDoc x = new ConsultationDoc();
        x.id = d.getId();

        Timestamp ts = d.getTimestamp("createdAt");
        x.createdAtMillis = (ts != null) ? ts.toDate().getTime() : 0;

        Long ageL = d.getLong("age");
        x.age = (ageL == null) ? 0 : ageL.intValue();

        x.gender = d.getString("gender");
        x.topDisease = d.getString("topDisease");
        x.notes = d.getString("notes");

        try {
            List<String> s = (List<String>) d.get("symptoms");
            if (s != null) x.symptoms = s;
        } catch (Exception ignored) {}

        try {
            List<Map<String, Object>> p = (List<Map<String, Object>>) d.get("predictedDiseases");
            if (p != null) x.predictedDiseases = p;
        } catch (Exception ignored) {}

        try {
            Map<String, Object> v = (Map<String, Object>) d.get("vitals");
            if (v != null) x.vitals = v;
        } catch (Exception ignored) {}

        return x;
    }
}
