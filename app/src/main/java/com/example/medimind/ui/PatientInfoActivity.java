package com.example.medimind.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.medimind.R;
import com.example.medimind.ui.HelperClasses.Patient;
import com.example.medimind.ui.base.BaseActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PatientInfoActivity extends BaseActivity {

    private ImageView btnBack, imgAvatar;
    private Button btnInfo;

    private TextView tvNameTop, tvMrn, tvNameRow, tvAge, tvGender, tvCreatedDate, tvLastUpdate;
    private TextView tvMeds, tvSurgeries, tvAllergies, tvFamily;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected int getActiveNavId() {
        return R.id.navPatients; // ✅ لأنه ضمن تبويب Patients
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_patient_info);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnBack = findViewById(R.id.btnBack);
        imgAvatar = findViewById(R.id.imgAvatar);
        btnInfo = findViewById(R.id.btnInfo);

        tvNameTop = findViewById(R.id.tvName);
        tvMrn = findViewById(R.id.tvMrn);
        tvNameRow = findViewById(R.id.tvNameRow);
        tvAge = findViewById(R.id.tvAge);
        tvGender = findViewById(R.id.tvGender);
        tvCreatedDate = findViewById(R.id.tvCreatedDate);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);

        tvMeds = findViewById(R.id.tvMeds);
        tvSurgeries = findViewById(R.id.tvSurgeries);
        tvAllergies = findViewById(R.id.tvAllergies);
        tvFamily = findViewById(R.id.tvFamily);

        btnBack.setOnClickListener(v -> finish());
        btnInfo.setOnClickListener(v -> Toast.makeText(this, "Info", Toast.LENGTH_SHORT).show());

        String mrn = getIntent().getStringExtra("mrn");
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadPatient(mrn.trim());
    }

    private void loadPatient(String mrn) {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("doctors")
                .document(doctorUid)
                .collection("patients")
                .document(mrn)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Patient not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    Patient p = doc.toObject(Patient.class);

                    String shownMrn = (p != null && notEmpty(p.getMedicalRecordNumber()))
                            ? p.getMedicalRecordNumber()
                            : doc.getId();

                    String name = (p != null && notEmpty(p.getName()))
                            ? p.getName()
                            : "Patient";

                    int age = (p != null) ? p.getAge() : 0;
                    String gender = (p != null && notEmpty(p.getGender())) ? p.getGender() : "—";
                    String createdDate = (p != null && notEmpty(p.getRecordCreationDate())) ? p.getRecordCreationDate() : "—";

                    String meds = (p != null && notEmpty(p.getCurrentMedications())) ? formatBullets(p.getCurrentMedications()) : "—";
                    String surgeries = (p != null && notEmpty(p.getPreviousSurgeries())) ? formatBullets(p.getPreviousSurgeries()) : "—";
                    String allergies = (p != null && notEmpty(p.getDrugAllergies())) ? formatBullets(p.getDrugAllergies()) : "—";
                    String family = (p != null && notEmpty(p.getFamilyHistoryGeneticDiseases())) ? formatBullets(p.getFamilyHistoryGeneticDiseases()) : "—";

                    String lastUpdate = (p != null && p.getCreatedAtMillis() > 0)
                            ? formatDate(p.getCreatedAtMillis())
                            : "—";

                    tvNameTop.setText(name);
                    tvMrn.setText(shownMrn);
                    tvNameRow.setText(name);
                    tvAge.setText(age > 0 ? (age + " years") : "—");
                    tvGender.setText(gender);
                    tvCreatedDate.setText(createdDate);
                    tvLastUpdate.setText(lastUpdate);

                    tvMeds.setText(meds);
                    tvSurgeries.setText(surgeries);
                    tvAllergies.setText(allergies);
                    tvFamily.setText(family);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String formatDate(long millis) {
        return new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(new Date(millis));
    }

    private String formatBullets(String raw) {
        String t = raw.trim();
        if (!t.contains("\n")) return "• " + t;

        String[] lines = t.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String x = line.trim();
            if (x.isEmpty()) continue;
            sb.append("• ").append(x).append("\n");
        }
        return sb.toString().trim();
    }
}
