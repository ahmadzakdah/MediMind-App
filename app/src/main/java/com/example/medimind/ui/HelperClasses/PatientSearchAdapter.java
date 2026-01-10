package com.example.medimind.ui.HelperClasses;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;

import java.util.ArrayList;
import java.util.List;

public class PatientSearchAdapter extends RecyclerView.Adapter<PatientSearchAdapter.VH> {

    public static class PatientRow {
        public String mrn;
        public String name;
        public String gender;
        public int age;

        public PatientRow(String mrn, String name, String gender, int age) {
            this.mrn = mrn;
            this.name = name;
            this.gender = gender;
            this.age = age;
        }
    }

    public interface OnPatientClickListener {
        void onPatientClick(PatientRow patient);
    }

    private final List<PatientRow> items = new ArrayList<>();
    private final OnPatientClickListener listener;

    public PatientSearchAdapter(OnPatientClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<PatientRow> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_patient, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PatientRow p = items.get(position);

        h.tvName.setText(p.name != null ? p.name : ("MRN " + p.mrn));
        String g = (p.gender != null && !p.gender.trim().isEmpty()) ? p.gender : "—";
        h.tvInfo.setText(g + " • " + p.age + " years • MRN " + p.mrn);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPatientClick(p);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvInfo;
        ImageView img;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPatientName);
            tvInfo = itemView.findViewById(R.id.tvPatientInfo);
            img = itemView.findViewById(R.id.imgPatient);
        }
    }
}
