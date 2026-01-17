// java
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

/**
 * HomeFragment
 *
 * Purpose:
 * - Provide a simple search UI to find patients by name or MRN.
 * - Offer a shortcut to create a new medical record.
 * - Show a compact list of results or an empty state when nothing matches.
 *
 * Important notes:
 * - Search by MRN when the input is purely numeric.
 * - Name searches use a lowercased index field (`nameLower`) and a range query.
 * - All Firestore calls are scoped under the current doctor's document.
 * - If the user is not authenticated we show a friendly toast and abort the action.
 */
public class HomeFragment extends Fragment {
    // Search field container and the editable input.
    private TextInputLayout tilSearch;
    private TextInputEditText etSearch;
    private Button btnSearch, btnCreateRecord;

    // UI shown when there are no results vs. results list.
    private LinearLayout layoutEmptyState;
    private RecyclerView rvPatients;
    private PatientSearchAdapter adapter;

    // Firebase helpers
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    /**
     * Ensure a lightweight stats document exists for the current doctor.
     * We only create the document when it doesn't exist (avoid overwriting existing counters).
     * This method is forgiving: if auth is not ready we simply return.
     */
    private void ensureStatsDoc() {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        var statsRef = db.collection("doctors")
                .document(uid)
                .collection("meta")
                .document("stats");

        statsRef.get().addOnSuccessListener(snap -> {
            // if there is already data we don't touch it — we just ensure existence
            if (snap != null && snap.exists()) return;

            HashMap<String, Object> init = new HashMap<>();
            init.put("patientsCount", 0L);
            init.put("consultationsCount", 0L);
            statsRef.set(init);
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate fragment layout and bind views once
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // Initialize Firebase and ensure stats doc exists (best-effort)
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        ensureStatsDoc();

        // Bind views from inflated layout (note: use view.findViewById inside fragments)
        tilSearch = view.findViewById(R.id.tilSearch);
        etSearch = view.findViewById(R.id.etSearch);

        btnSearch = view.findViewById(R.id.btnSearch);
        btnCreateRecord = view.findViewById(R.id.btnCreateRecord);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);

        rvPatients = view.findViewById(R.id.rvPatients);
        rvPatients.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Adapter: clicking a patient opens PatientInfoActivity
        adapter = new PatientSearchAdapter(patient -> {
            Intent i = new Intent(requireContext(), PatientInfoActivity.class);
            i.putExtra("mrn", patient.mrn);
            startActivity(i);
            // small fade animation for nicer transition
            requireActivity().overridePendingTransition(
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );
        });

        rvPatients.setAdapter(adapter);

        // show empty state initially
        showEmptyState();

        // Search button: validate and delegate either to MRN or name search
        btnSearch.setOnClickListener(v -> {
            String input = textOf(etSearch);
            if (TextUtils.isEmpty(input)) {
                setFieldError("Enter patient name or MRN");
                showEmptyState();
                return;
            }
            clearFieldError();

            // Numeric input -> treat as MRN, otherwise treat as name query
            if (input.matches("\\d+")) {
                searchByMrn(input);
            } else {
                searchByName(input);
            }
        });

        // Create record shortcut
        btnCreateRecord.setOnClickListener(v -> {
            if (getActivity() == null) return;
            Intent intent = new Intent(getActivity(), CreateMedicalRecordActivity.class);
            startActivity(intent);
        });

        return view;
    }

    /**
     * Search a single patient document by MRN under the current doctor's collection.
     * On success we show at most one result; on failure we clear the list and show a toast.
     */
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

                    // Safe extraction with fallbacks
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

    /**
     * Search patients by name using a `nameLower` index field.
     * We do a prefix-range query (`startAt` / `endAt`) and limit results to 30 to keep UI snappy.
     */
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

    /** Show empty state and hide results list. */
    private void showEmptyState() {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.VISIBLE);
        if (rvPatients != null) rvPatients.setVisibility(View.GONE);
    }

    /** Show results list and hide empty state. */
    private void showResults() {
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (rvPatients != null) rvPatients.setVisibility(View.VISIBLE);
    }

    /**
     * Helper to safely extract trimmed text from a TextInputEditText.
     * Returns an empty string when the field is null or empty.
     */
    private String textOf(TextInputEditText et) {
        return (et.getText() != null) ? et.getText().toString().trim() : "";
    }

    /** Show a validation error on the search input. */
    private void setFieldError(String msg) {
        tilSearch.setErrorEnabled(true);
        tilSearch.setError(msg);
    }

    /** Clear any validation error on the search input. */
    private void clearFieldError() {
        tilSearch.setError(null);
        tilSearch.setErrorEnabled(false);
    }
}
