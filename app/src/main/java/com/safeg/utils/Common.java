package com.safeg.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Pair;
import android.widget.Toast;

import com.safeg.facedetection.SimilarityClassifier;

import java.util.HashMap;
import java.util.Map;

public class Common {
    public static void showToast(Context context, String msg){
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    public static void showDialog(Activity context, String msg, String title){
        new AlertDialog.Builder(context)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setCancelable(false)
                .setMessage(msg)
                .setTitle(title)
                .show();
    }

    public static Pair<String, Float> findNearest(float[] emb, HashMap<String, SimilarityClassifier.Recognition> registered) {

        if (registered == null || registered.isEmpty()) return null;

        // 1️⃣ Normalize the input embedding
        float[] normalizedEmb = l2Normalize(emb);

        Pair<String, Float> ret = null;

        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {

            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            // 2️⃣ Normalize the stored embedding
            float[] normalizedKnown = l2Normalize(knownEmb);

            // 3️⃣ Compute Euclidean (L2) distance
            float distance = 0f;
            for (int i = 0; i < normalizedEmb.length; i++) {
                float diff = normalizedEmb[i] - normalizedKnown[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);

            // 4️⃣ Update nearest face
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }

        return ret;
    }


    public static Pair<String, Float> findNearest1(
            float[] emb,
            HashMap<String, SimilarityClassifier.Recognition> registered) {

        if (registered == null || registered.isEmpty()) return null;

        // 1️⃣ Normalize the probe embedding (input)
        float[] normalizedEmb = l2Normalize(emb);

        String bestName = null;
        float bestSim = -1f; // cosine similarity, higher = better

        // 2️⃣ Loop over all registered faces
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            String name = entry.getKey();
            float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            // Normalize the stored embedding
            float[] normalizedKnown = l2Normalize(knownEmb);

            // 3️⃣ Compute cosine similarity (dot product of normalized vectors)
            float sim = cosineSimilarity(normalizedEmb, normalizedKnown);

            if (sim > bestSim) {
                bestSim = sim;
                bestName = name;
            }
        }

        // Return the name and similarity (higher = more similar)
        return new Pair<>(bestName, bestSim);
    }

// --- Helper functions ---

    // Cosine similarity between two normalized vectors
    private static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot; // range: -1 to 1
    }

    // L2 normalization (makes vector unit length)
    private static float[] l2Normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += x * x;
        double norm = Math.sqrt(sum);
        if (norm == 0.0) return v;

        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = (float) (v[i] / norm);
        return out;
    }


    public static int parseInt(String str) throws Throwable{
        return Integer.parseInt(str);
    }
}
