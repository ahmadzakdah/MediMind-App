package com.example.medimind.ui;

import com.example.medimind.ui.HelperClasses.Patient;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import com.example.medimind.R;

import androidx.core.widget.NestedScrollView;

import com.example.medimind.ui.base.BaseActivity;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class CreateMedicalRecordActivity extends BaseActivity {

    private TextInputLayout tilMrn, tilName, tilAge, tilGender;
    private TextInputEditText etMrn, etName, etAge, etMeds, etSurgeries, etAllergies, etFamily;
    private MaterialAutoCompleteTextView actGender;
    private Button btnSave;
    NestedScrollView scroll;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected int getActiveNavId() {
        return R.id.navHome;
    }
    @Override
    protected String getScreenTitle() {
        return "Create Medical Record";
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_create_medical_record);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind views
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

        // ✅ MRN read-only (حتى لو نسيت تضيفها في XML)
        etMrn.setFocusable(false);
        etMrn.setClickable(false);
        etMrn.setCursorVisible(false);
        etMrn.setLongClickable(false);

        // ✅ مهم: نخلي error container مطفي افتراضيًا لتجنب الفراغات
        setFieldError(tilMrn, null);
        setFieldError(tilName, null);
        setFieldError(tilAge, null);
        setFieldError(tilGender, null);

        setupGenderDropdown();     // Gender list only
        setupLiveValidation();     // Live validation (بدون MRN)

        // ✅ لازم تتنادى هون عشان يظهر MRN أول ما تفتح الشاشة
        loadNextMrnPreview();

        btnSave.setOnClickListener(v -> saveToFirestore());
    }

    /**
     * ✅ الحل الأساسي لمشكلة الفراغات:
     * لما ما يكون في خطأ، لازم نعمل setErrorEnabled(false)
     */
    private void setFieldError(TextInputLayout til, String error) {
        if (til == null) return;

        if (error == null || error.trim().isEmpty()) {
            til.setError(null);
            til.setErrorEnabled(false); // ✅ يمنع مساحة الخطأ (الفراغ)
        } else {
            til.setErrorEnabled(true);
            til.setError(error);
        }
    }

    // Gender dropdown: Male/Female only, no typing
    private void setupGenderDropdown() {
        String[] genders = {"Male", "Female"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                genders
        );
        actGender.setAdapter(adapter);

        // show list on click
        actGender.setOnClickListener(v -> actGender.showDropDown());

        // validation when selected
        actGender.setOnItemClickListener((parent, view, position, id) -> {
            String g = safeText(actGender.getText());
            setFieldError(tilGender, g.isEmpty() ? "Required" : null);
        });

        // if user leaves without choosing
        actGender.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String g = safeText(actGender.getText());
                setFieldError(tilGender, g.isEmpty() ? "Required" : null);
            }
        });
    }

    // System date automatically (yyyy-MM-dd)
    private String getSystemDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    // ✅ Live visual validation (بدون MRN لأنه تلقائي)
    private void setupLiveValidation() {
        // Name
        etName.addTextChangedListener(simpleWatcher(() -> {
            String name = safeText(etName.getText());
            setFieldError(tilName, name.isEmpty() ? "Required" : null);
        }));

        // Age
        etAge.addTextChangedListener(simpleWatcher(() -> {
            String ageStr = safeText(etAge.getText());
            setFieldError(tilAge, validateAgeError(ageStr));
        }));
    }

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

    // Required fields final check
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

    // Save under authenticated doctor's UID + MRN auto increment by Transaction
    private void saveToFirestore() {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ إذا الـ MRN لسه ما انعرض (preview) ما نخلي المستخدم يحفظ
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

            // save patient
            transaction.set(patientRef, patient);

            // increment counter
            HashMap<String, Object> update = new HashMap<>();
            update.put("nextMrn", nextMrn + 1);

            // إذا وثيقة counters مش موجودة، merge ينشئها تلقائيًا
            transaction.set(counterRef, update, SetOptions.merge());

            return mrnStr;
                }).addOnSuccessListener(mrnStr -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Saved ✅ MRN: " + mrnStr, Toast.LENGTH_SHORT).show();

                    // ✅ افتح PatientInfoActivity مباشرة
                    Intent i = new Intent(CreateMedicalRecordActivity.this, PatientInfoActivity.class);
                    i.putExtra("mrn", mrnStr);
                    startActivity(i);

                    // (اختياري) اقفل صفحة الإنشاء عشان ما يرجع عليها بالباك
                    finish();
                })
                .addOnFailureListener(e -> {
            btnSave.setEnabled(true);
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void clearForm() {
        // ✅ لا تمسح MRN هنا، لأننا رح نعرض الجديد مباشرة بعد الحفظ
        etName.setText("");
        etAge.setText("");
        actGender.setText("", false);

        etMeds.setText("");
        etSurgeries.setText("");
        etAllergies.setText("");
        etFamily.setText("");

        // ✅ اهم جزء: امسح الأخطاء + اطفي errorEnabled عشان ما يحجز مساحة
        setFieldError(tilMrn, null);
        setFieldError(tilName, null);
        setFieldError(tilAge, null);
        setFieldError(tilGender, null);
    }

    private String safeText(Editable e) {
        return (e == null) ? "" : e.toString().trim();
    }

    // Preview MRN (UI only)
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

    // Small helper for text watchers
    private TextWatcher simpleWatcher(Runnable afterChanged) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { afterChanged.run(); }
        };
    }
}
