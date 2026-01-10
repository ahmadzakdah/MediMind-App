package com.example.medimind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.ui.HelperClasses.PatientListAdapter;
import com.example.medimind.ui.base.BaseActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class PatientsActivity extends BaseActivity {

    private RecyclerView rvPatients;
    private LinearLayout layoutEmptyState;

    private PatientListAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected int getActiveNavId() {
        return R.id.navPatients;
    }

    @Override
    protected String getScreenTitle() {
        return "Patients";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_patients);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvPatients = findViewById(R.id.rvPatientsAll);
        layoutEmptyState = findViewById(R.id.layoutEmptyPatients);

        rvPatients.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PatientListAdapter(patient -> {
            Intent i = new Intent(PatientsActivity.this, PatientInfoActivity.class);
            i.putExtra("mrn", patient.mrn);
            startActivity(i);
        });

        rvPatients.setAdapter(adapter);

        loadPatients();
    }

    private void loadPatients() {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }

        // إذا عندك حقل nameLower زي ما مستخدم بالبحث: ممتاز نرتب عليه
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

                    List<PatientListAdapter.PatientRow> list = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String mrn = doc.getString("medicalRecordNumber");
                        if (mrn == null || mrn.trim().isEmpty()) mrn = doc.getId();

                        String name = doc.getString("name");
                        String gender = doc.getString("gender");

                        Long ageL = doc.getLong("age");
                        int age = (ageL != null) ? ageL.intValue() : 0;

                        list.add(new PatientListAdapter.PatientRow(
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
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    showEmpty();
                });
    }

    private void showEmpty() {
        layoutEmptyState.setVisibility(View.VISIBLE);
        rvPatients.setVisibility(View.GONE);
    }

    private void showList() {
        layoutEmptyState.setVisibility(View.GONE);
        rvPatients.setVisibility(View.VISIBLE);
    }
}
