package com.safeg;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.safeg.models.DoVisitorPassReqMobile;
import com.safeg.models.GetConfigResponseItem;

public class StaticData {
    public static DoVisitorPassReqMobile request = new DoVisitorPassReqMobile();

    public static String base64_mykad = "";
    public static String base64_face = "";
    public static String base64_mysejahtera = "";
    public static String base64_vaccinationCertificate = "";


    public static boolean isForeigner = false;
    public static boolean collectCard = false;
    public static boolean invitation = false;

    public static GetConfigResponseItem moduleConfig = new GetConfigResponseItem();
}
