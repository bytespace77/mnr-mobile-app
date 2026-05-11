package com.safeg.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.safeg.R;
import com.safeg.facedetection.SimilarityClassifier;

import java.util.HashMap;
import java.util.Map;

public class Common {

    // ✅ Toast types
    public enum ToastType {
        SUCCESS, ERROR, WARNING, INFO
    }

    // ✅ Default toast — INFO type
    public static void showToast(Context context, String msg) {
        showToast(context, msg, ToastType.INFO);
    }

    // ✅ Custom styled toast — top center of screen
    public static void showToast(Context context, String msg, ToastType type) {
        try {
            LayoutInflater inflater = LayoutInflater.from(context);
            View layout = inflater.inflate(R.layout.toast_custom, null, false);

            ImageView icon = layout.findViewById(R.id.ivToastIcon);
            TextView text = layout.findViewById(R.id.tvToastMessage);

            text.setText(msg);

            switch (type) {
                case SUCCESS:
                    icon.setImageResource(R.drawable.ic_toast_success);
                    icon.setColorFilter(Color.parseColor("#006948"));
                    break;
                case ERROR:
                    icon.setImageResource(R.drawable.ic_toast_error);
                    icon.setColorFilter(Color.parseColor("#BA1A1A"));
                    break;
                case WARNING:
                    icon.setImageResource(R.drawable.ic_toast_warning);
                    icon.setColorFilter(Color.parseColor("#FFD700"));
                    break;
                case INFO:
                default:
                    icon.setImageResource(R.drawable.ic_toast_info);
                    icon.setColorFilter(Color.parseColor("#FFFFFF"));
                    break;
            }

            Toast toast = new Toast(context);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(layout);
            // ✅ Top center position — 150dp from top
            toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 150);
            toast.show();
        } catch (Exception e) {
            // fallback to default toast
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showDialog(Activity context, String msg, String title) {
        new AlertDialog.Builder(context)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {}
                })
                .setCancelable(false)
                .setMessage(msg)
                .setTitle(title)
                .show();
    }

    public static Pair<String, Float> findNearest(float[] emb, HashMap<String, SimilarityClassifier.Recognition> registered) {
        if (registered == null || registered.isEmpty()) return null;
        float[] normalizedEmb = l2Normalize(emb);
        Pair<String, Float> ret = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
            float[] normalizedKnown = l2Normalize(knownEmb);
            float distance = 0f;
            for (int i = 0; i < normalizedEmb.length; i++) {
                float diff = normalizedEmb[i] - normalizedKnown[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }
        return ret;
    }

    public static Pair<String, Float> findNearest1(float[] emb, HashMap<String, SimilarityClassifier.Recognition> registered) {
        if (registered == null || registered.isEmpty()) return null;
        float[] normalizedEmb = l2Normalize(emb);
        String bestName = null;
        float bestSim = -1f;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            String name = entry.getKey();
            float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
            float[] normalizedKnown = l2Normalize(knownEmb);
            float sim = cosineSimilarity(normalizedEmb, normalizedKnown);
            if (sim > bestSim) {
                bestSim = sim;
                bestName = name;
            }
        }
        return new Pair<>(bestName, bestSim);
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;
    }

    private static float[] l2Normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0.0) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }

    public static int parseInt(String str) throws Throwable {
        return Integer.parseInt(str);
    }
}