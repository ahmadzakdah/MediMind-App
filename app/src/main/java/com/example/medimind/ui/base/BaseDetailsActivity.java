package com.example.medimind.ui.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.medimind.R;

public abstract class BaseDetailsActivity extends AppCompatActivity {

    // كل شاشة تفاصيل ترجع عنوانها
    protected abstract String getScreenTitle();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base_details);

        TextView tv = findViewById(R.id.tvScreenTitle);
        if (tv != null) tv.setText(getScreenTitle());
    }

    protected void setContentLayout(@LayoutRes int layoutResId) {
        FrameLayout container = findViewById(R.id.detailsContainer);
        container.removeAllViews();
        LayoutInflater.from(this).inflate(layoutResId, container, true);
    }
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
    @Override
    public void startActivity(Intent i) {
        super.startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

}
