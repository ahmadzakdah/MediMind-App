package com.example.medimind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.ui.base.BaseActivity;
import com.example.medimind.ui.HelperClasses.PatientSearchAdapter;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchActivity extends BaseActivity {

    private TextInputLayout tilSearch;
    private TextInputEditText etSearch;
    private Button btnSearch, btnCreateRecord;

    private android.widget.LinearLayout layoutEmptyState;

    private RecyclerView rvPatients;
    private PatientSearchAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected int getActiveNavId() {
      return R.id.navHome; // لأنه هاي الصفحة هي الهوم
       }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_search);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind
        tilSearch = findViewById(R.id.tilSearch);
        etSearch = findViewById(R.id.etSearch);
        btnSearch = findViewById(R.id.btnSearch);
        btnCreateRecord = findViewById(R.id.btnCreateRecord);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        rvPatients = findViewById(R.id.rvPatients);
        rvPatients.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PatientSearchAdapter(patient -> {
            // ✅ فتح صفحة معلومات المريض
            Intent i = new Intent(SearchActivity.this, PatientInfoActivity.class);
            i.putExtra("mrn", patient.mrn);
            startActivity(i);
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

            // إذا أرقام فقط => MRN
            if (input.matches("\\d+")) {
                searchByMrn(input);
            } else {
                searchByName(input);
            }
        });

        btnCreateRecord.setOnClickListener(v -> {
            Intent intent = new Intent(SearchActivity.this, CreateMedicalRecordActivity.class);
            startActivity(intent);
        });
    }

    private void searchByMrn(String mrn) {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "No patient found", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void searchByName(String name) {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "No patient found", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void showEmptyState() {
        layoutEmptyState.setVisibility(android.view.View.VISIBLE);
        rvPatients.setVisibility(android.view.View.GONE);
    }

    private void showResults() {
        layoutEmptyState.setVisibility(android.view.View.GONE);
        rvPatients.setVisibility(android.view.View.VISIBLE);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
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
