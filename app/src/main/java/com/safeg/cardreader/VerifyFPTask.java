package com.safeg.cardreader;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Environment;

import com.intellego.morphosmart.driver.CTException;
import com.intellego.morphosmart.driver.MorphoSmart;
import com.intellego.morphosmart.driver.MorphoSmartResult;
import com.intellego.mykad.MyKad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public final class VerifyFPTask extends AsyncTask<Void, String, VerifyFPResult> {

	protected final Resources mResources;

	private VerifyFPResult mResult;
	private String mProgressMessage;
	private IProgressTracker mProgressTracker;
	private MorphoSmart morphosmart;
	private short timeout;

	/* UI Thread */
	public VerifyFPTask(Resources resources, MorphoSmart morphosmart, short timeout) {
		this.morphosmart = morphosmart;
		mResources = resources;
		mProgressMessage = "Fingerprint verification in progress. Please place your thumb on the reader when the light is on..";
		this.timeout = timeout;
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
	
	private void WriteToFile(String data) {
		File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		File file = new File(dir, "log.txt");

		try {
		    FileWriter fileWriter = new FileWriter(file);
		    fileWriter.append(data);
		    fileWriter.flush();
		    fileWriter.close();
		}
		catch(IOException e){
		    e.printStackTrace();
		}
	}

	@Override
	protected void onCancelled() {
		if (morphosmart != null) {
			//morphosmart.abortVerifyFingerprint();
		}

		mProgressTracker = null;

		super.onCancelled();
	}

	@Override
	protected void onCancelled(VerifyFPResult result) {
		if (morphosmart != null) {
			//int ret = morphosmart.abortVerifyFingerprint();
		}

		mProgressTracker = null;

		super.onCancelled(result);
	}

	@Override
	protected void onProgressUpdate(String... values) {
		mProgressMessage = values[0];
		if (mProgressTracker != null) {
			mProgressTracker.onProgress(mProgressMessage);
		}
	}

	@Override
	protected void onPostExecute(VerifyFPResult readCardResult) {
		mResult = readCardResult;

		if (mProgressTracker != null) {
			mProgressTracker.onComplete();
		}

		mProgressTracker = null;
	}

	private void SetProgressMessage(String msg) {
		publishProgress(msg);
	}

	@Override
	protected VerifyFPResult doInBackground(Void... arg0) {

		MyKad mykad = new MyKad(morphosmart);

		try {
			SetProgressMessage("Reading fingerprint from MyKad...");
			mykad.powerUp();
			byte[] fp = mykad.getFingerPrint();

			SetProgressMessage("Please place your thumb on the fingerprint reader...");
			mykad.powerDown();

			MorphoSmartResult morphosmartResult = morphosmart
					.verifyFingerprint(fp, (short) timeout);
			SetProgressMessage("Fingerprint verification completed" + 100 + "%");
			return new VerifyFPResult(morphosmartResult, "");
		} catch (CTException e) {
			e.printStackTrace();
			return new VerifyFPResult(new MorphoSmartResult(-100, -1),
					e.getMessage());
		}
	}
}