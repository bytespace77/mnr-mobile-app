package com.safeg.cardreader;

public class ReadCardVerifyFpResult {
    private ReadCardResult readCardResult;
    private VerifyFPResult verifyFPResult;

    public ReadCardVerifyFpResult(ReadCardResult readCardResult, VerifyFPResult verifyFPResult) {
        this.readCardResult = readCardResult;
        this.verifyFPResult = verifyFPResult;
    }

    public ReadCardResult getReadCardResult() {
        return readCardResult;
    }

    public void setReadCardResult(ReadCardResult readCardResult) {
        this.readCardResult = readCardResult;
    }

    public VerifyFPResult getVerifyFPResult() {
        return verifyFPResult;
    }

    public void setVerifyFPResult(VerifyFPResult verifyFPResult) {
        this.verifyFPResult = verifyFPResult;
    }
}
