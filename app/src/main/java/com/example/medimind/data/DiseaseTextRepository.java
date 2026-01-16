package com.example.medimind.data;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DiseaseTextRepository {

    // ملفاتك غالباً Windows-1252 (نفس Latin1 تقريباً)
    private static final Charset CSV_CHARSET = Charset.forName("windows-1252");

    private static Map<String, String> descMap;
    private static Map<String, String> instMap;

    private static String key(String disease) {
        return disease == null ? "" : disease.trim().toLowerCase(Locale.ROOT);
    }

    public static Map<String, String> loadDescriptions(Context ctx, String assetFileName) {
        if (descMap != null) return descMap;

        descMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open(assetFileName), CSV_CHARSET)
        )) {
            // header
            String line = br.readLine();
            if (line == null) return descMap;

            while ((line = br.readLine()) != null) {
                // CSV بسيط: Disease,Description
                // الوصف ممكن يحتوي فاصلات -> لذلك نستخدم split أول فاصلة فقط
                int firstComma = line.indexOf(',');
                if (firstComma <= 0) continue;

                String disease = stripQuotes(line.substring(0, firstComma));
                String desc = stripQuotes(line.substring(firstComma + 1));

                descMap.put(key(disease), desc);
            }
        } catch (Exception ignored) {}

        return descMap;
    }

    public static Map<String, String> loadInstructions(Context ctx, String assetFileName) {
        if (instMap != null) return instMap;

        instMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(ctx.getAssets().open(assetFileName), CSV_CHARSET)
        )) {
            String header = br.readLine();
            if (header == null) return instMap;

            String line;
            while ((line = br.readLine()) != null) {
                // Disease,Inst_1,Inst_2,Inst_3
                // هنا الأفضل نقسم مع احترام الاقتباسات. رح نستخدم parser بسيط.
                String[] cols = splitCsvLine(line);
                if (cols.length < 2) continue;

                String disease = stripQuotes(cols[0]);
                String i1 = cols.length > 1 ? stripQuotes(cols[1]) : "";
                String i2 = cols.length > 2 ? stripQuotes(cols[2]) : "";
                String i3 = cols.length > 3 ? stripQuotes(cols[3]) : "";

                String merged = mergeInstructions(i1, i2, i3);
                instMap.put(key(disease), merged);
            }
        } catch (Exception ignored) {}

        return instMap;
    }

    public static String getDescription(Context ctx, String disease) {
        loadDescriptions(ctx, "Diseases_Description.csv");
        String out = descMap.get(key(disease));
        return (out == null || out.trim().isEmpty()) ? "—" : out;
    }

    public static String getInstructions(Context ctx, String disease) {
        loadInstructions(ctx, "Diseases_Instructions.csv");
        String out = instMap.get(key(disease));
        return (out == null || out.trim().isEmpty()) ? "—" : out;
    }

    private static String mergeInstructions(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        appendIfNotEmpty(sb, a);
        appendIfNotEmpty(sb, b);
        appendIfNotEmpty(sb, c);
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private static void appendIfNotEmpty(StringBuilder sb, String s) {
        if (s == null) return;
        s = s.trim();
        if (s.isEmpty()) return;
        if (sb.length() > 0) sb.append("\n\n");
        sb.append("• ").append(s);
    }

    private static String stripQuotes(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    // CSV splitter بسيط يدعم الاقتباسات
    private static String[] splitCsvLine(String line) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                cur.append(ch);
            } else if (ch == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
