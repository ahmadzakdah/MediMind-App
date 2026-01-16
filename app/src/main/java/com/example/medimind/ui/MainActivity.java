package com.example.medimind.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.example.medimind.R;
import com.example.medimind.ui.fragments.HomeFragment;
import com.example.medimind.ui.fragments.PatientsFragment;
import com.example.medimind.ui.fragments.ProfileFragment;

public class

MainActivity extends AppCompatActivity {

    private final int activeColor = Color.parseColor("#3D008F");
    private final int inactiveColor = Color.parseColor("#999999");

    // indicators
    private View indicatorHome, indicatorPatients, indicatorProfile;

    // icons (لازم تكون ضايف ids بالـ activity_base.xml)
    private ImageView iconHome, iconPatients, iconProfile;

    // texts (لازم تكون ضايف ids بالـ activity_base.xml)
    private TextView textHome, textPatients, textProfile;

    private TextView tvScreenTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindBottomNavViews();
        setupBottomNavClicks();

        tvScreenTitle = findViewById(R.id.tvScreenTitle);

        if (savedInstanceState == null) {
            openTab(Tab.HOME);
        }
    }

    private void bindBottomNavViews() {
        indicatorHome = findViewById(R.id.indicatorHome);
        indicatorPatients = findViewById(R.id.indicatorPatients);
        indicatorProfile = findViewById(R.id.indicatorProfile);

        iconHome = findViewById(R.id.iconHome);
        iconPatients = findViewById(R.id.iconPatients);
        iconProfile = findViewById(R.id.iconProfile);

        textHome = findViewById(R.id.textHome);
        textPatients = findViewById(R.id.textPatients);
        textProfile = findViewById(R.id.textProfile);
    }

    private void setupBottomNavClicks() {
        findViewById(R.id.navHome).setOnClickListener(v -> openTab(Tab.HOME));
        findViewById(R.id.navPatients).setOnClickListener(v -> openTab(Tab.PATIENTS));
        findViewById(R.id.navProfile).setOnClickListener(v -> openTab(Tab.PROFILE));
    }

    private enum Tab { HOME, PATIENTS, PROFILE }

    private void openTab(Tab tab) {
        Fragment fragment;
        String title;

        switch (tab) {
            case PATIENTS:
                fragment = new PatientsFragment();
                title = "Patients";
                break;
            case PROFILE:
                fragment = new ProfileFragment();
                title = "Profile";
                break;
            case HOME:
            default:
                fragment = new HomeFragment();
                title = "Home";
                break;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(
                        R.anim.fade_in, R.anim.fade_out,
                        R.anim.fade_in, R.anim.fade_out
                )
                .replace(R.id.baseContainer, fragment)
                .commit();


        // Update title
        if (tvScreenTitle != null) tvScreenTitle.setText(title);

        // Update bottom nav colors/indicators
        highlight(tab);
    }

    private void highlight(Tab tab) {
        // indicators
        if (indicatorHome != null) indicatorHome.setVisibility(tab == Tab.HOME ? View.VISIBLE : View.INVISIBLE);
        if (indicatorPatients != null) indicatorPatients.setVisibility(tab == Tab.PATIENTS ? View.VISIBLE : View.INVISIBLE);
        if (indicatorProfile != null) indicatorProfile.setVisibility(tab == Tab.PROFILE ? View.VISIBLE : View.INVISIBLE);

        // icons tint
        tint(iconHome, tab == Tab.HOME ? activeColor : inactiveColor);
        tint(iconPatients, tab == Tab.PATIENTS ? activeColor : inactiveColor);
        tint(iconProfile, tab == Tab.PROFILE ? activeColor : inactiveColor);

        // texts color
        if (textHome != null) textHome.setTextColor(tab == Tab.HOME ? activeColor : inactiveColor);
        if (textPatients != null) textPatients.setTextColor(tab == Tab.PATIENTS ? activeColor : inactiveColor);
        if (textProfile != null) textProfile.setTextColor(tab == Tab.PROFILE ? activeColor : inactiveColor);
    }

    private void tint(ImageView iv, int color) {
        if (iv == null) return;
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(color));
    }
}
