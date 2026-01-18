package com.example.medimind.ui;

import static com.example.medimind.ui.adapters.SymptomSearchAdapter.pretty;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.medimind.Prediction.PredictResponseItem;
import com.example.medimind.R;
import com.example.medimind.Suggestion.DiseaseProb;
import com.example.medimind.data.SymptomRepository;
import com.example.medimind.domain.VitalsCollector;
import com.example.medimind.network.PredictionManager;
import com.example.medimind.network.SuggestionManager;
import com.example.medimind.ui.HelperClasses.PredictionSummaryPayload;
import com.example.medimind.ui.HelperClasses.UiUtils;
import com.example.medimind.ui.adapters.SymptomSearchAdapter;
import com.example.medimind.ui.base.BaseDetailsActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Activity to start a new consultation:
 * - collect symptoms (via chips + autocomplete)
 * - optionally collect vitals in a bottom sheet
 * - call prediction service and show top results
 * - request server-side suggestions based on top diseases
 * This class focuses on UI orchestration and passing minimal payloads to managers.
 */
public class CreateConsultationActivity extends BaseDetailsActivity {

    private static final String BASE_URL = "https://medimind-ml-api.onrender.com/";
    private static final String SYMPTOMS_FILE = "symptoms_list.json";

    private String mrn;
    private String patientGender; // "Male"/"Female" or "—" when unknown
    private int patientAge = 0;

    private BottomSheetBehavior<View> vitalsBehavior;
    private MaterialCardView cardSys, cardDia, cardHr, cardRr, cardSugar, cardTemp, cardSpo2;

    private View dimOverlay;

    private AutoCompleteTextView etSymptom;
    private ChipGroup chipsSymptoms, chipsSuggestions;

    private final LinkedHashSet<String> selectedSymptoms = new LinkedHashSet<>();
    private List<String> allSymptoms = new ArrayList<>();
    private SymptomSearchAdapter symAdapter;

    private TextView tvPredictHint, tvSuggestTitle, tvPatientAgeValue, tvPatientGenderValue;
    private LinearLayout predictBox;
    private View btnContinue;

    private EditText etSysBP, etDiaBP, etHR, etRR, etBloodSugar, etTemp, etSpO2;
    private VitalsCollector vitalsCollector;

    private final Map<String, Object> vitalsMap = new HashMap<>();
    private boolean vitalsSubmitted = false;

    private PredictionManager predictionManager;
    private SuggestionManager suggestionManager;

    private List<DiseaseProb> lastTopDiseases = new ArrayList<>();
    private List<PredictResponseItem> lastPredictItems = new ArrayList<>();

    @Override
    protected String getScreenTitle() {
        return "Start New Consultation";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_create_consultation);

        mrn = getIntent().getStringExtra(PatientInfoActivity.EXTRA_MRN);
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mrn = mrn.trim();

        patientAge = getIntent().getIntExtra(PatientInfoActivity.EXTRA_AGE, 0);
        patientGender = getIntent().getStringExtra(PatientInfoActivity.EXTRA_GENDER);
        if (patientGender == null) patientGender = "—";
        patientGender = patientGender.trim();

        bindHeaderPatientInfo();
        bindBottomSheet();
        bindSymptomsUI();
        bindPredictUI();
        bindVitalsUI();

        predictionManager = new PredictionManager(BASE_URL);
        suggestionManager = new SuggestionManager(BASE_URL);

        allSymptoms = SymptomRepository.loadFromAssets(this, SYMPTOMS_FILE);
        if (allSymptoms.isEmpty()) {
            Toast.makeText(this, "Symptoms list is empty (check file)", Toast.LENGTH_LONG).show();
        }

        setupSymptomAdapter();
        refreshSymptomAdapter();

        updatePredictUIIdle();
        hideSuggestions();
        vitalsSubmitted = false;

        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> openSummary());
        }
    }

    private void bindHeaderPatientInfo() {
        tvPatientAgeValue = findViewById(R.id.tvPatientAgeValue);
        tvPatientGenderValue = findViewById(R.id.tvPatientGenderValue);

        if (tvPatientAgeValue != null) {
            tvPatientAgeValue.setText(patientAge > 0 ? (patientAge + " years") : "—");
        }

        if (tvPatientGenderValue != null) {
            tvPatientGenderValue.setText(
                    (patientGender.isEmpty() || patientGender.equals("—")) ? "—" : patientGender
            );
        }
    }

    private void bindBottomSheet() {
        View vitalsSheet = findViewById(R.id.vitalsSheet);
        MaterialButton btnVitals = findViewById(R.id.btnVitals);
        View btnSubmitVitals = findViewById(R.id.btnSubmitVitals);
        dimOverlay = findViewById(R.id.dimOverlay);

        tvSuggestTitle = findViewById(R.id.tvSuggestTitle);
        chipsSuggestions = findViewById(R.id.chipsSuggestions);

        vitalsBehavior = BottomSheetBehavior.from(vitalsSheet);
        vitalsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        if (btnVitals != null) btnVitals.setOnClickListener(v -> openVitals());
        if (dimOverlay != null) dimOverlay.setOnClickListener(v -> closeVitals());

        View btnCancel = findViewById(R.id.btnCancel);
        if (btnCancel != null) {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setOnClickListener(v -> finish());
        }

        if (btnSubmitVitals != null) {
            btnSubmitVitals.setOnClickListener(v -> {

                if (!validateVitalsRangesUI()) return;

                Map<String, Object> enteredOnly = vitalsCollector.collectEnteredOnly();
                vitalsSubmitted = !enteredOnly.isEmpty();

                vitalsMap.clear();
                vitalsMap.putAll(vitalsCollector.buildForPredict(patientAge, patientGender, enteredOnly));

                closeVitals();
                if (selectedSymptoms.size() >= 2) schedulePredict();
            });
        }

        vitalsBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) hideOverlay();
                else showOverlay();
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (dimOverlay != null) {
                    dimOverlay.setAlpha(slideOffset <= 0f ? 0f : Math.min(0.8f, 0.8f * slideOffset));
                }
            }
        });
    }

    private void bindSymptomsUI() {
        etSymptom = findViewById(R.id.etSymptom);
        chipsSymptoms = findViewById(R.id.chipsSymptoms);
    }

    private void bindPredictUI() {
        tvPredictHint = findViewById(R.id.tvPredictHint);
        predictBox = findViewById(R.id.predictBox);
        btnContinue = findViewById(R.id.btnContinue);
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
    }

    private void bindVitalsUI() {
        etSysBP = findViewById(R.id.etSysBP);
        etDiaBP = findViewById(R.id.etDiaBP);
        etHR = findViewById(R.id.etHR);
        etRR = findViewById(R.id.etRR);
        etBloodSugar = findViewById(R.id.etBloodSugar);
        etTemp = findViewById(R.id.etTemp);
        etSpO2 = findViewById(R.id.etSpO2);

        cardSys = findViewById(R.id.cardSys);
        cardDia = findViewById(R.id.cardDia);
        cardHr = findViewById(R.id.cardHr);
        cardRr = findViewById(R.id.cardRr);
        cardSugar = findViewById(R.id.cardSugar);
        cardTemp = findViewById(R.id.cardTemp);
        cardSpo2 = findViewById(R.id.cardSpo2);

        vitalsCollector = new VitalsCollector(etSysBP, etDiaBP, etHR, etRR, etBloodSugar, etTemp, etSpO2);
    }

    /**
     * ✅ هنا التعديل المطلوب:
     * بعد اختيار عرض:
     * - نضيف الشيب
     * - نفرغ الحقل
     * - نغلق الدراوب داون
     * - ننزل الكيبورد تلقائي
     */
    private void setupSymptomAdapter() {
        symAdapter = new SymptomSearchAdapter(this, allSymptoms, () ->
                etSymptom.getText() != null ? etSymptom.getText().toString() : ""
        );

        etSymptom.setAdapter(symAdapter);
        etSymptom.setThreshold(1);

        etSymptom.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(android.text.Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (symAdapter != null) symAdapter.notifyDataSetChanged();
            }
        });

        etSymptom.setOnItemClickListener((parent, view, position, id) -> {
            String symptom = (String) parent.getItemAtPosition(position);

            addChip(symptom);

            etSymptom.setText("");
            etSymptom.dismissDropDown();

            // ✅ نزّل الكيبورد مباشرة (post عشان بعض الأجهزة تتأخر)
            etSymptom.post(() -> hideKeyboard(etSymptom));

            // ✅ اختياري: شيل الفوكس لنزول "مؤكد"
            etSymptom.clearFocus();

            // ✅ اختياري: رجّع الفوكس سريعًا عشان تكتب عرض ثاني بدون "كبسة زيادة"
            etSymptom.post(() -> etSymptom.requestFocus());
        });
    }

    private void addChip(String symptom) {
        if (selectedSymptoms.contains(symptom)) return;
        selectedSymptoms.add(symptom);

        Chip chip = UiUtils.createStyledChip(this, pretty(symptom), symptom, null, v -> {
            selectedSymptoms.remove(symptom);
            chipsSymptoms.removeView((Chip) v);
            refreshSymptomAdapter();
            onSymptomsChanged();
        });

        chipsSymptoms.addView(chip);
        refreshSymptomAdapter();
        onSymptomsChanged();
    }

    private void refreshSymptomAdapter() {
        ArrayList<String> filtered = new ArrayList<>();
        for (String s : allSymptoms) {
            if (!selectedSymptoms.contains(s)) filtered.add(s);
        }
        if (symAdapter != null) symAdapter.updateItems(filtered);
    }

    private void onSymptomsChanged() {
        if (selectedSymptoms.size() < 2) {
            predictionManager.cancel();
            suggestionManager.cancel();

            lastTopDiseases.clear();
            lastPredictItems.clear();

            updatePredictUIIdle();
            hideSuggestions();
            return;
        }
        schedulePredict();
    }

    private void schedulePredict() {
        if (vitalsMap.isEmpty()) {
            vitalsMap.putAll(vitalsCollector.buildForPredict(patientAge, patientGender, new HashMap<>()));
        }

        predictionManager.schedulePredict(
                new ArrayList<>(selectedSymptoms),
                vitalsMap,
                350,
                new PredictionManager.Listener() {
                    @Override public void onLoading() {
                        if (tvPredictHint != null) tvPredictHint.setText("Predicting...");
                        if (predictBox != null) { predictBox.setVisibility(View.GONE); predictBox.removeAllViews(); }
                        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
                    }

                    @Override public void onSuccess(List<PredictResponseItem> items) {
                        lastPredictItems = (items == null) ? new ArrayList<>() : items;

                        if (tvPredictHint != null) tvPredictHint.setText("Top results:");
                        showTop3(lastPredictItems);

                        if (btnContinue != null) {
                            btnContinue.setVisibility(lastPredictItems.isEmpty() ? View.GONE : View.VISIBLE);
                        }

                        lastTopDiseases = toDiseaseProb(lastPredictItems);
                        requestServerSuggestions();
                    }

                    @Override public void onError(String message) {
                        if (tvPredictHint != null) tvPredictHint.setText(message);
                        lastPredictItems.clear();
                        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
                        hideSuggestions();
                    }
                }
        );
    }

    private void showTop3(List<PredictResponseItem> items) {
        if (predictBox == null) return;
        predictBox.setVisibility(View.VISIBLE);
        predictBox.removeAllViews();

        if (items == null) return;

        for (PredictResponseItem r : items) {
            TextView tv = new TextView(this);
            tv.setText("• " + r.disease + "  (" + r.probability + "%)");
            tv.setTextSize(15f);
            tv.setPadding(0, 8, 0, 8);
            predictBox.addView(tv);
        }
    }

    private void updatePredictUIIdle() {
        if (tvPredictHint != null) tvPredictHint.setText("Choose at least 2 symptoms to start prediction.");
        if (predictBox != null) { predictBox.setVisibility(View.GONE); predictBox.removeAllViews(); }
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
    }

    private void openSummary() {
        if (lastPredictItems == null || lastPredictItems.isEmpty()) {
            Toast.makeText(this, "No prediction yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        PredictionSummaryPayload payload = new PredictionSummaryPayload();
        payload.symptoms = new ArrayList<>(selectedSymptoms);

        payload.vitals = vitalsSubmitted ? collectUserVitalsOnly() : new HashMap<>();

        payload.age = patientAge;
        payload.gender = patientGender;

        if (lastPredictItems.size() > 0) {
            payload.disease1 = lastPredictItems.get(0).disease;
            payload.prob1 = lastPredictItems.get(0).probability + "%";
        }
        if (lastPredictItems.size() > 1) {
            payload.disease2 = lastPredictItems.get(1).disease;
            payload.prob2 = lastPredictItems.get(1).probability + "%";
        }
        if (lastPredictItems.size() > 2) {
            payload.disease3 = lastPredictItems.get(2).disease;
            payload.prob3 = lastPredictItems.get(2).probability + "%";
        }

        String json = new Gson().toJson(payload);

        Intent i = new Intent(this, ConsultationSummaryActivity.class);
        i.putExtra(ConsultationSummaryActivity.EXTRA_PAYLOAD_JSON, json);
        i.putExtra(PatientInfoActivity.EXTRA_MRN, mrn);
        startActivity(i);
    }

    private HashMap<String, Object> collectUserVitalsOnly() {
        HashMap<String, Object> out = new HashMap<>();

        UiUtils.putIntIfPresent(out, "bp_sys", etSysBP);
        UiUtils.putIntIfPresent(out, "bp_dia", etDiaBP);
        UiUtils.putIntIfPresent(out, "hr", etHR);
        UiUtils.putIntIfPresent(out, "rr", etRR);
        UiUtils.putIntIfPresent(out, "blood_sugar", etBloodSugar);
        UiUtils.putDoubleIfPresent(out, "temp_c", etTemp);
        UiUtils.putIntIfPresent(out, "spo2", etSpO2);

        return out;
    }

    private List<DiseaseProb> toDiseaseProb(List<PredictResponseItem> items) {
        ArrayList<DiseaseProb> out = new ArrayList<>();
        if (items == null) return out;

        for (PredictResponseItem r : items) {
            out.add(new DiseaseProb(r.disease, r.probability));
        }
        return out;
    }

    private void requestServerSuggestions() {
        if (selectedSymptoms.size() < 2) {
            hideSuggestions();
            return;
        }

        suggestionManager.scheduleSuggest(
                new ArrayList<>(selectedSymptoms),
                lastTopDiseases,
                8,
                250,
                new SuggestionManager.Listener() {
                    @Override public void onSuccess(List<String> suggestions) {
                        renderSuggestions(suggestions);
                    }

                    @Override public void onError(String message) {
                        hideSuggestions();
                    }
                }
        );
    }

    private void renderSuggestions(List<String> suggestions) {
        if (chipsSuggestions == null || tvSuggestTitle == null) return;

        chipsSuggestions.removeAllViews();

        if (suggestions == null || suggestions.isEmpty()) {
            hideSuggestions();
            return;
        }

        tvSuggestTitle.setVisibility(View.VISIBLE);
        chipsSuggestions.setVisibility(View.VISIBLE);

        for (String s : suggestions) {
            Chip chip = UiUtils.createStyledChip(this, pretty(s), s, v -> addChip(s), null);
            chipsSuggestions.addView(chip);
        }
    }

    private void hideSuggestions() {
        if (tvSuggestTitle != null) tvSuggestTitle.setVisibility(View.GONE);
        if (chipsSuggestions != null) {
            chipsSuggestions.setVisibility(View.GONE);
            chipsSuggestions.removeAllViews();
        }
    }

    private void openVitals() {
        showOverlay();
        if (vitalsBehavior != null) vitalsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void closeVitals() {
        if (vitalsBehavior != null) vitalsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    private void showOverlay() {
        if (dimOverlay != null && dimOverlay.getVisibility() != View.VISIBLE) {
            dimOverlay.setVisibility(View.VISIBLE);
            dimOverlay.setAlpha(0f);
            dimOverlay.animate().alpha(0.8f).setDuration(180).start();
        }
    }

    private void hideOverlay() {
        if (dimOverlay != null && dimOverlay.getVisibility() == View.VISIBLE) {
            dimOverlay.animate().alpha(0f).setDuration(150)
                    .withEndAction(() -> dimOverlay.setVisibility(View.GONE))
                    .start();
        }
    }

    @Override
    public void onBackPressed() {
        if (vitalsBehavior != null && vitalsBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            closeVitals();
            return;
        }
        super.onBackPressed();
    }

    private boolean validateVitalsRangesUI() {
        boolean ok = true;

        ok &= UiUtils.validateNumberField(etSysBP, cardSys, 70, 200, true);
        ok &= UiUtils.validateNumberField(etDiaBP, cardDia, 40, 130, true);
        ok &= UiUtils.validateNumberField(etHR, cardHr, 40, 180, true);
        ok &= UiUtils.validateNumberField(etRR, cardRr, 8, 40, true);
        ok &= UiUtils.validateNumberField(etBloodSugar, cardSugar, 50, 400, true);
        ok &= UiUtils.validateNumberField(etTemp, cardTemp, 35.0, 42.0, false);
        ok &= UiUtils.validateNumberField(etSpO2, cardSpo2, 70, 100, true);

        if (!ok) Toast.makeText(this, "Vitals values are out of range.", Toast.LENGTH_SHORT).show();
        return ok;
    }

    // ✅ helper: hide keyboard
    private void hideKeyboard(View v) {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }
}
