// java
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

/**
 * MainActivity
 *
 * Responsibilities:
 * - Host three main sections (Home, Patients, Profile) via fragments.
 * - Provide a simple bottom navigation UI with icons, labels and small indicators.
 * - Animate fragment transitions and keep the title in the header in sync with the selected tab.
 *
 * Notes and UX decisions:
 * - Uses solid colors parsed at startup for active/inactive states to keep theming simple.
 * - `View.INVISIBLE` is used for indicators to keep layout stable while hiding them.
 * - Fragment transactions replace the container; state restoration relies on the
 *   FragmentManager and savedInstanceState (we only open the default tab when there is no saved state).
 * - All view lookups are defensive (null-checked) to avoid crashes if layout changes.
 */
public class MainActivity extends AppCompatActivity {

    // Colors for active/inactive bottom nav elements (kept as final constants)
    private final int activeColor = Color.parseColor("#3D008F");
    private final int inactiveColor = Color.parseColor("#999999");

    // small colored bars under the icons to indicate the active tab
    private View indicatorHome, indicatorPatients, indicatorProfile;

    // icons in the bottom nav (ensure these ids exist in activity_main / activity_base)
    private ImageView iconHome, iconPatients, iconProfile;

    // labels under icons
    private TextView textHome, textPatients, textProfile;

    // header title shown above the fragment container
    private TextView tvScreenTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind bottom navigation views and wire clicks
        bindBottomNavViews();
        setupBottomNavClicks();

        tvScreenTitle = findViewById(R.id.tvScreenTitle);

        // Only open default tab when activity is created fresh.
        // If savedInstanceState is non-null the FragmentManager already restored fragments.
        if (savedInstanceState == null) {
            openTab(Tab.HOME);
        }
    }

    /**
     * Cache views used by the bottom navigation.
     * Keeping this centralized makes it easier to update if the layout changes.
     */
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

    /**
     * Attach click listeners to bottom nav items.
     * Simple lambda calls to openTab keep navigation logic centralized.
     */
    private void setupBottomNavClicks() {
        findViewById(R.id.navHome).setOnClickListener(v -> openTab(Tab.HOME));
        findViewById(R.id.navPatients).setOnClickListener(v -> openTab(Tab.PATIENTS));
        findViewById(R.id.navProfile).setOnClickListener(v -> openTab(Tab.PROFILE));
    }

    // Simple enum for the three possible tabs — makes calls type-safe.
    private enum Tab { HOME, PATIENTS, PROFILE }

    /**
     * Replace the fragment container with the requested tab.
     *
     * Behavior notes:
     * - Uses replace + commit (not addToBackStack) so back returns out of the activity.
     * - Animations are small fade transitions for a minimal, fast feeling.
     * - Title and bottom nav visuals are updated after the fragment transaction is committed.
     */
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

        // Keep header title in sync with selected tab (defensive null-check)
        if (tvScreenTitle != null) tvScreenTitle.setText(title);

        // Update bottom navigation visuals to reflect the active tab
        highlight(tab);
    }

    /**
     * Update the bottom navigation UI:
     * - Show the appropriate indicator bar
     * - Tint icons based on active state
     * - Update label text colors
     *
     * Defensive null-checks are used so the method is safe if layout changes.
     */
    private void highlight(Tab tab) {
        // indicators (use INVISIBLE to keep layout spacing)
        if (indicatorHome != null) indicatorHome.setVisibility(tab == Tab.HOME ? View.VISIBLE : View.INVISIBLE);
        if (indicatorPatients != null) indicatorPatients.setVisibility(tab == Tab.PATIENTS ? View.VISIBLE : View.INVISIBLE);
        if (indicatorProfile != null) indicatorProfile.setVisibility(tab == Tab.PROFILE ? View.VISIBLE : View.INVISIBLE);

        // icons tint — helper wraps ImageViewCompat call
        tint(iconHome, tab == Tab.HOME ? activeColor : inactiveColor);
        tint(iconPatients, tab == Tab.PATIENTS ? activeColor : inactiveColor);
        tint(iconProfile, tab == Tab.PROFILE ? activeColor : inactiveColor);

        // label colors
        if (textHome != null) textHome.setTextColor(tab == Tab.HOME ? activeColor : inactiveColor);
        if (textPatients != null) textPatients.setTextColor(tab == Tab.PATIENTS ? activeColor : inactiveColor);
        if (textProfile != null) textProfile.setTextColor(tab == Tab.PROFILE ? activeColor : inactiveColor);
    }

    /**
     * Apply a tint to an ImageView in a safe way.
     * No-op when the ImageView reference is missing.
     */
    private void tint(ImageView iv, int color) {
        if (iv == null) return;
        ImageViewCompat.setImageTintList(iv, ColorStateList.valueOf(color));
    }
}
