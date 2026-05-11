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
import android.provider.MediaStore;
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
import com.google.mlkit.vision.text.internal.TextRegistrar;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.safeg.R;
import com.safeg.StaticData;
import com.safeg.databinding.ActivityTextRecognitionBinding;
import com.safeg.utils.Utils;

//import org.checkerframework.checker.units.qual.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TextRecognitionActivity extends AppCompatActivity {

    CameraSource cameraSource;
    final int RequestCameraPermissionID = 1001;
    Utils utils;

    private ActivityTextRecognitionBinding binding;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RequestCameraPermissionID) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                try {
                    cameraSource.start(binding.cameraView.getHolder());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTextRecognitionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        utils = new Utils();

        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        BoxDetector boxDetector = new BoxDetector(textRecognizer, 200 , 200);

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

            binding.cameraView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {

                    try {
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                            ActivityCompat.requestPermissions(TextRecognitionActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    RequestCameraPermissionID);
                            return;
                        }
                        cameraSource.start(binding.cameraView.getHolder());
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
                    startActivity(new Intent(TextRecognitionActivity.this, CardDetailsActivity.class));
                }

                @Override
                public void receiveDetections(Detector.Detections<TextBlock> visionText) {

                    //String text = getDetectionText(visionText);
                    //Log.d("Blocks Whole Text", text);

                }
            });
        }

        new CountDownTimer(15000, 500){

            @Override
            public void onTick(long l) {

            }

            @Override
            public void onFinish() {
                try {
                    if (StaticData.request.ic.isEmpty() || StaticData.request.fullName.isEmpty()) {
                        showMyKadFailedToReadDialog(TextRecognitionActivity.this);
                    }
                } catch (Throwable throwable){

                }
            }
        }.start();

        binding.rlAddManually.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                startActivity(new Intent(TextRecognitionActivity.this, CardDetailsActivity.class));
            }
        });
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

            //StaticData.request.icBased64[0] = new Utils().bitmapToBase64(bmp);

            recognizeText(bmp, TextRecognitionActivity.this);

            return mDelegate.detect(croppedFrame);
        }

        public Bitmap rotateBitmap(Bitmap source, Float angle){
            try {
                Matrix matrix = new Matrix();
                matrix.postRotate(angle);

                Bitmap rotatedBitmap = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);

               // int _x1 = (int)((rotatedBitmap.getWidth() / 100F) * 17);
               // int _y1 = (int)(rotatedBitmap.getHeight() / 4.5F);
               // int _x2 = (int)((rotatedBitmap.getWidth() / 100F) * 66);
               // int _y2 = (int)((rotatedBitmap.getHeight() / 4.5F) * 1.5F);

               // return Bitmap.createBitmap(rotatedBitmap, _x1, _y1, _x2, _y2, null, true);

                return  rotatedBitmap;
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
                        Log.d("Blocks Lines Count", ""+utils.countLines(visionText.getText()));
                        if (StaticData.request.ic.isEmpty() || StaticData.request.fullName.isEmpty()) {
                            try {
                                if(utils.countLines(visionText.getText()) >= 7
                                        && (visionText.getText().contains("kad") || visionText.getText().contains("peng"))){
                                    int position = 0;
                                    int positionIdentityNumber = -1;

                                    for(Text.TextBlock item : visionText.getTextBlocks()) {
                                        String[] items = item.getText().split("-");
                                        if (items.length == 3) {
                                            try {
                                                int firstPart = Integer.parseInt(items[0]);
                                                //else if(dob.length() == 8){
                                                //    StaticData.request.birthday = dob.substring(0,3)+"-"+dob.substring(2,3)+"-"+dob.substring(4,5);
                                                //}
                                                int secondPart = Integer.parseInt(items[1]);
                                                int thirdPart = Integer.parseInt(items[2]);

                                                List<String> linesCity = utils.getLines(visionText.getTextBlocks().get(position + 2).getText());

                                                if(linesCity.size() >= 3){
                                                    int postalCode = Integer.parseInt(linesCity.get(linesCity.size()-2).split(" ")[0]);
                                                    String city = linesCity.get(linesCity.size()-2).split(" ")[1];
                                                    positionIdentityNumber = position;
                                                    break;
                                                }
                                                //break;
                                            } catch (Throwable throwable){

                                            }
                                        }
                                        position++;
                                    }

                                    if (positionIdentityNumber != -1 && visionText.getTextBlocks().get(positionIdentityNumber + 1).getText().length() > 3) {
                                        StaticData.request.ic =
                                                visionText.getTextBlocks().get(positionIdentityNumber).getText();

                                        try {
                                            String dob = ""+StaticData.request.ic.split("-")[0];
                                            if(dob.length() == 6){
                                                StaticData.request.birthday = "19"+dob.substring(0,2)+"-"+dob.substring(2,4)+"-"+dob.substring(4,6);
                                            }
                                        } catch (Throwable throwable){

                                        }


                                        StaticData.request.fullName =
                                                visionText.getTextBlocks().get(positionIdentityNumber + 1).getText();

                                        List<String> linesCity = utils.getLines(visionText.getTextBlocks().get(positionIdentityNumber + 2).getText());

                                        if(linesCity.size() >= 3){
                                            StaticData.request.country = 0;
                                            StaticData.request.state = linesCity.get(linesCity.size()-1);
                                            StaticData.request.postalCode = linesCity.get(linesCity.size()-2).split(" ")[0];
                                            StaticData.request.city = linesCity.get(linesCity.size()-2).split(" ")[1];
                                            if(linesCity.size() == 3){
                                                StaticData.request.add1 = linesCity.get(0);
                                            }

                                            if(linesCity.size() == 4){
                                                StaticData.request.add1 = linesCity.get(0);
                                                StaticData.request.add2 = linesCity.get(1);
                                            }

                                            if(linesCity.size() == 5){
                                                StaticData.request.add1 = linesCity.get(0);
                                                StaticData.request.add2 = linesCity.get(1);
                                                StaticData.request.add3 = linesCity.get(2);
                                            }
                                        }

                                    }

                                    if (StaticData.request.ic.isEmpty() || StaticData.request.fullName.isEmpty()) {
                                        //Toast.makeText(applicationContext, "Not a valid MyKad", Toast.LENGTH_LONG)
                                        //    .show()
                                        //showMyKadFailedToReadDialog()
                                    } else {
                                        StaticData.base64_mykad = new Utils().bitmapToBase64(bitmap);
                                        context.finish();
                                        context.startActivity(new Intent(context, CardDetailsActivity.class));
                                    }
                                }
                            } catch (Throwable throwable) {
                                //showMyKadFailedToReadDialog()
                            }
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        showMyKadFailedToReadDialog(TextRecognitionActivity.this);
                    }
                });

    }

    private com.google.mlkit.vision.text.TextRecognizer getTextRecognizer() {
        return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    }

    private void showMyKadFailedToReadDialog(AppCompatActivity context) {
        AwesomeInfoDialog dialog = new AwesomeInfoDialog(context);
        dialog.setTitle("MyKad failed to read.");
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
                finish();
                context.startActivity(new Intent(context, CardDetailsActivity.class));
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
                context.startActivity(new Intent(context, TextRecognitionActivity.class));
            }
        });
        dialog.show();
    }
}

