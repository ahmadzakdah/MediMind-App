// java
package com.example.medimind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.medimind.R;
import com.example.medimind.ui.HelperClasses.Patient;
import com.example.medimind.ui.base.BaseDetailsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PatientInfoActivity
 *
 * Purpose:
 * - Display a single patient's details (name, MRN, age, gender, summary fields and timestamps).
 * - Provide quick actions: create a consultation for this patient and view consultation history.
 *
 * Notes / UX decisions:
 * - Requires an MRN in the launching Intent; if missing we show a toast and close early to avoid a broken screen.
 * - All Firestore reads are best-effort: on failures we show an error toast and close (this screen depends on the document).
 * - UI population is defensive: missing fields fall back to safe defaults (\"—\", 0, or placeholder text).
 * - Avatar selection is a small UX touch based on gender string equality (case-insensitive).
 * - The create-consultation button is disabled until patient data is successfully loaded to avoid launching with incomplete context.
 */
public class PatientInfoActivity extends BaseDetailsActivity {
    public static final String EXTRA_MRN = "mrn";
    public static final String EXTRA_AGE = "extra_age";
    public static final String EXTRA_GENDER = "extra_gender"; // "Male"/"Female" or other string values

    // Basic UI widgets
    private ImageView btnBack, imgAvatar;
    private Button btnInfo;

    private TextView tvNameTop, tvMrn, tvNameRow, tvAge, tvGender, tvCreatedDate, tvLastUpdate;
    private TextView tvMeds, tvSurgeries, tvAllergies, tvFamily;

    // Firebase helpers
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // Cached patient context used by actions (kept primitive/simple)
    private int patientAge = -1;
    private String patientGender = "";
    private String patientMrn = "";

    @Override
    protected String getScreenTitle() {
        return "Patient Information";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_patient_info);

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind views
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

        // Back button is visible and closes the screen — simple and expected behavior.
        btnBack.setVisibility(android.view.View.VISIBLE);
        btnBack.setOnClickListener(v -> finish());

        // Disable the consult button until patient load succeeds.
        btnInfo.setEnabled(false);
        btnInfo.setOnClickListener(v -> {
            if (patientMrn == null || patientMrn.trim().isEmpty()) {
                Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
                return;
            }

            // Launch create-consultation with the patient context populated.
            Intent intent = new Intent(this, CreateConsultationActivity.class);
            intent.putExtra(EXTRA_MRN, patientMrn);
            intent.putExtra(EXTRA_AGE, patientAge);
            intent.putExtra(EXTRA_GENDER, patientGender);
            startActivity(intent);
        });

        // History shortcut; validates MRN and opens ConsultationHistoryActivity.
        TextView tvHistory = findViewById(R.id.tvHistory);
        tvHistory.setOnClickListener(v -> {
            if (patientMrn == null || patientMrn.trim().isEmpty()) {
                Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, ConsultationHistoryActivity.class);
            i.putExtra(ConsultationHistoryActivity.EXTRA_MRN, patientMrn);
            startActivity(i);
        });

        // Validate required MRN from Intent early and bail out if missing.
        String mrn = getIntent().getStringExtra("mrn");
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadPatient(mrn.trim());
    }

    /**
     * Load patient document from Firestore and populate the UI.
     *
     * Behavior:
     * - Requires an authenticated doctor; otherwise shows a toast and closes the screen.
     * - On missing document we inform the user and close (this screen cannot function without the patient).
     * - Uses the Patient POJO mapping but falls back to doc id and safe defaults when fields are missing.
     * - Enables the create-consultation action only after successful load so downstream screens get valid context.
     */
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

                    // Map Firestore doc to the Patient helper class; may be null if mapping fails.
                    Patient p = doc.toObject(Patient.class);

                    // Prefer stored medicalRecordNumber but fall back to document id when missing.
                    String shownMrn = (p != null && notEmpty(p.getMedicalRecordNumber()))
                            ? p.getMedicalRecordNumber()
                            : doc.getId();

                    String name = (p != null && notEmpty(p.getName()))
                            ? p.getName()
                            : "Patient";

                    int age = (p != null) ? p.getAge() : 0;
                    String gender = (p != null && notEmpty(p.getGender())) ? p.getGender() : "—";

                    // Cache for actions
                    patientMrn = shownMrn;
                    patientAge = age;
                    patientGender = gender;

                    String createdDate = (p != null && notEmpty(p.getRecordCreationDate())) ? p.getRecordCreationDate() : "—";

                    // Convert long text fields to bullet lists when present, otherwise show placeholder.
                    String meds = (p != null && notEmpty(p.getCurrentMedications())) ? formatBullets(p.getCurrentMedications()) : "—";
                    String surgeries = (p != null && notEmpty(p.getPreviousSurgeries())) ? formatBullets(p.getPreviousSurgeries()) : "—";
                    String allergies = (p != null && notEmpty(p.getDrugAllergies())) ? formatBullets(p.getDrugAllergies()) : "—";
                    String family = (p != null && notEmpty(p.getFamilyHistoryGeneticDiseases())) ? formatBullets(p.getFamilyHistoryGeneticDiseases()) : "—";

                    String lastUpdate = (p != null && p.getCreatedAtMillis() > 0)
                            ? formatDate(p.getCreatedAtMillis())
                            : "—";

                    // Populate UI defensively.
                    tvNameTop.setText(name);
                    tvMrn.setText(shownMrn);
                    tvNameRow.setText(name);
                    tvAge.setText(age  + " years");
                    tvGender.setText(gender);

                    // Small avatar UX: choose icon by gender string; default is male icon for anything else.
                    if (gender.equalsIgnoreCase("female")) {
                        imgAvatar.setImageResource(R.drawable.ic_female);
                    } else {
                        imgAvatar.setImageResource(R.drawable.ic_male);
                    }

                    tvCreatedDate.setText(createdDate);
                    tvLastUpdate.setText(lastUpdate);

                    tvMeds.setText(meds);
                    tvSurgeries.setText(surgeries);
                    tvAllergies.setText(allergies);
                    tvFamily.setText(family);

                    // Now that we have valid context, enable the consultation action.
                    btnInfo.setEnabled(true);

                })
                .addOnFailureListener(e -> {
                    // Surface failure and close — this screen is not useful without the document.
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // Simple null/empty helper used throughout the class.
    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // Format millis to a user-friendly long date.
    private String formatDate(long millis) {
        return new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(new Date(millis));
    }

    // Convert newline-separated text into a bullets string for display.
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
