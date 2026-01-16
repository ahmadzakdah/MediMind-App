package com.example.medimind.ui.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.medimind.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SymptomSearchAdapter extends ArrayAdapter<String> {

    public interface QueryProvider {
        String getQuery();
    }

    private final QueryProvider queryProvider;

    public SymptomSearchAdapter(
            @NonNull Context context,
            @NonNull List<String> items,
            @NonNull QueryProvider queryProvider
    ) {
        super(context, R.layout.item_symptom_dropdown, R.id.tvItem, new ArrayList<>(items));
        this.queryProvider = queryProvider;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = super.getView(position, convertView, parent);

        TextView tv = v.findViewById(R.id.tvItem);
        TextView sub = v.findViewById(R.id.tvSub);

        String raw = getItem(position);
        if (raw == null) return v;

        String query = queryProvider != null ? queryProvider.getQuery() : "";
        String display = pretty(raw);

        tv.setText(makeHighlighted(display, query));

        if (sub != null) {
            sub.setText("Symptom • tap to add");
        }

        return v;
    }


    public void updateItems(List<String> items) {
        clear();
        addAll(items);
        notifyDataSetChanged();
    }

    public static String pretty(String raw) {
        String s = raw.replace("_", " ").trim();
        if (s.isEmpty()) return s;

        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) out.append(p.substring(1));
            out.append(" ");
        }
        return out.toString().trim();
    }


    public static CharSequence makeHighlighted(String text, String query) {
        if (query == null) query = "";
        query = query.trim();
        if (query.isEmpty()) return text;

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        int start = lowerText.indexOf(lowerQuery);
        if (start < 0) return text;

        int end = start + lowerQuery.length();

        SpannableString sp = new SpannableString(text);
        sp.setSpan(new BackgroundColorSpan(0x224B0099), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }
}
