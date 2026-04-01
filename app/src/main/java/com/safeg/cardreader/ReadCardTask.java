package com.safeg.cardreader;

import android.content.res.Resources;
import android.os.AsyncTask;

import com.intellego.morphosmart.driver.MorphoSmart;
import com.intellego.mykad.CardHolderInfo;
import com.intellego.mykad.MyKad;

import java.util.Date;

public final class ReadCardTask extends AsyncTask<Void, String, ReadCardResult> {

	protected final Resources mResources;

	private ReadCardResult mResult;
	private String mProgressMessage;
	private IProgressTracker mProgressTracker;
	private MyKad mykad;
	private boolean readPhoto = false;

	/* UI Thread */
	public ReadCardTask(Resources resources, MorphoSmart morphoSmart, boolean readPicture) {
		//mykad = new MyKad(morphoSmart.getCardTerminal());
		mykad = new MyKad(morphoSmart);
		mResources = resources;
		mProgressMessage = "Reading MyKad...";
		readPhoto = readPicture;
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
	protected void onPostExecute(ReadCardResult readCardResult) {
		mResult = readCardResult;

		if (mProgressTracker != null) {
			mProgressTracker.onComplete();
		}

		mProgressTracker = null;
	}

	private void SetProgressMessage(String msg){
		publishProgress(msg);
	}	
	
	@Override
	protected ReadCardResult doInBackground(Void... arg0) {
		long lStartTime = new Date().getTime();
		
		CardHolderInfo cardHolderInfo = new CardHolderInfo();
		
		try {
			SetProgressMessage("Opening MyKad for access...");
			
			mykad.powerUp();
			
			//Test
			SetProgressMessage("Reading personal information in MyKad...");
			cardHolderInfo = mykad.getCardHolderInfo(false, false);

			if (readPhoto)
			{
				cardHolderInfo.setPhoto(mykad.getPhoto());
			}
			//End Test
						
			mykad.powerDown();
			
			long lEndTime = new Date().getTime();
									
			SetProgressMessage("Read MyKad completed");
			
			return new ReadCardResult(cardHolderInfo, true, (lEndTime - lStartTime)/1000, "");	
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return new ReadCardResult(cardHolderInfo, false, 0, e.getMessage());
		}
	}
}