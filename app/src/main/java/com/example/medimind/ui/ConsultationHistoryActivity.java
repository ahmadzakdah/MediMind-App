package com.example.medimind.ui;

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

public class ConsultationHistoryActivity extends BaseDetailsActivity {

    public static final String EXTRA_MRN = "mrn";

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String mrn;

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

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ImageView btnBack = findViewById(R.id.btnBack);
        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView rv = findViewById(R.id.rvConsultations);

        if (tvTitle != null) tvTitle.setText("Consultation History");

        btnBack.setOnClickListener(v -> {
            finish();
        });

        mrn = getIntent().getStringExtra(EXTRA_MRN);
        if (mrn == null || mrn.trim().isEmpty()) {
            Toast.makeText(this, "Missing MRN", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mrn = mrn.trim();

        adapter = new ConsultationHistoryAdapter(items);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        load(tvEmpty);
    }

    private void load(TextView tvEmpty) {
        String uid = (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
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
                    items.clear();
                    for (var d : qs.getDocuments()) {
                        items.add(ConsultationDoc.from(d));
                    }
                    adapter.notifyDataSetChanged();

                    boolean empty = items.isEmpty();
                    if (tvEmpty != null) tvEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}
