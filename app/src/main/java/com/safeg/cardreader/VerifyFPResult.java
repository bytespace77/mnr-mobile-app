package com.safeg.cardreader;

import com.intellego.morphosmart.driver.MorphoSmartResult;

public class VerifyFPResult {
	private MorphoSmartResult morphoSmartResult;
	private String errorMessage;
	
	public String getErrorMessage() {
		return errorMessage;
	}

	public VerifyFPResult(MorphoSmartResult morphoSmartResult, String errorMessage){
 		this.morphoSmartResult = morphoSmartResult;
 		this.errorMessage = errorMessage;
	}

 	public MorphoSmartResult getMorphoSmartResult() {
		return morphoSmartResult;
	}
}
