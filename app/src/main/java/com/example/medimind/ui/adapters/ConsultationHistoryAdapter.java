package com.example.medimind.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.domain.ConsultationDoc;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ConsultationHistoryAdapter extends RecyclerView.Adapter<ConsultationHistoryAdapter.VH> {

    private final List<ConsultationDoc> items;

    public ConsultationHistoryAdapter(List<ConsultationDoc> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_consultation_full, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ConsultationDoc x = items.get(pos);

        String dateTxt = (x.createdAtMillis > 0)
                ? new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(new Date(x.createdAtMillis))
                : "—";

        h.tvDate.setText(dateTxt);
        h.tvTopDisease.setText((x.topDisease == null || x.topDisease.trim().isEmpty()) ? "—" : x.topDisease);

        h.tvAge.setText(x.age > 0 ? (x.age + " years") : "—");
        h.tvGender.setText((x.gender == null || x.gender.trim().isEmpty()) ? "—" : x.gender);

        h.tvNotes.setText((x.notes == null || x.notes.trim().isEmpty()) ? "—" : x.notes);

        h.tvSymptoms.setText(joinSymptoms(x.symptoms));
        h.tvPredicted.setText(joinPredicted(x.predictedDiseases));
        h.tvVitals.setText(joinVitals(x.vitals));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvTopDisease, tvAge, tvGender, tvSymptoms, tvVitals, tvPredicted, tvNotes;
        VH(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDate);
            tvTopDisease = v.findViewById(R.id.tvTopDisease);
            tvAge = v.findViewById(R.id.tvAge);
            tvGender = v.findViewById(R.id.tvGender);
            tvSymptoms = v.findViewById(R.id.tvSymptoms);
            tvVitals = v.findViewById(R.id.tvVitals);
            tvPredicted = v.findViewById(R.id.tvPredicted);
            tvNotes = v.findViewById(R.id.tvNotes);
        }
    }

    private String joinSymptoms(List<String> list) {
        if (list == null || list.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append("• ").append(com.example.medimind.ui.adapters.SymptomSearchAdapter.pretty(s)).append("\n");
        }
        return sb.toString().trim();
    }

    private String joinPredicted(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : list) {
            String name = (m.get("name") == null) ? "" : String.valueOf(m.get("name")).trim();
            String prob = (m.get("prob") == null) ? "" : String.valueOf(m.get("prob")).trim();
            if (name.isEmpty()) continue;
            sb.append("• ").append(name);
            if (!prob.isEmpty()) sb.append("  (").append(prob).append(")");
            sb.append("\n");
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "—" : out;
    }

    private String joinVitals(Map<String, Object> vit) {
        if (vit == null || vit.isEmpty()) return "—";

        StringBuilder sb = new StringBuilder();

        String sys = v(vit, "bp_sys");
        String dia = v(vit, "bp_dia");
        if (!sys.equals("—") || !dia.equals("—")) {
            String bp = (sys.equals("—") && dia.equals("—")) ? "—" : (sys + "/" + dia + " mmHg");
            sb.append("• BP: ").append(bp).append("\n");
        }

        addLine(sb, "HR", v(vit, "hr"), "bpm");
        addLine(sb, "RR", v(vit, "rr"), "/min");
        addLine(sb, "SpO2", v(vit, "spo2"), "%");
        addLine(sb, "Sugar", v(vit, "blood_sugar"), "mg/dL");
        addLine(sb, "Temp", v(vit, "temp_c"), "°C");

        String out = sb.toString().trim();
        return out.isEmpty() ? "—" : out;
    }

    private void addLine(StringBuilder sb, String label, String val, String unit) {
        if (val == null || val.trim().isEmpty() || val.equals("—")) return;
        sb.append("• ").append(label).append(": ").append(val).append(" ").append(unit).append("\n");
    }

    private String v(Map<String, Object> vit, String key) {
        Object o = vit.get(key);
        if (o == null) return "—";
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? "—" : s;
    }
}
