package com.example.medimind.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.ui.CreateMedicalRecordActivity;
import com.example.medimind.ui.PatientInfoActivity;
import com.example.medimind.ui.HelperClasses.PatientSearchAdapter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private TextInputLayout tilSearch;
    private TextInputEditText etSearch;
    private Button btnSearch, btnCreateRecord;

    private LinearLayout layoutEmptyState;
    private RecyclerView rvPatients;
    private PatientSearchAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private void ensureStatsDoc() {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        HashMap<String, Object> init = new HashMap<>();
        init.put("patientsCount", 0L);
        init.put("consultationsCount", 0L);

        db.collection("doctors")
                .document(uid)
                .collection("meta")
                .document("stats")
                .set(init, com.google.firebase.firestore.SetOptions.merge());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Firebase
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        ensureStatsDoc();

        // Bind (لاحظ: view.findViewById)
        tilSearch = view.findViewById(R.id.tilSearch);
        etSearch = view.findViewById(R.id.etSearch);

        btnSearch = view.findViewById(R.id.btnSearch);
        btnCreateRecord = view.findViewById(R.id.btnCreateRecord);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);

        rvPatients = view.findViewById(R.id.rvPatients);
        rvPatients.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new PatientSearchAdapter(patient -> {
            Intent i = new Intent(requireContext(), PatientInfoActivity.class);
            i.putExtra("mrn", patient.mrn);
            startActivity(i);
            requireActivity().overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
        });

        rvPatients.setAdapter(adapter);

        showEmptyState();

        btnSearch.setOnClickListener(v -> {
            String input = textOf(etSearch);
            if (TextUtils.isEmpty(input)) {
                setFieldError("Enter patient name or MRN");
                showEmptyState();
                return;
            }
            clearFieldError();

            if (input.matches("\\d+")) {
                searchByMrn(input);
            } else {
                searchByName(input);
            }
        });

        btnCreateRecord.setOnClickListener(v -> {
            if (getActivity() == null) return;
            Intent intent = new Intent(getActivity(), CreateMedicalRecordActivity.class);
            startActivity(intent);
        });


        return view;
    }

    private void searchByMrn(String mrn) {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(requireContext(), "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("doctors")
                .document(doctorUid)
                .collection("patients")
                .document(mrn)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        adapter.setItems(new ArrayList<>());
                        showEmptyState();
                        Toast.makeText(requireContext(), "No patient found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String name = doc.getString("name");
                    String gender = doc.getString("gender");

                    Long ageL = doc.getLong("age");
                    int age = (ageL != null) ? ageL.intValue() : 0;

                    String shownMrn = doc.getString("medicalRecordNumber");
                    if (shownMrn == null || shownMrn.trim().isEmpty()) shownMrn = doc.getId();

                    List<PatientSearchAdapter.PatientRow> results = new ArrayList<>();
                    results.add(new PatientSearchAdapter.PatientRow(
                            shownMrn,
                            name != null ? name : ("MRN " + shownMrn),
                            gender != null ? gender : "—",
                            age
                    ));

                    adapter.setItems(results);
                    showResults();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void searchByName(String name) {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(requireContext(), "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String q = name.trim().toLowerCase(Locale.ROOT);

        db.collection("doctors")
                .document(doctorUid)
                .collection("patients")
                .orderBy("nameLower")
                .startAt(q)
                .endAt(q + "\uf8ff")
                .limit(30)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        adapter.setItems(new ArrayList<>());
                        showEmptyState();
                        Toast.makeText(requireContext(), "No patient found", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<PatientSearchAdapter.PatientRow> results = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String mrn = doc.getString("medicalRecordNumber");
                        if (mrn == null || mrn.trim().isEmpty()) mrn = doc.getId();

                        String pName = doc.getString("name");
                        String gender = doc.getString("gender");
                        Long ageL = doc.getLong("age");
                        int age = (ageL != null) ? ageL.intValue() : 0;

                        results.add(new PatientSearchAdapter.PatientRow(
                                mrn,
                                pName != null ? pName : ("MRN " + mrn),
                                gender != null ? gender : "—",
                                age
                        ));
                    }

                    adapter.setItems(results);
                    showResults();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void showEmptyState() {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        if (rvPatients != null) rvPatients.setVisibility(View.GONE);
    }

    private void showResults() {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (rvPatients != null) rvPatients.setVisibility(View.VISIBLE);
    }

    private String textOf(TextInputEditText et) {
        return (et.getText() != null) ? et.getText().toString().trim() : "";
    }

    private void setFieldError(String msg) {
        tilSearch.setErrorEnabled(true);
        tilSearch.setError(msg);
    }

    private void clearFieldError() {
        tilSearch.setError(null);
        tilSearch.setErrorEnabled(false);
    }
}
