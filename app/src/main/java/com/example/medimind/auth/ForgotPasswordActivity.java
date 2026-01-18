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
        btnSend.setOnClickListener(v -> sendReset());


    }

    private void sendReset() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        tilEmail.setError(null);
        tvMsg.setVisibility(View.GONE);

        // ✅ Validate email
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Enter a valid email");
            tilEmail.startAnimation(shakeAnim);
            return;
        }

        btnSend.setEnabled(false);

        // ✅ Auth only
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    btnSend.setEnabled(true);

                    if (task.isSuccessful()) {
                        showMsgSuccess("If Email is yours, check your inbox!");
                    } else {
                        String msg = (task.getException() != null && task.getException().getMessage() != null)
                                ? task.getException().getMessage()
                                : "Something went wrong. Please try again.";

                        showMsgError("Server error: " + msg);
                    }
                });
    }

    private void showMsgSuccess(String msg) {
        tvMsg.setText(msg);
        tvMsg.setTextColor(getColor(android.R.color.holo_blue_dark));
        tvMsg.setVisibility(View.VISIBLE);
    }

    private void showMsgError(String msg) {
        tvMsg.setText(msg);
        tvMsg.setTextColor(getColor(android.R.color.holo_red_dark));
        tvMsg.setVisibility(View.VISIBLE);
        tvMsg.startAnimation(shakeAnim);
    }

}
