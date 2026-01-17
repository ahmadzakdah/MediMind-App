// java
package com.example.medimind.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.domain.ConsultationDoc;
import com.example.medimind.ui.adapters.ConsultationHistoryAdapter;
import com.example.medimind.ui.base.BaseDetailsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

/**
 * Screen that displays a chronological list of consultations for a single patient.
 *
 * Responsibilities:
 * - Read the patient's consultations from Firestore under doctors/{uid}/patients/{mrn}/consultations
 * - Order entries by `createdAt` descending so newest consultations appear first
 * - Render results using ConsultationHistoryAdapter and show an empty hint when there are none
 *
 * Edge cases handled:
 * - Missing or empty MRN passed via intent -> show a toast and close the screen
 * - Unauthenticated user -> show a toast and close the screen
 * - Firestore failures -> show a toast but keep the screen visible (so user can retry by re-opening)
 *
 * Note: This class is primarily UI orchestration; actual model mapping is delegated to ConsultationDoc.
 */
public class ConsultationHistoryActivity extends BaseDetailsActivity {

    // Intent extra key expected by callers (PatientInfoActivity, etc.)
    public static final String EXTRA_MRN = "mrn";

    // Firebase helpers used for simple read-only queries.
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // The patient MRN (trimmed). Required for loading consultations.
    private String mrn;

    // In-memory list backing the RecyclerView adapter. Keeps insertion order stable.
    private final ArrayList<ConsultationDoc> items = new ArrayList<>();
    private ConsultationHistoryAdapter adapter;

    @Override
    protected String getScreenTitle() {
        return "Consultation History";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentLayout(R.layout.activity_consultation_history);

        // Initialize Firebase instances (read-only usage)
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind basic UI elements
        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvConsultations);

        // Make the header explicit (defensive: layout might already set this)
        if (tvTitle != null) tvTitle.setText("Consultation History");

        // Back button closes the activity — simple UX expectation
        btnBack.setOnClickListener(v -> {
            finish();
        });

        // Read MRN from intent and validate early so we can bail out if missing.
        mrn = getIntent().getStringExtra(EXTRA_MRN);
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mrn = mrn.trim();

        // Setup RecyclerView with a linear layout and an adapter backed by `items`.
        adapter = new ConsultationHistoryAdapter(items);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // Kick off load of consultations (updates UI when complete)
        load(tvEmpty);
    }

    /**
     * Load consultations for the authenticated doctor and patient MRN.
     *
     * Flow:
     * - Ensure user is authenticated; if not show a toast and close the screen (no further work)
     * - Query the consultations subcollection ordering by createdAt desc
     * - On success: map documents to ConsultationDoc and refresh adapter; toggle empty hint
     * - On failure: show a toast describing the error
     *
     * `tvEmpty` is the TextView shown when there are no consultations; method toggles its visibility.
     */
    @SuppressLint("NotifyDataSetChanged")
    private void load(TextView tvEmpty) {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            // Unauthenticated: nothing we can do — show message and close to avoid a broken screen.
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("doctors")
                .document(uid)
                .collection("patients")
                .document(mrn)
                .collection("consultations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    // Replace local list contents with fresh data from Firestore.
                    items.clear();
                    for (var d : qs.getDocuments()) {
                        items.add(ConsultationDoc.from(d));
                    }
                    // Notify adapter that the backing list changed.
                    adapter.notifyDataSetChanged();

                    // Toggle the empty hint visibility based on whether we have items.
                    boolean empty = items.isEmpty();
                    if (tvEmpty != null) tvEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
                })
                .addOnFailureListener(e ->
                        // Surface failure to the user; keep the screen available for retry by navigation.
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
