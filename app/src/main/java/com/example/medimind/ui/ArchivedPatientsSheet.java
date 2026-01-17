// java
package com.example.medimind.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.ui.HelperClasses.PatientsAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Bottom sheet listing archived patients for the current doctor.
 *
 * Responsibilities:
 * - Observe archived patient documents in Firestore and render them in a RecyclerView.
 * - Allow the user to restore a patient (toggle archived -> false) with a simple confirm dialog.
 * - Update the doctor's stats document atomically inside a Firestore transaction.
 *
 * Notes / UX decisions:
 * - We limit to 200 archived items to keep the UI snappy.
 * - ListenerRegistration is cleaned up in onDestroyView to avoid leaks.
 * - Restoration is guarded: if the patient doc no longer exists or is already active we bail out.
 */
public class ArchivedPatientsSheet extends BottomSheetDialogFragment {

    // Firestore instance and active snapshot listener registration.
    private FirebaseFirestore db;
    private ListenerRegistration reg;

    // UI elements: recycler view for results and a simple empty state view.
    private RecyclerView rv;
    private LinearLayout empty;

    // Adapter that renders patient rows and provides click callbacks.
    private PatientsAdapter adapter;

    public static ArchivedPatientsSheet newInstance() {
        return new ArchivedPatientsSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the sheet layout (kept minimal on purpose).
        return inflater.inflate(R.layout.sheet_archived_patients, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        rv = view.findViewById(R.id.rvArchivedPatients);
        empty = view.findViewById(R.id.emptyArchived);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Adapter: primary click is reserved for opening patient details (left empty),
        // secondary click (archive action) is reused here to perform a "restore".
        adapter = new PatientsAdapter(
                patient -> {
                    // If you want to allow opening archived patient details, put an Intent here.
                },
                patient -> confirmRestore(patient.mrn) // reuse onArchive callback to restore
        );

        rv.setAdapter(adapter);

        // Start listening to archived patients for the current user.
        listenArchived();
    }

    /**
     * Listen for archived patients under the authenticated doctor's collection.
     * - If the user is unauthenticated we simply return.
     * - Existing listener (if any) is removed before attaching a new one to avoid duplicates.
     */
    private void listenArchived() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        if (reg != null) {
            reg.remove();
            reg = null;
        }

        // Query archived patients ordered by nameLower for stable ordering.
        reg = db.collection("doctors")
                .document(uid)
                .collection("patients")
                .whereEqualTo("archived", true)
                .orderBy("nameLower")
                .limit(200)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snap == null || snap.isEmpty()) {
                        adapter.setItems(new ArrayList<>());
                        showEmpty(true);
                        return;
                    }

                    List<PatientsAdapter.PatientRow> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String mrn = doc.getString("medicalRecordNumber");
                        String name = doc.getString("name");
                        String gender = doc.getString("gender");
                        Long ageL = doc.getLong("age");
                        int age = ageL == null ? 0 : ageL.intValue();

                        // Fallback to document id when medicalRecordNumber is missing.
                        if (mrn == null || mrn.trim().isEmpty()) mrn = doc.getId();

                        list.add(new PatientsAdapter.PatientRow(
                                mrn,
                                name == null ? "—" : name,
                                gender == null ? "—" : gender,
                                age
                        ));
                    }

                    adapter.setItems(list);
                    showEmpty(list.isEmpty());
                });
    }

    /**
     * Toggle UI between empty state and list.
     */
    private void showEmpty(boolean isEmpty) {
        if (empty != null) empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    /**
     * Show a confirmation dialog before attempting a restore.
     * Keeps the user in control and avoids accidental restores.
     */
    private void confirmRestore(String mrn) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restore patient?")
                .setMessage("This will remove the patient from archive.")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Restore", (d, w) -> {
                    d.dismiss();
                    restorePatient(mrn);
                })
                .show();
    }

    /**
     * Restore a patient inside a Firestore transaction.
     *
     * Transaction steps:
     * 1) Read the patient document and stats document.
     * 2) If patient is archived -> update patient.archived=false and delete archivedAt.
     * 3) Ensure stats doc exists and increment patientsCount atomically.
     *
     * Safety notes:
     * - If patient document is missing or already active we return early.
     * - We initialize stats with safe defaults when missing.
     */
    private void restorePatient(String mrn) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        var patientRef = db.collection("doctors").document(uid)
                .collection("patients").document(mrn);

        var statsRef = db.collection("doctors").document(uid)
                .collection("meta").document("stats");

        db.runTransaction(tr -> {
            // Read phase
            var pSnap = tr.get(patientRef);
            var sSnap = tr.get(statsRef);

            if (!pSnap.exists()) return null;

            Boolean archived = pSnap.getBoolean("archived");
            if (archived == null || !archived) return null; // already active or malformed

            // Write phase: mark patient active and clear archivedAt timestamp
            tr.update(patientRef,
                    "archived", false,
                    "archivedAt", FieldValue.delete()
            );

            // Initialize stats doc if it doesn't exist
            if (!sSnap.exists()) {
                java.util.HashMap<String, Object> init = new java.util.HashMap<>();
                init.put("patientsCount", 0L);
                init.put("consultationsCount", 0L);
                tr.set(statsRef, init);
            }

            Long current = sSnap.getLong("patientsCount");
            if (current == null) current = 0L;

            long newVal = current + 1;

            tr.set(statsRef, new java.util.HashMap<String, Object>() {{
                put("patientsCount", newVal);
            }}, SetOptions.merge());
            return null;

        }).addOnSuccessListener(v ->
                Toast.makeText(requireContext(), "Restored ✅", Toast.LENGTH_SHORT).show()
        ).addOnFailureListener(e ->
                Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Remove snapshot listener to avoid memory leaks and unnecessary work.
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }
}
