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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArchivedPatientsSheet extends BottomSheetDialogFragment {

    private FirebaseFirestore db;
    private ListenerRegistration reg;

    private RecyclerView rv;
    private LinearLayout empty;

    private PatientsAdapter adapter;

    public static ArchivedPatientsSheet newInstance() {
        return new ArchivedPatientsSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_archived_patients, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        rv = view.findViewById(R.id.rvArchivedPatients);
        empty = view.findViewById(R.id.emptyArchived);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new PatientsAdapter(
                patient -> {
                    // إذا بدك تفتح تفاصيل المريض حتى وهو مؤرشف، حط intent هون
                },
                patient -> confirmRestore(patient.mrn) // بنستخدم onArchive كـ Restore
        );

        rv.setAdapter(adapter);

        listenArchived();
    }

    private void listenArchived() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        if (reg != null) {
            reg.remove();
            reg = null;
        }

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
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String mrn = doc.getString("medicalRecordNumber");
                        String name = doc.getString("name");
                        String gender = doc.getString("gender");
                        Long ageL = doc.getLong("age");
                        int age = ageL == null ? 0 : ageL.intValue();

                        if (mrn == null || mrn.trim().isEmpty()) mrn = doc.getId();

                        list.add(new PatientsAdapter.PatientRow(mrn, name == null ? "—" : name,
                                gender == null ? "—" : gender, age));
                    }

                    adapter.setItems(list);
                    showEmpty(list.isEmpty());
                });
    }

    private void showEmpty(boolean isEmpty) {
        if (empty != null) empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (rv != null) rv.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

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
            // ✅ READS أولاً
            var pSnap = tr.get(patientRef);
            var sSnap = tr.get(statsRef);

            if (!pSnap.exists()) return null;

            Boolean archived = pSnap.getBoolean("archived");
            if (archived == null || !archived) return null; // already active

            // ✅ WRITES بعدين
            tr.update(patientRef,
                    "archived", false,
                    "archivedAt", FieldValue.delete()
            );

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
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }
}
