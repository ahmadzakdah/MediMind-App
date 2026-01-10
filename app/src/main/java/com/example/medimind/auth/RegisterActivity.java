package com.example.medimind.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
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

    // Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        // ربط العناصر
        tilName            = findViewById(R.id.tilName);
        tilEmail           = findViewById(R.id.tilEmail);
        tilPassword        = findViewById(R.id.tilPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilPhone           = findViewById(R.id.tilPhone);

        etName             = findViewById(R.id.etName);
        etEmail            = findViewById(R.id.etEmail);
        etPassword         = findViewById(R.id.etPassword);
        etConfirmPassword  = findViewById(R.id.etConfirmPassword);
        etPhone            = findViewById(R.id.etPhone);

        btnSignUp   = findViewById(R.id.btnSignUp);
        tvBackLogin = findViewById(R.id.tvBackLogin);

        btnSignUp.setOnClickListener(v -> tryRegister());

        tvBackLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
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

        // Name
        if (TextUtils.isEmpty(name)) {
            tilName.setError("Enter your full name");
            ok = false;
        }

        // Email
        if (TextUtils.isEmpty(email) ||
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email");
            ok = false;
        }

        // Password checks
        if (TextUtils.isEmpty(pass)) {
            tilPassword.setError("Password is required");
            ok = false;
        } else if (pass.length() < 8) {
            tilPassword.setError("At least 8 characters");
            ok = false;
        } else if (!pass.matches(".*[a-z].*")) {
            tilPassword.setError("Must contain lowercase letter");
            ok = false;
        } else if (!pass.matches(".*[A-Z].*")) {
            tilPassword.setError("Must contain uppercase letter");
            ok = false;
        } else if (!pass.matches(".*[0-9].*")) {
            tilPassword.setError("Must contain a number");
            ok = false;
        } else if (!pass.matches(".*[^a-zA-Z0-9].*")) {
            tilPassword.setError("Must contain a symbol");
            ok = false;
        }

        // Confirm password
        if (TextUtils.isEmpty(passConf)) {
            tilConfirmPassword.setError("Confirm your password");
            ok = false;
        } else if (!passConf.equals(pass)) {
            tilConfirmPassword.setError("Passwords do not match");
            ok = false;
        }

        // Phone
        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError("Phone is required");
            ok = false;
        } else if (phone.length() < 7) { // فحص بسيط
            tilPhone.setError("Enter a valid phone");
            ok = false;
        }

        if (!ok) return;

        btnSignUp.setEnabled(false);

        // إنشاء الحساب في Firebase Auth
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    btnSignUp.setEnabled(true);

                    if (!task.isSuccessful()) {
                        String msg = task.getException() != null ?
                                task.getException().getMessage() : "Registration failed";
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) {
                        Toast.makeText(this, "Registration error, please try again", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // نحدّث الاسم في البروفايل (FirebaseAuth)
                    UserProfileChangeRequest profileUpdates =
                            new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                    user.updateProfile(profileUpdates);

                    // نخزّن البيانات في Firestore (وفيها رقم التلفون)
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    Map<String, Object> userData = new HashMap<>();
                    userData.put("name", name);
                    userData.put("email", email);
                    userData.put("phone", phone);      // 👈 هون بنخزن رقم التلفون
                    userData.put("role", "doctor");
                    userData.put("created_at", System.currentTimeMillis());

                    db.collection("doctors")
                            .document(user.getUid())
                            .set(userData)
                            .addOnSuccessListener(a -> {
                                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                                FirebaseAuth.getInstance().signOut(); // ضروري حتى يرجّع المستخدم لتسجيل الدخول
                                startActivity(new Intent(this, LoginActivity.class));
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Firestore error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });



                })
                .addOnFailureListener(e -> {
                    btnSignUp.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void clearErrors() {
        tilName.setError(null);
        tilEmail.setError(null);
        tilPassword.setError(null);
        tilConfirmPassword.setError(null);
        tilPhone.setError(null);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
