package com.example.medimind.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Patterns;
import androidx.appcompat.app.AppCompatActivity;

import com.example.medimind.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputLayout tilEmail;
    private TextInputEditText etEmail;
    private TextView tvMsg, tvBack;
    private Button btnSend;
    private Animation shakeAnim;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        tilEmail = findViewById(R.id.tilEmail);
        etEmail = findViewById(R.id.etEmail);
        btnSend = findViewById(R.id.btnSend);
        tvMsg = findViewById(R.id.tvMsg);
        tvBack = findViewById(R.id.tvBack);
        tvBack.setOnClickListener(v -> {
            finish(); // يرجع خطوة للخلف بدون مشاكل
        });


        shakeAnim = AnimationUtils.loadAnimation(this, R.anim.shake);

        btnSend.setOnClickListener(v -> {

            tvMsg.setVisibility(View.GONE);
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

            if (email.isEmpty() ||
                    !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.setError("Enter a valid email");
                return;
            }

            tilEmail.setError(null);
            btnSend.setEnabled(false);

            // 1️⃣ نفحص إذا الإيميل مسجل بالداتا بيس
            FirebaseFirestore.getInstance()
                    .collection("doctors")
                    .whereEqualTo("email", email)
                    .get()
                    .addOnSuccessListener(q -> {
                        if (q.isEmpty()) {
                            // ❌ الايميل مش موجود في النظام
                            btnSend.setEnabled(true);
                            tvMsg.setText("This email is not registered");
                            tvMsg.setTextColor(getColor(android.R.color.holo_red_dark));
                            tvMsg.setVisibility(View.VISIBLE);
                        } else {

                            // 2️⃣ نرسل رابط الاسترجاع
                            FirebaseAuth.getInstance()
                                    .sendPasswordResetEmail(email)
                                    .addOnSuccessListener(a -> {
                                        btnSend.setEnabled(true);
                                        tvMsg.setText("Reset link sent! Check your inbox");
                                        tvMsg.setTextColor(getColor(android.R.color.holo_green_dark));
                                        tvMsg.setVisibility(View.VISIBLE);
                                    })
                                    .addOnFailureListener(e -> {
                                        btnSend.setEnabled(true);
                                        tvMsg.setText("Error: " + e.getMessage());
                                        tvMsg.setTextColor(getColor(android.R.color.holo_red_dark));
                                        tvMsg.setVisibility(View.VISIBLE);
                                    });

                        }
                    })
                    .addOnFailureListener(e -> {
                        btnSend.setEnabled(true);
                        tvMsg.setText("Server error: " + e.getMessage());
                        tvMsg.setTextColor(getColor(android.R.color.holo_red_dark));
                        tvMsg.setVisibility(View.VISIBLE);
                    });
        });

    }

    private void resetPassword() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        tilEmail.setError(null);
        tvMsg.setVisibility(View.GONE);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            tilEmail.setError("Enter a valid email");
            tilEmail.startAnimation(shakeAnim);
            return;
        }

        btnSend.setEnabled(false);

        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(a -> {
                    tvMsg.setText("Reset link sent to your email ✔️");
                    tvMsg.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    tvMsg.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    tvMsg.setText("Error: " + e.getMessage());
                    tvMsg.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    tvMsg.setVisibility(View.VISIBLE);
                    tvMsg.startAnimation(shakeAnim);
                })
                .addOnCompleteListener(r -> btnSend.setEnabled(true));
    }
}
