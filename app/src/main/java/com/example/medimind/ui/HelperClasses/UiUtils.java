// java
package com.example.medimind.ui.HelperClasses;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.EditText;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.Map;

public final class UiUtils {

    public static final int STROKE_OK = Color.parseColor("#EDEAF5");
    public static final int STROKE_ERR = Color.parseColor("#E53935");
    private static final int PRIMARY = Color.parseColor("#4B0099");
    private static final int BG = Color.parseColor("#F4F1FF");

    private UiUtils() {}

    // Create a styled chip. onMainClick for normal click, onClose for close-icon click.
    public static Chip createStyledChip(Context ctx, String text, String tag,
                                        View.OnClickListener onMainClick,
                                        View.OnClickListener onClose) {
        Chip chip = new Chip(ctx);
        chip.setText(text);
        chip.setTag(tag);

        chip.setCloseIconVisible(onClose != null);
        chip.setCloseIconTint(ColorStateList.valueOf(PRIMARY));
        chip.setChipBackgroundColor(ColorStateList.valueOf(BG));
        chip.setChipStrokeColor(ColorStateList.valueOf(PRIMARY));
        chip.setChipStrokeWidth(1.5f);
        chip.setTextColor(PRIMARY);
        chip.setChipCornerRadius(18f);
        chip.setEnsureMinTouchTargetSize(false);

        if (onMainClick != null) chip.setOnClickListener(onMainClick);
        if (onClose != null) chip.setOnCloseIconClickListener(onClose);

        return chip;
    }

    // Put integer if field not empty and parsable
    public static void putIntIfPresent(Map<String, Object> out, String key, EditText et) {
        if (et == null) return;
        String s = et.getText() == null ? "" : et.getText().toString().trim();
        if (s.isEmpty()) return;
        try { out.put(key, Integer.parseInt(s)); } catch (Exception ignored) {}
    }

    // Put double if field not empty and parsable
    public static void putDoubleIfPresent(Map<String, Object> out, String key, EditText et) {
        if (et == null) return;
        String s = et.getText() == null ? "" : et.getText().toString().trim();
        if (s.isEmpty()) return;
        try { out.put(key, Double.parseDouble(s)); } catch (Exception ignored) {}
    }

    // Generic numeric validator: use isInteger true for int validation, false for double.
    // Sets card stroke color and requests focus on error.
    public static boolean validateNumberField(EditText et, MaterialCardView card,
                                              double min, double max, boolean isInteger) {
        if (card != null) card.setStrokeColor(STROKE_OK);
        if (et == null) return true;

        String s = et.getText() == null ? "" : et.getText().toString().trim();
        if (s.isEmpty()) return true; // optional field

        try {
            double v = isInteger ? Integer.parseInt(s) : Double.parseDouble(s);
            if (v < min || v > max) {
                if (card != null) card.setStrokeColor(STROKE_ERR);
                et.requestFocus();
                return false;
            }
            return true;
        } catch (Exception e) {
            if (card != null) card.setStrokeColor(STROKE_ERR);
            et.requestFocus();
            return false;
        }
    }
}
