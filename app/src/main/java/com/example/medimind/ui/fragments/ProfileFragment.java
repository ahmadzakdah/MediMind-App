package com.example.medimind.ui.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.medimind.R;
import com.example.medimind.auth.LoginActivity;
import com.example.medimind.ui.ArchivedPatientsSheet;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.AggregateQuerySnapshot;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;


public class ProfileFragment extends Fragment {
    private ListenerRegistration statsReg;


    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private TextView tvDoctorName, tvDoctorEmail, tvDoctorUid, tvDoctorPhone;
    private TextView tvPatientsCount, tvConsultationsCount, tvStatsHint;

    private ImageView btnCopyUid;
    private MaterialButton btnLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvDoctorName = view.findViewById(R.id.tvDoctorName);
        tvDoctorEmail = view.findViewById(R.id.tvDoctorEmail);
        tvDoctorUid = view.findViewById(R.id.tvDoctorUid);
        tvDoctorPhone = view.findViewById(R.id.tvDoctorPhone);
        tvPatientsCount = view.findViewById(R.id.tvPatientsCount);
        tvConsultationsCount = view.findViewById(R.id.tvConsultationsCount);
        tvStatsHint = view.findViewById(R.id.tvStatsHint);
        Chip chipArchived = view.findViewById(R.id.chipArchivedPatients);

        chipArchived.setOnClickListener(v -> {
            ArchivedPatientsSheet.newInstance()
                    .show(getChildFragmentManager(), "archived_patients");
        });


        loadStats();


        btnCopyUid = view.findViewById(R.id.btnCopyUid);
        btnLogout = view.findViewById(R.id.btnLogout);

        renderDoctorAuth();       // اسم/ايميل/uid من Auth
        loadDoctorFromFirestore(); // phone من Firestore

        btnCopyUid.setOnClickListener(v -> copyUid());
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    // =========================
    // ✅ Auth basic info
    // =========================
    private void renderDoctorAuth() {
        FirebaseUser u = (auth != null) ? auth.getCurrentUser() : null;
        if (u == null) {
            if (tvDoctorName != null) tvDoctorName.setText("—");
            if (tvDoctorEmail != null) tvDoctorEmail.setText("Not logged in");
            if (tvDoctorUid != null) tvDoctorUid.setText("—");
            if (tvDoctorPhone != null) tvDoctorPhone.setText("—");
            if (btnLogout != null) btnLogout.setEnabled(false);
            return;
        }

        String name = u.getDisplayName();
        String email = u.getEmail();
        String uid = u.getUid();

        if (tvDoctorName != null)
            tvDoctorName.setText((name == null || name.trim().isEmpty()) ? "Doctor" : name.trim());

        if (tvDoctorEmail != null)
            tvDoctorEmail.setText((email == null || email.trim().isEmpty()) ? "—" : email.trim());

        if (tvDoctorUid != null)
            tvDoctorUid.setText(uid);

        if (tvDoctorPhone != null)
            tvDoctorPhone.setText("—"); // رح نعبّيه من Firestore

        if (btnLogout != null) btnLogout.setEnabled(true);
    }

    // =========================
    // ✅ Firestore doctor info
    // =========================
    private void loadDoctorFromFirestore() {
        if (auth == null || db == null) return;

        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            if (tvDoctorPhone != null) tvDoctorPhone.setText("—");
            return;
        }

        db.collection("doctors")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (tvDoctorPhone == null) return;

                    // ✅ غيّر "phone" إذا اسم الحقل مختلف عندك
                    String phone = doc.getString("phone");
                    if (phone == null || phone.trim().isEmpty()) phone = "—";
                    tvDoctorPhone.setText(phone);
                })
                .addOnFailureListener(e -> {
                    if (tvDoctorPhone != null) tvDoctorPhone.setText("—");
                });
    }

    // =========================
    // Actions
    // =========================
    private void copyUid() {
        if (getContext() == null || tvDoctorUid == null) return;

        String uid = tvDoctorUid.getText() == null ? "" : tvDoctorUid.getText().toString().trim();
        if (uid.isEmpty() || uid.equals("—")) {
            Toast.makeText(getContext(), "UID is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("doctor_uid", uid));
            Toast.makeText(getContext(), "Copied ✅", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmLogout() {
        if (getContext() == null) return;

        new MaterialAlertDialogBuilder(getContext())
                .setTitle("Logout?")
                .setMessage("You will need to login again to access patients.")
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Logout", (d, w) -> {
                    d.dismiss();
                    doLogout();
                })
                .show();
    }
    private void loadStats() {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        if (tvStatsHint != null) tvStatsHint.setText("Updating…");

        // ✅ شيل أي listener قديم
        if (statsReg != null) {
            statsReg.remove();
            statsReg = null;
        }

        statsReg = db.collection("doctors")
                .document(uid)
                .collection("meta")
                .document("stats")
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) {
                        if (tvStatsHint != null) tvStatsHint.setText("Failed to load");
                        return;
                    }

                    long patients = 0;
                    long consults = 0;

                    Long p = doc.getLong("patientsCount");
                    Long c = doc.getLong("consultationsCount");
                    if (p != null) patients = p;
                    if (c != null) consults = c;

                    if (tvPatientsCount != null) tvPatientsCount.setText(String.valueOf(patients));
                    if (tvConsultationsCount != null) tvConsultationsCount.setText(String.valueOf(consults));
                    if (tvStatsHint != null) tvStatsHint.setText("Up to date ✅");
                });
    }


    private void doLogout() {
        if (getContext() == null) return;

        FirebaseAuth.getInstance().signOut();

        Intent i = new Intent(getContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
    @Override
    public void onStop() {
        super.onStop();
        if (statsReg != null) {
            statsReg.remove();
            statsReg = null;
        }
    }

}
