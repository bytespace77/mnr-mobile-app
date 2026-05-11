package com.safeg.cardreader;

import com.intellego.mykad.CardHolderInfo;

public class ReadCardResult {
	private CardHolderInfo PersonalInfo;
	private Boolean Status; 
	private long ElapsedTime;
	private String ErroMessage;
	
 	public String getErrorMessage() {
		return ErroMessage;
	}

	public ReadCardResult(CardHolderInfo cardHolderInfo, Boolean status, long elapsedTime, String errorMessage){
 		PersonalInfo = cardHolderInfo;
 		Status = status;
 		ElapsedTime = elapsedTime;
 		this.ErroMessage = errorMessage;
	}

	public CardHolderInfo getPersonalInfo() {
		return PersonalInfo;
	}

	public Boolean isSuccessful() {
		return (Status == true);
	}

	public long getElapsedTime() {
		return ElapsedTime;
	}

	public void setElapsedTime(long elapsedTime) {
		this.ElapsedTime = elapsedTime;
	}
}
