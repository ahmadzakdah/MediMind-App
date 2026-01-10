package com.example.medimind.ui.base;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;

import com.example.medimind.R;
import com.example.medimind.ui.PatientsActivity;
import com.example.medimind.ui.SearchActivity;
import com.example.medimind.ui.ProfileActivity;

public abstract class BaseActivity extends AppCompatActivity {

    // كل صفحة رح ترجع ID الزر النشط (Home/Patients/Profile)
    protected abstract int getActiveNavId();

    // اختياري: كل صفحة ترجع عنوانها
    protected String getScreenTitle() {
        return null; // لو رجّعت null ما بغيّر العنوان
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);

        setupBottomNav();
        highlightActiveNav();
        updateScreenTitleIfNeeded();
    }

    protected void setContentLayout(int layoutResId) {
        FrameLayout container = findViewById(R.id.baseContainer);
        container.removeAllViews();
        LayoutInflater.from(this).inflate(layoutResId, container, true);
    }

    private void setupBottomNav() {

        findViewById(R.id.navHome).setOnClickListener(v -> {
            if (getActiveNavId() != R.id.navHome) {
                startActivity(new Intent(this, SearchActivity.class));
                finish();
            }
        });

        findViewById(R.id.navPatients).setOnClickListener(v -> {
            if (getActiveNavId() != R.id.navPatients) {
                startActivity(new Intent(this, PatientsActivity.class));
                finish();
            }
        });


        findViewById(R.id.navProfile).setOnClickListener(v -> {
            if (getActiveNavId() != R.id.navProfile) {
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
            }
        });
    }

    private void updateScreenTitleIfNeeded() {
        String title = getScreenTitle();
        if (title != null) {
            TextView tvTitle = findViewById(R.id.tvScreenTitle);
            if (tvTitle != null) tvTitle.setText(title);
        }
    }

    private void highlightActiveNav() {

        final int activeColor = Color.parseColor("#3D008F");
        final int inactiveColor = Color.parseColor("#999999");

        int activeId = getActiveNavId();

        // Indicators
        View indicatorHome = findViewById(R.id.indicatorHome);
        View indicatorPatients = findViewById(R.id.indicatorPatients);
        View indicatorProfile = findViewById(R.id.indicatorProfile);

        if (indicatorHome != null) indicatorHome.setVisibility(activeId == R.id.navHome ? View.VISIBLE : View.INVISIBLE);
        if (indicatorPatients != null) indicatorPatients.setVisibility(activeId == R.id.navPatients ? View.VISIBLE : View.INVISIBLE);
        if (indicatorProfile != null) indicatorProfile.setVisibility(activeId == R.id.navProfile ? View.VISIBLE : View.INVISIBLE);

        // Icons (لازم تكون ضايف ids: iconHome / iconPatients / iconProfile)
        ImageView iconHome = findViewById(R.id.iconHome);
        ImageView iconPatients = findViewById(R.id.iconPatients);
        ImageView iconProfile = findViewById(R.id.iconProfile);

        if (iconHome != null) {
            ImageViewCompat.setImageTintList(iconHome,
                    ColorStateList.valueOf(activeId == R.id.navHome ? activeColor : inactiveColor));
        }
        if (iconPatients != null) {
            ImageViewCompat.setImageTintList(iconPatients,
                    ColorStateList.valueOf(activeId == R.id.navPatients ? activeColor : inactiveColor));
        }
        if (iconProfile != null) {
            ImageViewCompat.setImageTintList(iconProfile,
                    ColorStateList.valueOf(activeId == R.id.navProfile ? activeColor : inactiveColor));
        }

        // Texts (لازم تكون ضايف ids: textHome / textPatients / textProfile)
        TextView textHome = findViewById(R.id.textHome);
        TextView textPatients = findViewById(R.id.textPatients);
        TextView textProfile = findViewById(R.id.textProfile);

        if (textHome != null) textHome.setTextColor(activeId == R.id.navHome ? activeColor : inactiveColor);
        if (textPatients != null) textPatients.setTextColor(activeId == R.id.navPatients ? activeColor : inactiveColor);
        if (textProfile != null) textProfile.setTextColor(activeId == R.id.navProfile ? activeColor : inactiveColor);
    }
}
