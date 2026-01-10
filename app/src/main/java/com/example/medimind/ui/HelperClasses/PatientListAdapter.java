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

public class PatientListAdapter extends RecyclerView.Adapter<PatientListAdapter.VH> {

    public static class PatientRow {
        public final String mrn;
        public final String name;
        public final String gender;
        public final int age;

        public PatientRow(String mrn, String name, String gender, int age) {
            this.mrn = mrn;
            this.name = name;
            this.gender = gender;
            this.age = age;
        }
    }

    public interface OnPatientClick {
        void onClick(PatientRow patient);
    }

    private final OnPatientClick onClick;
    private final List<PatientRow> items = new ArrayList<>();

    public PatientListAdapter(OnPatientClick onClick) {
        this.onClick = onClick;
    }

    public void setItems(List<PatientRow> list) {
        items.clear();
        if (list != null) items.addAll(list);
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

        h.tvPatientName.setText(p.name);
        h.tvPatientInfo.setText(
                p.gender + " • " + p.age + " years • MRN " + p.mrn
        );

// لو بدك تغير الأيقونة حسب الجنس (اختياري)
        if ("female".equalsIgnoreCase(p.gender)) {
            h.imgPatient.setImageResource(R.drawable.ic_female);
        } else {
            h.imgPatient.setImageResource(R.drawable.ic_male);
        }


        h.itemView.setOnClickListener(v -> onClick.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class VH extends RecyclerView.ViewHolder {

        ImageView imgPatient;
        TextView tvPatientName, tvPatientInfo;

        VH(@NonNull View itemView) {
            super(itemView);
            imgPatient = itemView.findViewById(R.id.imgPatient);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvPatientInfo = itemView.findViewById(R.id.tvPatientInfo);
        }
    }

}
