package com.example.medimind.data;

import android.content.Context;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SymptomRepository {

    public static List<String> loadFromAssets(Context ctx, String fileName) {
        ArrayList<String> out = new ArrayList<>();
        BufferedReader br = null;

        try {
            br = new BufferedReader(new InputStreamReader(ctx.getAssets().open(fileName)));

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            JSONArray arr = new JSONArray(sb.toString());
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.getString(i);
                if (s != null && !s.trim().isEmpty()) {
                    out.add(s.trim().toLowerCase());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try { if (br != null) br.close(); } catch (Exception ignored) {}
        }

        Collections.sort(out);
        return out;
    }
}
