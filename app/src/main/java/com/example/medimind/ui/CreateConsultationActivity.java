// java
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

    // Remote API base and local symptom file — kept as constants for easier maintenance.
    private static final String BASE_URL = "https://medimind-ml-api.onrender.com/";
    private static final String SYMPTOMS_FILE = "symptoms_list.json";

    // Patient context (provided by previous screen via intent).
    private String mrn;
    private String patientGender; // "Male"/"Female" or "—" when unknown
    private int patientAge = 0;

    // BottomSheet for vitals entry and the cards used for inline validation highlighting.
    private BottomSheetBehavior<View> vitalsBehavior;
    private MaterialCardView cardSys, cardDia, cardHr, cardRr, cardSugar, cardTemp, cardSpo2;

    // Dim overlay shown while bottom sheet is visible.
    private View dimOverlay;

    // Symptoms UI: text field for searching and groups for selected/suggested chips.
    private AutoCompleteTextView etSymptom;
    private ChipGroup chipsSymptoms, chipsSuggestions;

    // Currently selected symptoms (preserves insertion order, avoids duplicates).
    private final LinkedHashSet<String> selectedSymptoms = new LinkedHashSet<>();
    private List<String> allSymptoms = new ArrayList<>();
    private SymptomSearchAdapter symAdapter;

    // UI for showing prediction results / hints and a continue button to go to summary.
    private TextView tvPredictHint, tvSuggestTitle, tvPatientAgeValue, tvPatientGenderValue;
    private LinearLayout predictBox;
    private View btnContinue;

    // Vitals input fields and a helper that centralizes parsing/packing the vitals.
    private EditText etSysBP, etDiaBP, etHR, etRR, etBloodSugar, etTemp, etSpO2;
    private VitalsCollector vitalsCollector;

    // Map used for building the prediction request payload (only fields needed by ML API).
    private final Map<String, Object> vitalsMap = new HashMap<>();
    private boolean vitalsSubmitted = false; // true if user entered any vitals

    // Managers that handle background work (network + scheduling).
    private PredictionManager predictionManager;
    private SuggestionManager suggestionManager;

    // Last received prediction/suggestion results are cached for the summary screen.
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

        // ===== Intent handling =====
        // MRN is mandatory — if missing, bail out early.
        mrn = getIntent().getStringExtra(PatientInfoActivity.EXTRA_MRN);
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mrn = mrn.trim();

        // Optional demographics passed along and normalized.
        patientAge = getIntent().getIntExtra(PatientInfoActivity.EXTRA_AGE, 0);
        patientGender = getIntent().getStringExtra(PatientInfoActivity.EXTRA_GENDER);
        if (patientGender == null) patientGender = "—";
        patientGender = patientGender.trim();

        // ===== Bind UI components =====
        bindHeaderPatientInfo();
        bindBottomSheet();
        bindSymptomsUI();
        bindPredictUI();
        bindVitalsUI();

        // ===== Initialize managers =====
        predictionManager = new PredictionManager(BASE_URL);
        suggestionManager = new SuggestionManager(BASE_URL);

        // ===== Load symptom list from assets (local cache) =====
        allSymptoms = SymptomRepository.loadFromAssets(this, SYMPTOMS_FILE);
        if (allSymptoms.isEmpty()) {
            // This should not happen in normal app install — warn dev/user.
            Toast.makeText(this, "Symptoms list is empty (check file)", Toast.LENGTH_LONG).show();
        }
        setupSymptomAdapter();
        refreshSymptomAdapter();

        // initial UI state
        updatePredictUIIdle();
        hideSuggestions();
        vitalsSubmitted = false;

        // Continue button navigates to the summary screen once prediction exists.
        if (btnContinue != null) {
            btnContinue.setOnClickListener(v -> openSummary());
        }
    }

    /**
     * Bind header UI that shows patient age and gender.
     * Small normalization done: show '—' for unknown values.
     */
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

    /**
     * Configure the vitals bottom sheet and its controls.
     * - The submit button validates ranges and prepares the payload for prediction.
     * - A dim overlay is animated in/out alongside the bottom sheet.
     */
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
            // small UX: show a cancel button on the bottom to finish this activity.
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setOnClickListener(v -> {
                finish();
            });
        }

        if (btnSubmitVitals != null) {
            btnSubmitVitals.setOnClickListener(v -> {

                // 1) Validate ranges — if invalid, UI already highlights offending field.
                if (!validateVitalsRangesUI()) return;

                // 2) collect exactly what the user entered (don't add age/gender here)
                Map<String, Object> enteredOnly = vitalsCollector.collectEnteredOnly();
                vitalsSubmitted = !enteredOnly.isEmpty();

                // 3) build the map that the prediction API expects (age/gender + entered vitals)
                vitalsMap.clear();
                vitalsMap.putAll(vitalsCollector.buildForPredict(patientAge, patientGender, enteredOnly));

                // close bottom sheet and possibly trigger prediction if enough symptoms are selected
                closeVitals();
                if (selectedSymptoms.size() >= 2) schedulePredict();
            });

        }

        // Keep overlay visibility / alpha in sync with bottom sheet state/drag.
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

    /**
     * Bind symptom entry UI elements.
     * The autocomplete view will use SymptomSearchAdapter to suggest items as the user types.
     */
    private void bindSymptomsUI() {
        etSymptom = findViewById(R.id.etSymptom);
        chipsSymptoms = findViewById(R.id.chipsSymptoms);
    }

    /**
     * Bind UI elements used to display prediction results.
     * Hides the continue button initially until a prediction is available.
     */
    private void bindPredictUI() {
        tvPredictHint = findViewById(R.id.tvPredictHint);
        predictBox = findViewById(R.id.predictBox);
        btnContinue = findViewById(R.id.btnContinue);
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
    }

    /**
     * Find and hold references to vitals EditTexts and the card views used for visual validation.
     * Also create a small helper (VitalsCollector) that centralizes parsing and payload creation.
     */
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
     * Configure the AutoCompleteTextView with the symptom list and a listener to add chips on selection.
     * The adapter is kept lightweight and notifies changes as the typed text changes.
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
                // keep suggestions fresh while typing
                if (symAdapter != null) symAdapter.notifyDataSetChanged();
            }
        });

        etSymptom.setOnItemClickListener((parent, view, position, id) -> {
            String symptom = (String) parent.getItemAtPosition(position);
            addChip(symptom);
            etSymptom.setText("");
        });
    }

    /**
     * Add a selected symptom as a chip.
     * The close icon removes the symptom and updates the adapters/UI.
     *
     * Note: the close action uses the clicked view (v) to avoid capturing the local chip variable.
     */
    private void addChip(String symptom) {
        if (selectedSymptoms.contains(symptom)) return;
        selectedSymptoms.add(symptom);

        Chip chip = UiUtils.createStyledChip(this, pretty(symptom), symptom, null, v -> {
            selectedSymptoms.remove(symptom);
            // remove the chip using the view provided by the close click
            chipsSymptoms.removeView((Chip) v);
            refreshSymptomAdapter();
            onSymptomsChanged();
        });

        chipsSymptoms.addView(chip);
        refreshSymptomAdapter();
        onSymptomsChanged();
    }

    /**
     * Rebuilds the symptom adapter's item list excluding currently selected symptoms.
     * This keeps autocomplete suggestions relevant.
     */
    private void refreshSymptomAdapter() {
        ArrayList<String> filtered = new ArrayList<>();
        for (String s : allSymptoms) {
            if (!selectedSymptoms.contains(s)) filtered.add(s);
        }
        if (symAdapter != null) symAdapter.updateItems(filtered);
    }

    /**
     * Called whenever the selected symptoms set changes.
     * - If less than 2 symptoms selected we cancel background requests and reset UI.
     * - Otherwise trigger a prediction flow.
     */
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

    /**
     * Schedule a prediction using the PredictionManager.
     * If vitalsMap is empty (user never opened vitals sheet), build a default map from demographics only.
     *
     * The listener updates UI onLoading/onSuccess/onError.
     */
    private void schedulePredict() {
        // if vitals not provided by user, use demographics-only payload so API still receives expected fields
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

    /**
     * Show up to three top prediction results inside a vertical container.
     * Simple TextViews are added dynamically.
     */
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

    /**
     * Reset prediction UI to the idle state and hide previously shown results.
     */
    private void updatePredictUIIdle() {
        if (tvPredictHint != null) tvPredictHint.setText("Choose at least 2 symptoms to start prediction.");
        if (predictBox != null) { predictBox.setVisibility(View.GONE); predictBox.removeAllViews(); }
        if (btnContinue != null) btnContinue.setVisibility(View.GONE);
    }

    // =========================
    // ✅ Open Summary Screen
    // =========================
    /**
     * Build a small payload with:
     * - chosen symptoms
     * - entered vitals (only those the user typed)
     * - demographics (age/gender)
     * - top 3 predictions (if available)
     *
     * Then start ConsultationSummaryActivity with the JSON payload.
     */
    private void openSummary() {
        if (lastPredictItems == null || lastPredictItems.isEmpty()) {
            Toast.makeText(this, "No prediction yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        PredictionSummaryPayload payload = new PredictionSummaryPayload();
        payload.symptoms = new ArrayList<>(selectedSymptoms);

        // only include vitals the user explicitly entered
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

    // ===== Collect entered vitals only =====
    /**
     * Return a map with only the vitals the user typed into (no empty fields).
     * Uses UiUtils helpers to avoid repetitive parsing code.
     */
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

    // =========================
    // Suggestions
    // =========================
    /**
     * Convert PredictResponseItem list into the local DiseaseProb model expected by SuggestionManager.
     */
    private List<DiseaseProb> toDiseaseProb(List<PredictResponseItem> items) {
        ArrayList<DiseaseProb> out = new ArrayList<>();
        if (items == null) return out;

        for (PredictResponseItem r : items) {
            out.add(new DiseaseProb(r.disease, r.probability));
        }
        return out;
    }

    /**
     * Ask the server for suggestions based on selected symptoms and top diseases.
     * If there aren't enough symptoms, we hide suggestions.
     */
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

    /**
     * Render server suggestions as chips. Each chip click adds the suggestion to selected symptoms.
     */
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

    // BottomSheet helpers
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
        // If vitals sheet is open, close it instead of leaving the screen. This feels nicer UX-wise.
        if (vitalsBehavior != null && vitalsBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
            closeVitals();
            return;
        }
        super.onBackPressed();
    }

    /**
     * Validate numeric vitals using UiUtils helpers.
     * Each field toggles its card stroke color on error and focuses the offending field.
     * The method returns a single boolean and shows one toast if any field is invalid.
     */
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
}
