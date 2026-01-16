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

public class PatientsAdapter extends RecyclerView.Adapter<PatientsAdapter.VH> {

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

    public interface OnArchiveClick {
        void onArchive(PatientRow patient);
    }

    private final OnPatientClick onClick;
    private final OnArchiveClick onArchive;
    private final List<PatientRow> items = new ArrayList<>();

    public PatientsAdapter(OnPatientClick onClick, OnArchiveClick onArchive) {
        this.onClick = onClick;
        this.onArchive = onArchive;
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
        h.tvPatientInfo.setText(p.gender + " • " + p.age + " years • MRN " + p.mrn);

        if ("Female".equalsIgnoreCase(p.gender))
            h.imgPatient.setImageResource(R.drawable.ic_female);
        else
            h.imgPatient.setImageResource(R.drawable.ic_male);

        // open patient
        h.itemView.setOnClickListener(v -> onClick.onClick(p));

        // archive button (حماية بسيطة)
        if (h.btnArchive != null) {
            h.btnArchive.setOnClickListener(v -> onArchive.onArchive(p));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        ImageView imgPatient, btnArchive;
        TextView tvPatientName, tvPatientInfo;

        VH(@NonNull View itemView) {
            super(itemView);
            imgPatient = itemView.findViewById(R.id.imgPatient);
            tvPatientName = itemView.findViewById(R.id.tvPatientName);
            tvPatientInfo = itemView.findViewById(R.id.tvPatientInfo);
            btnArchive = itemView.findViewById(R.id.btnArchive);
        }
    }
}
