package com.example.medimind.auth;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.medimind.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    // TextInputLayouts
    private TextInputLayout tilName, tilEmail, tilPassword, tilConfirmPassword, tilPhone;
    // EditTexts
    private TextInputEditText etName, etEmail, etPassword, etConfirmPassword, etPhone;

    private Button btnSignUp;
    private TextView tvBackLogin;

    // Message + Loading
    private TextView tvServerMsg;
    private ProgressBar pb;

    // Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        // Bind views
        tilName            = findViewById(R.id.tilName);
        tilEmail           = findViewById(R.id.tilEmail);
        tilPassword        = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilPhone           = findViewById(R.id.tilPhone);

        etName            = findViewById(R.id.etName);
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etPhone           = findViewById(R.id.etPhone);

        btnSignUp   = findViewById(R.id.btnSignUp);
        tvBackLogin = findViewById(R.id.tvBackLogin);

        // Must exist in XML (add them under the button)
        tvServerMsg = findViewById(R.id.tvServerMsg);
        pb          = findViewById(R.id.pb);

        // Auto-clear errors + hide message while typing
        wireLiveValidation();

        btnSignUp.setOnClickListener(v -> tryRegister());

        tvBackLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void wireLiveValidation() {
        clearErrorOnType(etName, tilName);
        clearErrorOnType(etEmail, tilEmail);
        clearErrorOnType(etPassword, tilPassword);
        clearErrorOnType(etConfirmPassword, tilConfirmPassword);
        clearErrorOnType(etPhone, tilPhone);

        TextWatcher hideServerMsgWatcher = new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideServerMsg();
            }
        };

        etName.addTextChangedListener(hideServerMsgWatcher);
        etEmail.addTextChangedListener(hideServerMsgWatcher);
        etPassword.addTextChangedListener(hideServerMsgWatcher);
        etConfirmPassword.addTextChangedListener(hideServerMsgWatcher);
        etPhone.addTextChangedListener(hideServerMsgWatcher);
    }

    private void clearErrorOnType(TextInputEditText et, TextInputLayout til) {
        et.addTextChangedListener(new SimpleTextWatcher() {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (til.getError() != null) til.setError(null);
            }
        });
    }

    private void tryRegister() {
        clearErrors();

        String name     = textOf(etName);
        String email    = textOf(etEmail);
        String pass     = textOf(etPassword);
        String passConf = textOf(etConfirmPassword);
        String phone    = textOf(etPhone);

        boolean ok = true;

        // Track first invalid view to focus + shake + show friendly message
        View firstInvalid = null;
        String firstErrorMsg = null;

        // Name
        if (TextUtils.isEmpty(name)) {
            tilName.setError("Required");
            ok = false;
            if (firstInvalid == null) {
                firstInvalid = tilName;
                firstErrorMsg = "Please enter your full name.";
            }
        }

        // Email
        if (TextUtils.isEmpty(email) ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid");
            ok = false;
            if (firstInvalid == null) {
                firstInvalid = tilEmail;
                firstErrorMsg = "Enter a valid email address (example@domain.com).";
            }
        }

        // Password rules (same as code #1) + friendly explanation
        String passErr = validatePassword(pass);
        if (passErr != null) {
            tilPassword.setError("Invalid");
            ok = false;
            if (firstInvalid == null) {
                firstInvalid = tilPassword;
                firstErrorMsg = explainPasswordError(passErr);
            }
        }

        // Confirm password
        if (TextUtils.isEmpty(passConf)) {
            tilConfirmPassword.setError("Required");
            ok = false;
            if (firstInvalid == null) {
                firstInvalid = tilConfirmPassword;
                firstErrorMsg = "Please confirm your password.";
            }
        } else if (!passConf.equals(pass)) {
            tilConfirmPassword.setError("Mismatch");
            ok = false;
            if (firstInvalid == null) {
                firstInvalid = tilConfirmPassword;
                firstErrorMsg = "Passwords do not match. Please re-enter them.";
            }
        }

        // Phone
        String phoneErr = validatePhoneSimple(phone);
        if (phoneErr != null) {
            tilPhone.setError("Invalid");
            ok = false;
            if (firstInvalid == null) {
                firstInvalid = tilPhone;
                firstErrorMsg = "Enter a valid phone number (digits only).";
            }
        }

        if (!ok) {
            showServerMsg(firstErrorMsg != null ? firstErrorMsg : "Please fix the highlighted fields.");
            if (firstInvalid != null) {
                firstInvalid.requestFocus();
                shake(firstInvalid);
            }
            return;
        }

        setLoading(true);

        // Firebase Auth create account
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);
                        String msg = task.getException() != null ?
                                task.getException().getMessage() : "Registration failed";
                        showServerMsg(mapFirebaseAuthError(msg));
                        shake(btnSignUp);
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        setLoading(false);
                        showServerMsg("Registration error, please try again");
                        shake(btnSignUp);
                        return;
                    }

                    // Update display name
                    UserProfileChangeRequest profileUpdates =
                            new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                    user.updateProfile(profileUpdates);

                    // Save to Firestore (doctors collection)
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", name);
                    userData.put("email", email);
                    userData.put("phone", phone);
                    userData.put("role", "doctor");
                    userData.put("created_at", System.currentTimeMillis());

                    db.collection("doctors")
                            .document(user.getUid())
                            .set(userData)
                            .addOnSuccessListener(a -> {
                                setLoading(false);
                                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                                FirebaseAuth.getInstance().signOut();
                                startActivity(new Intent(this, LoginActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                setLoading(false);
                                showServerMsg("Firestore error: " + e.getMessage());
                                shake(btnSignUp);
                            });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showServerMsg("Error: " + e.getMessage());
                    shake(btnSignUp);
                });
    }

    private void setLoading(boolean loading) {
        if (pb != null) pb.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSignUp.setEnabled(!loading);
    }

    private void showServerMsg(String msg) {
        if (tvServerMsg == null) {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            return;
        }
        tvServerMsg.setText(msg);
        tvServerMsg.setVisibility(View.VISIBLE);
    }

    private void hideServerMsg() {
        if (tvServerMsg != null && tvServerMsg.getVisibility() == View.VISIBLE) {
            tvServerMsg.setText("");
            tvServerMsg.setVisibility(View.GONE);
        }
    }

    private void clearErrors() {
        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tilPhone.setError(null);
        hideServerMsg();
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    // Password validation
    private String validatePassword(String pass) {
        if (TextUtils.isEmpty(pass)) {
            return "Password is required";
        } else if (pass.length() < 8) {
            return "Password must be at least 8 characters";
        } else if (!pass.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        } else if (!pass.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        } else if (!pass.matches(".*[0-9].*")) {
            return "Password must contain at least one number";
        } else if (!pass.matches(".*[^a-zA-Z0-9].*")) {
            return "Password must contain at least one symbol";
        }
        return null;
    }

    // Friendly explanation for why password is invalid
    private String explainPasswordError(String passErr) {
        if (passErr == null) return "Password does not meet security requirements.";
        if (passErr.contains("required")) {
            return "Password is required.";
        }
        if (passErr.contains("at least 8")) {
            return "Password must be at least 8 characters long.";
        }
        if (passErr.contains("lowercase")) {
            return "Password must include at least one lowercase letter (a–z).";
        }
        if (passErr.contains("uppercase")) {
            return "Password must include at least one uppercase letter (A–Z).";
        }
        if (passErr.contains("number")) {
            return "Password must include at least one number (0–9).";
        }
        if (passErr.contains("symbol")) {
            return "Password must include at least one symbol such: ! @ # $ %";
        }
        return "Password does not meet security requirements.";
    }

    private String validatePhoneSimple(String phone) {
        if (TextUtils.isEmpty(phone)) return "Phone is required";

        String cleaned = phone.replaceAll("[\\s\\-()]", "");
        if (cleaned.startsWith("+")) cleaned = cleaned.substring(1);

        if (!cleaned.matches("\\d+")) return "Phone must contain digits only";
        if (cleaned.length() < 7) return "Enter a valid phone";
        return null;
    }

    // Friendly mapping (optional)
    private String mapFirebaseAuthError(String raw) {
        String msg = raw != null ? raw : "Registration failed";
        String lower = msg.toLowerCase();

        if (lower.contains("already in use") || lower.contains("already")) {
            return "Email already exists. Try logging in instead.";
        }
        if (lower.contains("badly formatted") || lower.contains("invalid")) {
            return "Invalid email format. Please check your email.";
        }
        if (lower.contains("network error")) {
            return "Network error. Please check your internet connection.";
        }
        if (lower.contains("password") && (lower.contains("weak") || lower.contains("least"))) {
            return "Weak password. Please use a stronger password.";
        }
        return msg;
    }

    // Shake animation
    private void shake(View v) {
        if (v == null) return;

        ObjectAnimator animator = ObjectAnimator.ofFloat(
                v,
                "translationX",
                0f, 12f, -12f, 10f, -10f, 6f, -6f, 0f
        );
        animator.setDuration(350);
        animator.start();
    }

    // Simple watcher to avoid implementing all methods every time
    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
