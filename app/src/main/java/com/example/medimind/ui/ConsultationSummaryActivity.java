// java
package com.example.medimind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.medimind.R;
import com.example.medimind.data.DiseaseTextRepository;
import com.example.medimind.ui.HelperClasses.PredictionSummaryPayload;
import com.example.medimind.ui.base.BaseDetailsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * ConsultationSummaryActivity
 *
 * Purpose:
 * - Present an ML-driven consultation summary (symptoms, predicted diseases, vitals).
 * - Allow the doctor to save the consultation to Firestore or discard it.
 *
 * Responsibilities and UX choices:
 * - Read a JSON payload (PredictionSummaryPayload) from the launching Intent.
 * - Render safe defaults when fields are missing (use "—" and empty lists).
 * - Disable repeated saves by disabling the save button while the network call is pending.
 * - Increment the doctor's consultation counter using a merge set with FieldValue.increment on success.
 * - Keep UI rendering defensive (null checks) so layout changes don't crash the screen.
 *
 * Edge cases:
 * - If user is unauthenticated or MRN is missing the save is blocked and the user is informed.
 * - If the payload is null, the screen still shows UI but save is prevented and defaults are shown.
 */
public class ConsultationSummaryActivity extends BaseDetailsActivity {

    public static final String EXTRA_PAYLOAD_JSON = "extra_payload_json";

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private PredictionSummaryPayload p;
    private String mrn;

    private EditText etNotes;
    private Button btnSaveClose;

    @Override
    protected String getScreenTitle() {
        return "Consultation Review";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_consultation_summary);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // ===== Bind UI =====
        TextView tvSummaryAgeValue = findViewById(R.id.tvSummaryAgeValue);
        TextView tvSummaryGenderValue = findViewById(R.id.tvSummaryGenderValue);

        TextView txtBpValue = findViewById(R.id.txtBpValue);
        TextView txtHrValue = findViewById(R.id.txtHrValue);
        TextView txtRrValue = findViewById(R.id.txtRrValue);
        TextView txtSpo2Value = findViewById(R.id.txtSpo2Value);
        TextView txtSugarValue = findViewById(R.id.txtSugarValue);
        TextView txtTempValue = findViewById(R.id.txtTempValue);

        TextView txtSymptomsList = findViewById(R.id.txtSymptomsList);
        TextView txtDisease1 = findViewById(R.id.txtDisease1);
        TextView txtProb1 = findViewById(R.id.txtProb1);
        TextView txtDisease2 = findViewById(R.id.txtDisease2);
        TextView txtProb2 = findViewById(R.id.txtProb2);
        TextView txtDisease3 = findViewById(R.id.txtDisease3);
        TextView txtProb3 = findViewById(R.id.txtProb3);
        TextView txtDescription = findViewById(R.id.txtDescription);
        TextView txtInstructions = findViewById(R.id.txtInstructions);

        etNotes = findViewById(R.id.etNotes);
        btnSaveClose = findViewById(R.id.btnSaveClose);
        Button btnDiscard = findViewById(R.id.btnDiscard);

        // ===== Parse intent =====
        String json = getIntent().getStringExtra(EXTRA_PAYLOAD_JSON);
        mrn = getIntent().getStringExtra(PatientInfoActivity.EXTRA_MRN);

        // Deserialize payload if present; keep `p` null when we have nothing to show/save.
        p = (json == null) ? null : new Gson().fromJson(json, PredictionSummaryPayload.class);

        // ===== Render =====
        if (p != null) {

            // ✅ Age/Gender from payload fields (use readable defaults)
            if (tvSummaryAgeValue != null) {
                tvSummaryAgeValue.setText(p.age > 0 ? (p.age + " years") : "—");
            }

            if (tvSummaryGenderValue != null) {
                String g = (p.gender == null) ? "—" : p.gender.trim();
                tvSummaryGenderValue.setText(g.isEmpty() || g.equals("—") ? "—" : g);
            }

            // Symptoms and predictions rendered defensively
            txtSymptomsList.setText(joinBullets(p.symptoms));

            txtDisease1.setText(nullToDash(p.disease1));
            txtProb1.setText(nullToDash(p.prob1));
            txtDisease2.setText(nullToDash(p.disease2));
            txtProb2.setText(nullToDash(p.prob2));
            txtDisease3.setText(nullToDash(p.disease3));
            txtProb3.setText(nullToDash(p.prob3));

            // Use repository to provide helpful description/instructions for the top disease.
            String top1 = p.disease1;
            txtDescription.setText(DiseaseTextRepository.getDescription(this, top1));
            txtInstructions.setText(DiseaseTextRepository.getInstructions(this, top1));

            // Render vitals (may be null)
            setVitals(txtBpValue, txtHrValue, txtRrValue, txtSpo2Value, txtSugarValue, txtTempValue, p.vitals);

        } else {
            // Payload missing: show clear placeholders so doctor knows there is no AI suggestion.
            if (tvSummaryAgeValue != null) tvSummaryAgeValue.setText("—");
            if (tvSummaryGenderValue != null) tvSummaryGenderValue.setText("—");
            setVitals(txtBpValue, txtHrValue, txtRrValue, txtSpo2Value, txtSugarValue, txtTempValue, null);
        }

        // ===== Actions =====
        // Discard and save handlers — save is guarded and disables the button while saving to avoid duplicates.
        btnDiscard.setOnClickListener(v -> showDiscardDialog());
        btnSaveClose.setOnClickListener(v -> saveConsultation());
    }

    // =========================
    // ✅ Save to Firestore
    // =========================
    private void saveConsultation() {
        // Validate auth and MRN early to avoid creating incomplete documents.
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "MRN missing. Can't save.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (p == null) {
            // Nothing to persist — keep UX consistent by informing the user and closing.
            Toast.makeText(this, "Nothing to save.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Prevent duplicate taps while network operation is in-flight
        btnSaveClose.setEnabled(false);

        String notes = (etNotes == null) ? "" : etNotes.getText().toString().trim();

        int ageResolved = (p.age > 0) ? p.age : 0;
        String genderResolved = (p.gender == null || p.gender.trim().isEmpty()) ? "—" : p.gender.trim();

        // ✅ Clean vitals (remove Age/Gender just in case they were included)
        HashMap<String, Object> vitalsClean = new HashMap<>();
        if (p.vitals != null) {
            vitalsClean.putAll(p.vitals);
            vitalsClean.remove("Age");
            vitalsClean.remove("Gender");
        }

        // Build consultation document with safe defaults and server timestamp.
        HashMap<String, Object> doc = new HashMap<>();
        doc.put("symptoms", (p.symptoms == null) ? new ArrayList<>() : new ArrayList<>(p.symptoms));
        doc.put("vitals", vitalsClean);

        List<HashMap<String, Object>> predicted = new ArrayList<>();
        addPred(predicted, p.disease1, p.prob1);
        addPred(predicted, p.disease2, p.prob2);
        addPred(predicted, p.disease3, p.prob3);
        doc.put("predictedDiseases", predicted);

        doc.put("topDisease", p.disease1);
        doc.put("age", ageResolved);
        doc.put("gender", genderResolved);
        doc.put("notes", notes);
        doc.put("createdAt", FieldValue.serverTimestamp());

        // Persist consultation under doctors/{uid}/patients/{mrn}/consultations
        db.collection("doctors")
                .document(doctorUid)
                .collection("patients")
                .document(mrn)
                .collection("consultations")
                .add(doc)
                .addOnSuccessListener(ref -> {
                    // Notify user and increment consultationsCount in the doctor's stats doc atomically.
                    Toast.makeText(this, "Saved ✅", Toast.LENGTH_SHORT).show();
                    db.collection("doctors")
                            .document(doctorUid)
                            .collection("meta")
                            .document("stats")
                            .set(new HashMap<String, Object>() {{
                                put("consultationsCount", FieldValue.increment(1));
                            }}, com.google.firebase.firestore.SetOptions.merge());

                    // Return to patient details, clearing intermediate activities.
                    Intent i = new Intent(this, PatientInfoActivity.class);
                    i.putExtra(PatientInfoActivity.EXTRA_MRN, mrn);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    // Re-enable save on failure so user can retry.
                    btnSaveClose.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // =========================
    // ✅ Vitals rendering
    // =========================
    private void setVitals(TextView bp, TextView hr, TextView rr, TextView spo2, TextView sugar, TextView temp,
                           HashMap<String, Object> vit) {
        // Render vitals defensively: missing map -> placeholders
        if (vit == null) vit = new HashMap<>();

        String sys = get(vit, "bp_sys");
        String dia = get(vit, "bp_dia");
        String bpText = (isDash(sys) && isDash(dia)) ? "—" : sys + "/" + dia + " mmHg";

        if (bp != null) bp.setText(bpText);
        if (hr != null) hr.setText(unit(get(vit, "hr"), "bpm"));
        if (rr != null) rr.setText(unit(get(vit, "rr"), "/min"));
        if (spo2 != null) spo2.setText(unit(get(vit, "spo2"), "%"));
        if (sugar != null) sugar.setText(unit(get(vit, "blood_sugar"), "mg/dL"));
        if (temp != null) temp.setText(unit(get(vit, "temp_c"), "°C"));
    }

    // Safely read a value from the vitals map and normalize to a display string.
    private String get(HashMap<String, Object> vit, String key) {
        Object v = vit.get(key);
        if (v == null) return "—";
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? "—" : stripTrailingZeros(s);
    }

    // Remove unnecessary trailing `.0` from numeric strings for cleaner UI.
    private String stripTrailingZeros(String s) {
        try {
            double d = Double.parseDouble(s);
            if (d == (long) d) return String.valueOf((long) d);
            return String.valueOf(d);
        } catch (Exception e) {
            return s;
        }
    }

    // Helper to detect placeholder strings.
    private boolean isDash(String s) {
        return s == null || s.trim().isEmpty() || "—".equals(s.trim());
    }

    // Append unit or show placeholder when missing.
    private String unit(String v, String u) {
        return isDash(v) ? "—" : (v + " " + u);
    }

    // =========================
    // Helpers
    // =========================
    private String nullToDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "—" : s;
    }

    // Join symptom list into bullet-separated lines for display; show placeholder when empty.
    private String joinBullets(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append("• ").append(com.example.medimind.ui.adapters.SymptomSearchAdapter.pretty(list.get(i)));
            if (i != list.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private void showDiscardDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Discard changes?")
                .setMessage("All entered notes and this consultation will be lost.")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Discard", (d, w) -> {
                    d.dismiss();

                    Intent i = new Intent(this, PatientInfoActivity.class);
                    i.putExtra(PatientInfoActivity.EXTRA_MRN, mrn);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                })
                .show();
    }

    // Add a predicted disease to the list with safe defaults.
    private void addPred(List<HashMap<String, Object>> list, String name, String prob) {
        if (name == null || name.trim().isEmpty()) return;

        HashMap<String, Object> d = new HashMap<>();
        d.put("name", name);
        d.put("prob", (prob == null) ? "" : prob);
        list.add(d);
    }
}
