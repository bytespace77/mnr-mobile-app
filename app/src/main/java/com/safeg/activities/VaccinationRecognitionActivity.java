package com.safeg.activities;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.awesomedialog.blennersilva.awesomedialoglibrary.AwesomeInfoDialog;
import com.awesomedialog.blennersilva.awesomedialoglibrary.interfaces.Closure;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.safeg.R;
import com.safeg.StaticData;
import com.safeg.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class VaccinationRecognitionActivity extends AppCompatActivity {

    SurfaceView cameraView;
    TextView textView;
    CameraSource cameraSource;
    final int RequestCameraPermissionID = 1001;
    ImageView iv;
    Utils utils;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RequestCameraPermissionID: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    try {
                        cameraSource.start(cameraView.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vaccination_recognition);

        iv = (ImageView) findViewById(R.id.iv);
        cameraView = (SurfaceView) findViewById(R.id.surface_view);
        textView = (TextView) findViewById(R.id.text_view);
        utils = new Utils();

        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        BoxDetector boxDetector = new BoxDetector(textRecognizer, 200, 200);

        if (!textRecognizer.isOperational()) {
            Log.w("MainActivity", "Detector dependencies are not yet available");
        } else {
            cameraSource = new CameraSource.Builder(getApplicationContext(), boxDetector)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    //.setRequestedPreviewSize(800, 480)
                    .setRequestedPreviewSize(2160, 3840)
                    .setRequestedFps(10.0f)
                    .setAutoFocusEnabled(true)
                    .build();

            cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {

                    try {
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                            ActivityCompat.requestPermissions(VaccinationRecognitionActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    RequestCameraPermissionID);
                            return;
                        }
                        cameraSource.start(cameraView.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    cameraSource.stop();
                }
            });

            boxDetector.setProcessor(new Detector.Processor<TextBlock>() {

                @Override
                public void release() {
                    finish();
                    startActivity(new Intent(VaccinationRecognitionActivity.this, CardDetailsActivity.class));
                }

                @Override
                public void receiveDetections(Detector.Detections<TextBlock> visionText) {

                }
            });
        }

        new CountDownTimer(15000, 500) {

            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                try {
                    if (StaticData.request.ic.isEmpty() || StaticData.request.fullName.isEmpty()) {
                        showMyKadFailedToReadDialog(VaccinationRecognitionActivity.this);
                    }
                } catch (Throwable throwable) {

                }
            }
        }.start();


        findViewById(R.id.rlAddManually).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                startActivity(new Intent(getApplicationContext(), VaccinationDetailsActivity.class));
            }
        });


    }

    private String getDetectionText(Detector.Detections<TextBlock> detections) {
        final SparseArray<TextBlock> items = detections.getDetectedItems();
        StringBuilder stringBuilder = new StringBuilder();
        if (items.size() > 0) {
            for (int i = 0; i < items.size(); i++) {
                TextBlock item = items.valueAt(i);
                stringBuilder.append(item.getValue());
                stringBuilder.append("\n");
            }
        }
        return stringBuilder.toString();
    }


    public class BoxDetector extends Detector {
        private Detector mDelegate;
        private int mBoxWidth, mBoxHeight;

        public BoxDetector(Detector delegate, int boxWidth, int boxHeight) {
            mDelegate = delegate;
            mBoxWidth = boxWidth;
            mBoxHeight = boxHeight;
        }

        public SparseArray detect(Frame frame) {
            int width = frame.getMetadata().getWidth();
            int height = frame.getMetadata().getHeight();
//            int right = (width / 2) + (mBoxHeight / 2);
//            int left = (width / 2) - (mBoxHeight / 2);
//            int bottom = (height / 2) + (mBoxWidth / 2);
//            int top = (height / 2) - (mBoxWidth / 2);

            int right = width;
            int left = 0;
            int bottom = height;
            int top = 0;

            YuvImage yuvImage = new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(left, top, right, bottom), 100, byteArrayOutputStream);
            byte[] jpegArray = byteArrayOutputStream.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegArray, 0, jpegArray.length);

            Frame croppedFrame =
                    new Frame.Builder()
                            .setBitmap(bitmap)
                            .setRotation(frame.getMetadata().getRotation())
                            .build();

            Bitmap bmp = rotateBitmap(croppedFrame.getBitmap(), 90.0F);

            recognizeText(bmp, VaccinationRecognitionActivity.this);

            return mDelegate.detect(croppedFrame);
        }

        public Bitmap rotateBitmap(Bitmap source, Float angle) {
            try {
                Matrix matrix = new Matrix();
                matrix.postRotate(angle);

                Bitmap rotatedBitmap = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);

                // int _x1 = (int)((rotatedBitmap.getWidth() / 100F) * 17);
                // int _y1 = (int)(rotatedBitmap.getHeight() / 4.5F);
                // int _x2 = (int)((rotatedBitmap.getWidth() / 100F) * 66);
                // int _y2 = (int)((rotatedBitmap.getHeight() / 4.5F) * 1.5F);

                // return Bitmap.createBitmap(rotatedBitmap, _x1, _y1, _x2, _y2, null, true);

                return rotatedBitmap;
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            return null;
        }

        public boolean isOperational() {
            return mDelegate.isOperational();
        }

        public boolean setFocus(int id) {
            return mDelegate.setFocus(id);
        }
    }

    private void recognizeText(Bitmap bitmap, AppCompatActivity context) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        com.google.mlkit.vision.text.TextRecognizer recognizer = getTextRecognizer();
        Task<Text> result = recognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text visionText) {
                        Log.d("Blocks Whole Text", visionText.getText());
                        if (StaticData.base64_mysejahtera.isEmpty()) {
                            if (utils.countLines(visionText.getText()) >= 5 &&
                                    (
                                            visionText.getText().toLowerCase().contains("covid-19 risk status") ||
                                                    visionText.getText().toLowerCase().contains("covid 19 risk status") ||
                                                    visionText.getText().toLowerCase().contains("covd 19 risk status")
                                    )
                                    &&
                                    (visionText.getText().toLowerCase().contains("covid-19 vaccination status") ||
                                            visionText.getText().toLowerCase().contains("covid 19 vaccination status") ||
                                            visionText.getText().toLowerCase().contains("covd 19 vaccination status")

                                    )


                            ) {
                                List<String> lines = utils.getLines(visionText.getText());

                                //if(lines.size() > 3 && lines.get(2).toLowerCase().contains(StaticData.request.fullName)){
                                //if (lines.size() > 3 && lines.get(2).toLowerCase().contains("Mohd wafiy")) {
                                if (visionText.getText().toLowerCase().contains("covid-19 risk status")
                                || visionText.getText().toLowerCase().contains("covid 19 risk status")
                                || visionText.getText().toLowerCase().contains("covd 19 risk status")
                                ) {
                                    for (int i = 0; i < lines.size(); i++) {
                                        if (lines.get(i).toLowerCase().contains("covid-19 risk status")
                                                || lines.get(i).toLowerCase().contains("covid 19 risk status")
                                                || lines.get(i).toLowerCase().contains("covd 19 risk status")


                                        ) {
                                            StaticData.request.riskStatus = lines.get(i + 1);
                                            break;
                                        }
                                    }
                                }

                                if (visionText.getText().toLowerCase().contains("covid-19 vaccination status")
                                        || visionText.getText().toLowerCase().contains("covid 19 vaccination status")
                                        || visionText.getText().toLowerCase().contains("covd 19 vaccination status")


                                ) {
                                    for (int i = 0; i < lines.size(); i++) {
                                        if (lines.get(i).toLowerCase().contains("covid-19 vaccination status")
                                        || lines.get(i).toLowerCase().contains("covid 19 vaccination status")
                                                || lines.get(i).toLowerCase().contains("covd 19 vaccination status")

                                        ) {
                                            StaticData.request.vaccinationStatus = lines.get(i + 1);
                                            if (lines.get(i + 1).toLowerCase().contains("fully vaccinated")) {
                                                StaticData.request.fullyVaccineFlag = "YES";
                                            } else {
                                                StaticData.request.fullyVaccineFlag = "NO";
                                            }
                                            break;
                                        }
                                    }
                                }

                                if(StaticData.moduleConfig.getVpVACOCR()){
                                    context.finish();
                                    context.startActivity(new Intent(context, VaccinationCertificateActivity.class));
                                    StaticData.base64_mysejahtera = new Utils().bitmapToBase64(bitmap);
                                } else {
                                    context.finish();
                                    context.startActivity(new Intent(context, ThankYouActivity.class));
                                }


                                //}
                            }
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        showMyKadFailedToReadDialog(VaccinationRecognitionActivity.this);
                    }
                });

    }

    private com.google.mlkit.vision.text.TextRecognizer getTextRecognizer() {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    private void showMyKadFailedToReadDialog(AppCompatActivity context) {
        AwesomeInfoDialog dialog = new AwesomeInfoDialog(context);
        dialog.setTitle("Failed to read");
        dialog.setMessage("Please fill in manually or retry.");
        dialog.setColoredCircle(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor);
        dialog.setDialogIconAndColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.drawable.ic_dialog_error, com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white);
        dialog.setCancelable(false);

        dialog.setPositiveButtonText("Add Manually");
        dialog.setPositiveButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor);
        dialog.setPositiveButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white);
        dialog.setPositiveButtonClick(new Closure() {
            @Override
            public void exec() {
                dialog.hide();
                context.finish();
                context.startActivity(new Intent(context, VaccinationDetailsActivity.class));
            }
        });

        dialog.setNegativeButtonText("Retry");
        dialog.setNegativeButtonbackgroundColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.dialogErrorBackgroundColor);
        dialog.setNegativeButtonTextColor(com.awesomedialog.blennersilva.awesomedialoglibrary.R.color.white);
        dialog.setNegativeButtonClick(new Closure() {
            @Override
            public void exec() {
                dialog.hide();
                context.finish();
                context.startActivity(new Intent(context, VaccinationRecognitionActivity.class));
            }
        });
        dialog.show();
    }
}

