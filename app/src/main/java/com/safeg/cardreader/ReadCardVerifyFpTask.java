package com.safeg.cardreader;

import android.content.res.Resources;
import android.os.AsyncTask;

import com.intellego.morphosmart.driver.MorphoSmart;
import com.intellego.morphosmart.driver.MorphoSmartResult;
import com.intellego.mykad.CardHolderInfo;
import com.intellego.mykad.MyKad;

import java.util.Date;

public class ReadCardVerifyFpTask extends AsyncTask<Void, String, ReadCardVerifyFpResult> {

    protected final Resources mResources;

    private ReadCardVerifyFpResult mResult;
    private String mProgressMessage;
    private IProgressTracker mProgressTracker;
    private MorphoSmart morphosmart;
    private MyKad mykad;
    private boolean readPhoto = false;
    private int verifyFpTimeout;

    /* UI Thread */
    public ReadCardVerifyFpTask(Resources resources, MorphoSmart morphoSmart, boolean readPicture, int verifyFpTimeout) {
        //mykad = new MyKad(morphoSmart.getCardTerminal());
        this.morphosmart = morphoSmart;
        mykad = new MyKad(morphoSmart);
        mResources = resources;
        mProgressMessage = "Reading MyKad...";
        readPhoto = readPicture;
        this.verifyFpTimeout = verifyFpTimeout;
    }

    public void setProgressTracker(IProgressTracker progressTracker) {
        mProgressTracker = progressTracker;

        if (mProgressTracker != null) {
            mProgressTracker.onProgress(mProgressMessage);
            if (mResult != null) {
                mProgressTracker.onComplete();
            }
        }
    }

    @Override
    protected void onCancelled() {
        mProgressTracker = null;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        mProgressMessage = values[0];
        if (mProgressTracker != null) {
            mProgressTracker.onProgress(mProgressMessage);
        }
    }

    @Override
    protected void onPostExecute(ReadCardVerifyFpResult readCardVerifyFpResult) {
        mResult = readCardVerifyFpResult;

        if (mProgressTracker != null) {
            mProgressTracker.onComplete();
        }

        mProgressTracker = null;
    }

    private void SetProgressMessage(String msg){
        publishProgress(msg);
    }

    @Override
    protected ReadCardVerifyFpResult doInBackground(Void... arg0) {
        long lStartTime = new Date().getTime();

        CardHolderInfo cardHolderInfo = new CardHolderInfo();

        try {
            SetProgressMessage("Opening MyKad for access...");

            mykad.powerUp();

            //Test
            SetProgressMessage("Reading personal information in MyKad...");
            cardHolderInfo = mykad.getCardHolderInfo(readPhoto, true);

            if (readPhoto)
            {
                cardHolderInfo.setPhoto(mykad.getPhoto());
            }
            //End Test

            mykad.powerDown();

            SetProgressMessage("Verifying fingerprint [Please place fingerprint on biometric reader when the light is ON]...");
            long lEndTime = new Date().getTime();

            byte[] fingerprint = new byte[1196];

            System.arraycopy(cardHolderInfo.getFingerprint1(), 0, fingerprint, 0, cardHolderInfo.getFingerprint1().length);
            System.arraycopy(cardHolderInfo.getFingerprint2(), 0, fingerprint, cardHolderInfo.getFingerprint1().length, cardHolderInfo.getFingerprint2().length);
            MorphoSmartResult morphosmartResult = morphosmart.verifyFingerprint(fingerprint, (short) verifyFpTimeout);
            return new ReadCardVerifyFpResult(new ReadCardResult(cardHolderInfo, true, (lEndTime - lStartTime)/1000, ""), new VerifyFPResult(morphosmartResult, ""));

        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            //return new ReadCardResult(cardHolderInfo, false, 0, e.getMessage());
            return null;
        }
    }
}
