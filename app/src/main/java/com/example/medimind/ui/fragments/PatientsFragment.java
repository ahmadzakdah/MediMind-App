package com.example.medimind.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.ui.PatientInfoActivity;
import com.example.medimind.ui.HelperClasses.PatientsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class PatientsFragment extends Fragment {

    private RecyclerView rvPatients;
    private LinearLayout empty;

    private PatientsAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_patients, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvPatients = view.findViewById(R.id.rvPatientsAll);
        empty = view.findViewById(R.id.layoutEmptyPatients);

        rvPatients.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new PatientsAdapter(p -> {
            Intent i = new Intent(requireContext(), PatientInfoActivity.class);
            i.putExtra("mrn", p.mrn);
            startActivity(i);
        });

        rvPatients.setAdapter(adapter);

        loadPatients();

        return view;
    }

    private void loadPatients() {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(requireContext(), "Please login first.", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }

        db.collection("doctors")
                .document(doctorUid)
                .collection("patients")
                .orderBy("nameLower")
                .limit(200)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        adapter.setItems(new ArrayList<>());
                        showEmpty();
                        return;
                    }

                    List<PatientsAdapter.PatientRow> list = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String mrn = doc.getString("medicalRecordNumber");
                        if (mrn == null || mrn.trim().isEmpty()) mrn = doc.getId();

                        String name = doc.getString("name");
                        String gender = doc.getString("gender");
                        Long ageL = doc.getLong("age");
                        int age = (ageL != null) ? ageL.intValue() : 0;

                        list.add(new PatientsAdapter.PatientRow(
                                mrn,
                                name != null ? name : ("MRN " + mrn),
                                gender != null ? gender : "—",
                                age
                        ));
                    }

                    adapter.setItems(list);
                    showList();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showEmpty();
                });
    }

    private void showEmpty() {
        empty.setVisibility(View.VISIBLE);
        rvPatients.setVisibility(View.GONE);
    }

    private void showList() {
        empty.setVisibility(View.GONE);
        rvPatients.setVisibility(View.VISIBLE);
    }
}
