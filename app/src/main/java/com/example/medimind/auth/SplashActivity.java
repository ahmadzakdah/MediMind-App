package com.example.medimind.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.medimind.R;
import com.example.medimind.ui.MainActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler().postDelayed(() -> {

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

            if (user != null) {
                // logged in → go to main
                System.out.println(user.getUid());
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } else {
                // not logged in → go to login
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
            }

            finish();

        }, 3000);  // 3 seconds

    }
}
