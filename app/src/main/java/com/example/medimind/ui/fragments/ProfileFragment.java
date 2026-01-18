// java
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
import com.google.firebase.firestore.ListenerRegistration;

/**
 * ProfileFragment
 *
 * Purpose:
 * - Show basic doctor information (name, email, UID, phone).
 * - Display live counts for patients and consultations from Firestore stats.
 * - Provide quick actions:
 *   - copy UID to clipboard
 *   - view archived patients
 *   - logout
 *
 * Notes and UX decisions:
 * - When the user is not authenticated a friendly "Not logged in" state is shown
 *   and actions that require auth are disabled.
 * - Stats are observed with a snapshot listener and cleaned up to avoid leaks.
 * - Phone is loaded separately from the doctor's Firestore document; fallback is "—".
 * - Copy-to-clipboard only proceeds when a valid UID is shown.
 */
public class ProfileFragment extends Fragment {
    // Snapshot listener for the stats document; removed in onStop/onDestroy to avoid leaks.
    private ListenerRegistration statsReg;

    // Firebase helpers
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // UI references: doctor basic info
    private TextView tvDoctorName, tvDoctorEmail, tvDoctorUid, tvDoctorPhone;

    // UI references: stats
    private TextView tvPatientsCount, tvConsultationsCount, tvStatsHint;

    // Action buttons
    private ImageView btnCopyUid;
    private MaterialButton btnLogout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the fragment layout. All view binding happens in onViewCreated.
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Bind UI elements (use view.findViewById inside fragments)
        tvDoctorName = view.findViewById(R.id.tvDoctorName);
        tvDoctorEmail = view.findViewById(R.id.tvDoctorEmail);
        tvDoctorUid = view.findViewById(R.id.tvDoctorUid);
        tvDoctorPhone = view.findViewById(R.id.tvDoctorPhone);
        tvPatientsCount = view.findViewById(R.id.tvPatientsCount);
        tvConsultationsCount = view.findViewById(R.id.tvConsultationsCount);
        tvStatsHint = view.findViewById(R.id.tvStatsHint);
        Chip chipArchived = view.findViewById(R.id.chipArchivedPatients);

        // Archived patients bottom sheet — keep it scoped to child fragment manager.
        chipArchived.setOnClickListener(v -> {
            ArchivedPatientsSheet.newInstance()
                    .show(getChildFragmentManager(), "archived_patients");
        });

        // Start listening for stats updates
        loadStats();

        // Bind action buttons and render initial auth info
        btnCopyUid = view.findViewById(R.id.btnCopyUid);
        btnLogout = view.findViewById(R.id.btnLogout);

        renderDoctorAuth();        // fill name/email/uid from FirebaseAuth
        loadDoctorFromFirestore(); // load phone from Firestore document (best-effort)

        btnCopyUid.setOnClickListener(v -> copyUid());
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    // =========================
    // Auth basic info rendering
    // =========================
    /**
     * Renders minimal info from FirebaseAuth.
     * - If no user is present we display a clear "not logged in" state and disable logout.
     * - We keep phone as a placeholder ("—") until Firestore provides a value.
     */
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

        // Phone is populated from Firestore in loadDoctorFromFirestore()
        if (tvDoctorPhone != null)
            tvDoctorPhone.setText("—");

        if (btnLogout != null) btnLogout.setEnabled(true);
    }

    // =========================
    // Load doctor extra info from Firestore
    // =========================
    /**
     * Loads optional fields (like phone) from the doctor's Firestore document.
     * This is best-effort — failures or missing fields fallback to "—".
     */
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

                    // Change "phone" here if your field name differs
                    String phone = doc.getString("phone");
                    if (phone == null || phone.trim().isEmpty()) phone = "—";
                    tvDoctorPhone.setText(phone);
                })
                .addOnFailureListener(e -> {
                    // Keep UI stable on failure
                    if (tvDoctorPhone != null) tvDoctorPhone.setText("—");
                });
    }

    // =========================
    // Actions
    // =========================
    /**
     * Copy the displayed UID to clipboard.
     * - Validates that there is a non-empty UID before copying.
     * - Uses a simple toast for feedback.
     */
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

    /**
     * Confirm logout with a dialog so user doesn't accidentally sign out.
     * If confirmed we call doLogout().
     */
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

    // =========================
    // Stats listener
    // =========================
    /**
     * Attaches a snapshot listener to the doctor's `meta/stats` document.
     * - Displays "Updating…" while waiting for the first value.
     * - Safely removes any previous listener before attaching a new one.
     * - Updates the UI with safe fallbacks when fields are missing.
     */
    private void loadStats() {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        if (tvStatsHint != null) tvStatsHint.setText("Updating…");

        // Remove any existing listener to avoid duplicate callbacks and leaks
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

    /**
     * Perform logout and navigate to the login screen.
     * We clear the activity stack so the user cannot go back.
     */
    private void doLogout() {
        if (getContext() == null) return;

        FirebaseAuth.getInstance().signOut();

        Intent i = new Intent(getContext(), LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        requireActivity().overridePendingTransition(
                android.R.anim.fade_in,
                android.R.anim.fade_out
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        // Remove the stats listener when fragment is stopped to avoid leaks and unnecessary updates.
        if (statsReg != null) {
            statsReg.remove();
            statsReg = null;
        }
    }

}
