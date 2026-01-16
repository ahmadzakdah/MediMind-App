package com.example.medimind.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.medimind.R;
import com.example.medimind.ui.PatientInfoActivity;
import com.example.medimind.ui.HelperClasses.PatientsAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PatientsFragment extends Fragment {

    private RecyclerView rvPatients;
    private LinearLayout empty;

    private PatientsAdapter adapter;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // ✅ realtime listener
    private ListenerRegistration reg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_patients, container, false);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvPatients = view.findViewById(R.id.rvPatientsAll);
        empty = view.findViewById(R.id.layoutEmptyPatients);

        rvPatients.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new PatientsAdapter(
                p -> {
                    Intent i = new Intent(requireContext(), PatientInfoActivity.class);
                    i.putExtra("mrn", p.mrn);
                    startActivity(i);
                    requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                },
                p -> archivePatient(p.mrn)
        );

        rvPatients.setAdapter(adapter);

        // ✅ realtime
        attachPatientsListener();

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        // ✅ وقف الليسنر لما تطلع من الصفحة
        if (reg != null) {
            reg.remove();
            reg = null;
        }
    }

    private void attachPatientsListener() {
        String doctorUid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (doctorUid == null) {
            Toast.makeText(requireContext(), "Please login first.", Toast.LENGTH_SHORT).show();
            showEmpty();
            return;
        }

        // لو كان في listener قديم شيله
        if (reg != null) {
            reg.remove();
            reg = null;
        }

        reg = db.collection("doctors")
                .document(doctorUid)
                .collection("patients")
                .whereEqualTo("archived", false)
                .orderBy("nameLower")
                .limit(200)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        Toast.makeText(requireContext(), "Failed: " + (e != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show();
                        showEmpty();
                        return;
                    }

                    if (snap.isEmpty()) {
                        adapter.setItems(new ArrayList<>());
                        showEmpty();
                        return;
                    }

                    List<PatientsAdapter.PatientRow> list = new ArrayList<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        String mrn = doc.getString("medicalRecordNumber");
                        if (mrn == null || mrn.trim().isEmpty()) mrn = doc.getId();

                        String name = doc.getString("name");
                        String gender = doc.getString("gender");
                        Long ageL = doc.getLong("age");
                        int age = (ageL != null) ? ageL.intValue() : 0;

                        list.add(new PatientsAdapter.PatientRow(
                                mrn,
                                name != null ? name : ("MRN " + mrn),
                                gender != null ? gender : "—",
                                age
                        ));
                    }

                    adapter.setItems(list);
                    showList();
                });
    }

    private void showEmpty() {
        empty.setVisibility(View.VISIBLE);
        rvPatients.setVisibility(View.GONE);
    }

    private void showList() {
        empty.setVisibility(View.GONE);
        rvPatients.setVisibility(View.VISIBLE);
    }

    private void archivePatient(String mrn) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        var patientRef = db.collection("doctors").document(uid)
                .collection("patients").document(mrn);

        var statsRef = db.collection("doctors").document(uid)
                .collection("meta").document("stats");

        db.runTransaction(tr -> {
            // ✅ 1) READS أولاً
            var pSnap = tr.get(patientRef);
            var sSnap = tr.get(statsRef);

            if (!pSnap.exists()) return null;

            Boolean archived = pSnap.getBoolean("archived");
            if (archived != null && archived) return null; // already archived

            // ✅ 2) بعد القراءات: WRITES
            tr.update(patientRef,
                    "archived", true,
                    "archivedAt", FieldValue.serverTimestamp()
            );

            // إذا stats مش موجودة، أنشئها
            if (!sSnap.exists()) {
                java.util.HashMap<String, Object> init = new java.util.HashMap<>();
                init.put("patientsCount", 0L);
                init.put("consultationsCount", 0L);
                tr.set(statsRef, init);
            }

            Long current = sSnap.getLong("patientsCount");
            if (current == null) current = 0L;

            long newVal = Math.max(0L, current - 1);

            // بدل FieldValue.increment(-1) استخدم set بقيمة محسوبة
            tr.set(statsRef, new java.util.HashMap<String, Object>() {{
                put("patientsCount", newVal);
            }}, SetOptions.merge());


            return null;
        }).addOnSuccessListener(v ->
                Toast.makeText(requireContext(), "Archived ✅", Toast.LENGTH_SHORT).show()
        ).addOnFailureListener(e ->
                Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }


}
