// java
package com.example.medimind.ui;

import com.example.medimind.ui.HelperClasses.Patient;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import com.example.medimind.R;

import androidx.core.widget.NestedScrollView;

import com.example.medimind.ui.base.BaseDetailsActivity;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * CreateMedicalRecordActivity
 *
 * Purpose:
 * - Let a doctor create a new patient record (MRN is auto-incremented).
 * - Validate required fields (name, age, gender) with live feedback.
 * - Perform a Firestore transaction to safely allocate the next MRN and write the patient.
 *
 * Key decisions / notes:
 * - MRN is shown as read-only preview and cannot be edited by the user.
 * - Validation uses setErrorEnabled(false) when no error to avoid leaving empty space.
 * - Transaction increments counters and updates stats atomically to avoid race conditions.
 * - After successful save the newly created PatientInfoActivity is opened and this screen is finished.
 */
public class CreateMedicalRecordActivity extends BaseDetailsActivity {

    // Input containers and fields
    private TextInputLayout tilMrn, tilName, tilAge, tilGender;
    private TextInputEditText etMrn, etName, etAge, etMeds, etSurgeries, etAllergies, etFamily;
    private MaterialAutoCompleteTextView actGender;
    private Button btnSave;
    NestedScrollView scroll;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected String getScreenTitle() {
        return "Create Medical Record";
    }

    /**
     * Small helper to wire the back button if present in the layout.
     * Makes the header back button visible and finishes the activity when tapped.
     */
    private void setupBackButton() {
        android.widget.ImageView btnBack = findViewById(R.id.btnBack);
        if (btnBack == null) return;

        btnBack.setVisibility(android.view.View.VISIBLE);
        btnBack.setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_create_medical_record);
        setupBackButton();

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // ===== Bind views =====
        tilMrn = findViewById(R.id.tilMrn);
        tilName = findViewById(R.id.tilName);
        tilAge = findViewById(R.id.tilAge);
        tilGender = findViewById(R.id.tilGender);

        etMrn = findViewById(R.id.etMrn);
        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        actGender = findViewById(R.id.actGender);

        etMeds = findViewById(R.id.etMeds);
        etSurgeries = findViewById(R.id.etSurgeries);
        etAllergies = findViewById(R.id.etAllergies);
        etFamily = findViewById(R.id.etFamily);

        btnSave = findViewById(R.id.btnSave);
        scroll = findViewById(R.id.scrollForm);

        // MRN is read-only in the UI (defensive: in case XML forgot to mark it)
        etMrn.setFocusable(false);
        etMrn.setClickable(false);
        etMrn.setCursorVisible(false);
        etMrn.setLongClickable(false);

        // Hide error containers by default so they don't reserve vertical space.
        setFieldError(tilMrn, null);
        setFieldError(tilName, null);
        setFieldError(tilAge, null);
        setFieldError(tilGender, null);

        setupGenderDropdown();     // configure gender selector
        setupLiveValidation();     // attach live validators

        // Load MRN preview immediately so user sees it right away.
        loadNextMrnPreview();

        btnSave.setOnClickListener(v -> saveToFirestore());
    }

    /**
     * Helper that sets/clears TextInputLayout error while also toggling errorEnabled
     * to prevent leftover empty spacing when there is no error.
     */
    private void setFieldError(TextInputLayout til, String error) {
        if (til == null) return;

        if (error == null || error.trim().isEmpty()) {
            til.setError(null);
            til.setErrorEnabled(false); // important: avoid reserved space
        } else {
            til.setErrorEnabled(true);
            til.setError(error);
        }
    }

    /**
     * Configure the gender autocomplete as a simple dropdown (Male/Female).
     * - User cannot type arbitrary values (adapter is fixed).
     * - Validate on selection and when focus is lost.
     */
    private void setupGenderDropdown() {
        String[] genders = {"Male", "Female"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                genders
        );
        actGender.setAdapter(adapter);

        // show list when tapping the field
        actGender.setOnClickListener(v -> actGender.showDropDown());

        // validation when the user selects an item
        actGender.setOnItemClickListener((parent, view, position, id) -> {
            String g = safeText(actGender.getText());
            setFieldError(tilGender, g.isEmpty() ? "Required" : null);
        });

        // validate once the field loses focus to catch empty selection
        actGender.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String g = safeText(actGender.getText());
                setFieldError(tilGender, g.isEmpty() ? "Required" : null);
            }
        });
    }

    // Return today's date in yyyy-MM-dd to stamp the record.
    private String getSystemDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    /**
     * Attach lightweight text watchers to provide immediate feedback:
     * - Name: required
     * - Age: numeric and within reasonable range
     *
     * MRN is excluded because it is auto-generated.
     */
    private void setupLiveValidation() {
        // Name watcher
        etName.addTextChangedListener(simpleWatcher(() -> {
            String name = safeText(etName.getText());
            setFieldError(tilName, name.isEmpty() ? "Required" : null);
        }));

        // Age watcher with numeric validation
        etAge.addTextChangedListener(simpleWatcher(() -> {
            String ageStr = safeText(etAge.getText());
            setFieldError(tilAge, validateAgeError(ageStr));
        }));
    }

    // Return an error string for age or null when valid.
    private String validateAgeError(String ageStr) {
        if (ageStr.isEmpty()) return "Required";
        Integer age = null;
        try {
            age = Integer.parseInt(ageStr);
        } catch (Exception ignored) { }
        if (age == null) return "Numbers only";
        if (age <= 0 || age > 120) return "Enter valid age";
        return null;
    }

    /**
     * Final required-field validation before attempting to save.
     * Returns true only when all required inputs are valid.
     */
    private boolean validateRequiredFinal() {
        String name = safeText(etName.getText());
        String ageStr = safeText(etAge.getText());
        String gender = safeText(actGender.getText());

        boolean ok = true;

        setFieldError(tilName, name.isEmpty() ? "Required" : null);
        if (name.isEmpty()) ok = false;

        String ageErr = validateAgeError(ageStr);
        setFieldError(tilAge, ageErr);
        if (ageErr != null) ok = false;

        setFieldError(tilGender, gender.isEmpty() ? "Required" : null);
        if (gender.isEmpty()) ok = false;

        return ok;
    }

    /**
     * Save a new patient record under the authenticated doctor's collection.
     *
     * Steps:
     * 1) Ensure authenticated
     * 2) Ensure MRN preview is loaded
     * 3) Validate required fields
     * 4) Run a Firestore transaction to:
     *    - read counters.nextMrn
     *    - write the patient document at doctors/{uid}/patients/{mrn}
     *    - merge default archive flags and increment stats.patientsCount
     *    - increment counters.nextMrn atomically
     *
     * On success: show toast, open PatientInfoActivity for the new MRN and finish this screen.
     */
    private void saveToFirestore() {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // MRN preview must be present; otherwise ask user to wait and refresh.
        String mrnPreview = safeText(etMrn.getText());
        if (mrnPreview.isEmpty()) {
            Toast.makeText(this, "MRN is loading... try again.", Toast.LENGTH_SHORT).show();
            loadNextMrnPreview();
            return;
        }

        if (!validateRequiredFinal()) {
            Toast.makeText(this, "Please complete required fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = safeText(etName.getText());
        String nameLower = name.toLowerCase(Locale.ROOT).trim();
        int age = Integer.parseInt(safeText(etAge.getText()));
        String gender = safeText(actGender.getText());

        String meds = safeText(etMeds.getText());
        String surgeries = safeText(etSurgeries.getText());
        String allergies = safeText(etAllergies.getText());
        String family = safeText(etFamily.getText());

        String recordCreationDate = getSystemDate();
        long nowMillis = System.currentTimeMillis();

        btnSave.setEnabled(false);

        final DocumentReference counterRef =
                db.collection("doctors")
                        .document(doctorUid)
                        .collection("meta")
                        .document("counters");

        // Firestore transaction ensures the next MRN allocation is atomic and safe under concurrency.
        db.runTransaction(transaction -> {
                    DocumentSnapshot snap = transaction.get(counterRef);

                    long nextMrn = 1;
                    if (snap.exists()) {
                        Long stored = snap.getLong("nextMrn");
                        if (stored != null) nextMrn = stored;
                    }

                    String mrnStr = String.valueOf(nextMrn);

                    DocumentReference patientRef =
                            db.collection("doctors")
                                    .document(doctorUid)
                                    .collection("patients")
                                    .document(mrnStr);

                    Patient patient = new Patient(
                            mrnStr,
                            name,
                            nameLower,
                            age,
                            gender,
                            recordCreationDate,
                            meds,
                            surgeries,
                            allergies,
                            family,
                            nowMillis
                    );

                    // write patient document
                    transaction.set(patientRef, patient);

                    // ensure archive flags exist with defaults (merge keeps existing values)
                    HashMap<String, Object> arch = new HashMap<>();
                    arch.put("archived", false);
                    arch.remove("archivedAt");
                    transaction.set(patientRef, arch, SetOptions.merge());

                    // increment stats.patientsCount (merge will create doc if missing)
                    DocumentReference statsRef =
                            db.collection("doctors")
                                    .document(doctorUid)
                                    .collection("meta")
                                    .document("stats");

                    transaction.set(statsRef, new HashMap<String, Object>() {{
                        put("patientsCount", FieldValue.increment(1));
                    }}, SetOptions.merge());

                    // update counters.nextMrn
                    HashMap<String, Object> update = new HashMap<>();
                    update.put("nextMrn", nextMrn + 1);
                    transaction.set(counterRef, update, SetOptions.merge());

                    return mrnStr;
                }).addOnSuccessListener(mrnStr -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Saved ✅ MRN: " + mrnStr, Toast.LENGTH_SHORT).show();

                    // Open patient info for the newly created MRN and close this screen.
                    Intent i = new Intent(CreateMedicalRecordActivity.this, PatientInfoActivity.class);
                    i.putExtra("mrn", mrnStr);
                    startActivity(i);

                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Clear non-MRN fields after a save or when resetting the form.
     * Keeps MRN intact because a new MRN preview will be shown after a save.
     */
    private void clearForm() {
        // Note: do not clear MRN here
        etName.setText("");
        etAge.setText("");
        actGender.setText("", false);

        etMeds.setText("");
        etSurgeries.setText("");
        etAllergies.setText("");
        etFamily.setText("");

        // remove errors and disable error containers to avoid reserved space
        setFieldError(tilMrn, null);
        setFieldError(tilName, null);
        setFieldError(tilAge, null);
        setFieldError(tilGender, null);
    }

    // Safe extraction of trimmed text from Editable objects.
    private String safeText(Editable e) {
        return (e == null) ? "" : e.toString().trim();
    }

    /**
     * Load the next MRN from Firestore counters document for preview only.
     * If the counters doc doesn't exist the default is 1.
     * This is a best-effort UX feature and not a replacement for the transaction.
     */
    private void loadNextMrnPreview() {
        String doctorUid;
        if (auth.getCurrentUser() != null)
            doctorUid = auth.getCurrentUser().getUid();
        else
            doctorUid = null;

        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("doctors")
                .document(doctorUid)
                .collection("meta")
                .document("counters")
                .get()
                .addOnSuccessListener(doc -> {
                    long next = 1;
                    Long v = doc.getLong("nextMrn");
                    if (v != null) next = v;

                    etMrn.setText(String.valueOf(next));
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load MRN: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    // Lightweight TextWatcher factory that runs a Runnable after text changes.
    private TextWatcher simpleWatcher(Runnable afterChanged) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { afterChanged.run(); }
        };
    }
}
