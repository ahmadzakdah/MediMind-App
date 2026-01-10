package com.example.medimind.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.medimind.R;
import com.example.medimind.ui.MainActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvSignup, tvForgot, tvLoginError;
    private ProgressBar pbLogin;

    private FirebaseAuth mAuth;
    private Animation shakeAnim;

    private GoogleSignInClient googleClient;
    private static final int RC_GOOGLE = 100;
    private ImageView btnGoogle;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            goMain();
            return;
        }

        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignup = findViewById(R.id.tvSignup);
        tvForgot = findViewById(R.id.tvForgot);
        tvLoginError = findViewById(R.id.tvLoginError);
        btnGoogle = findViewById(R.id.btnGoogle);
        pbLogin = findViewById(R.id.pbLogin);

        shakeAnim = AnimationUtils.loadAnimation(this, R.anim.shake);

        btnLogin.setOnClickListener(v -> submit());
        tvSignup.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        tvForgot.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));

        setupGoogle();
        btnGoogle.setOnClickListener(v -> signInGoogle());
    }

    private void submit() {
        clearErrors();

        String email = textOf(etEmail);
        String pass = textOf(etPassword);

        boolean ok = true;

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email!");
            ok = false;
        }

        if (TextUtils.isEmpty(pass)) {
            tilPassword.setError("Enter password!");
            ok = false;
        }

        if (!ok) {
            showError("Please check your input");
            return;
        }

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        showLoading(false);
                        showError("Email or password is incorrect!");
                        return;
                    }
                    goMain();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    showError("Email or Password is incorrect");
                });

    }

    private void setupGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleClient = GoogleSignIn.getClient(this, gso);
    }

    private void signInGoogle() {
        showLoading(true);
        startActivityForResult(googleClient.getSignInIntent(), RC_GOOGLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GOOGLE) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount acc = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(acc);
            } catch (Exception e) {
                showLoading(false);
                showError("Google login failed");
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acc) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acc.getIdToken(), null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        showLoading(false);
                        showError("Google authentication failed!");
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    FirebaseFirestore db = FirebaseFirestore.getInstance();

                    db.collection("doctors").document(user.getUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    Map<String, Object> map = new HashMap<>();
                                    map.put("name", user.getDisplayName());
                                    map.put("email", user.getEmail());
                                    map.put("phone", "");
                                    map.put("role", "doctor");
                                    map.put("created_at", System.currentTimeMillis());

                                    db.collection("doctors").document(user.getUid()).set(map);
                                }

                                showLoading(false);
                                goMain();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                goMain();
                            });
                });
    }

    private void showError(String msg) {
        tvLoginError.setText(msg);
        tvLoginError.setVisibility(View.VISIBLE);
        tvLoginError.startAnimation(shakeAnim);
    }

    private void clearErrors() {
        tilEmail.setError(null);
        tilPassword.setError(null);
        tvLoginError.setVisibility(View.GONE);
    }

    private void showLoading(boolean show) {
        if (show) {
            pbLogin.setVisibility(View.VISIBLE);
            btnLogin.setVisibility(View.INVISIBLE); // 👈 لإخفائه تمامًا
            btnGoogle.setEnabled(false);
            tvForgot.setEnabled(false);
            tvSignup.setEnabled(false);
            tvLoginError.setVisibility(View.GONE);
        } else {
            pbLogin.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);
            btnGoogle.setEnabled(true);
            tvForgot.setEnabled(true);
            tvSignup.setEnabled(true);
        }
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void goMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
