package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetConfigResponseItem {

    @SerializedName("id")
    @Expose
    var id:Int  = 0;

    @SerializedName("vpOCR")
    @Expose
    var vpOCR : Boolean  = true;

    @SerializedName("vpICUpload")
    @Expose
    var vpICUpload : Boolean  = true;

    @SerializedName("vpVACOCR")
    @Expose
    var vpVACOCR : Boolean  = true;

    @SerializedName("vpVaccine")
    @Expose
    var vpVaccine : Boolean  = true;

    @SerializedName("vpQR")
    @Expose
    var vpQR : Boolean  = true;

    @SerializedName("vpFacial")
    @Expose
    var vpFacial : Boolean  = true;

    @SerializedName("vpSaliva")
    @Expose
    var vpSaliva : Boolean  = true;

    @SerializedName("vpNFC")
    @Expose
    var vpNFC : Boolean  = true;

    @SerializedName("vpRegApproval")
    @Expose
    var vpRegApproval : Boolean  = true;

    @SerializedName("vpUrine")
    @Expose
    var vpUrine : Boolean  = true;

    @SerializedName("vpEvett")
    @Expose
    var vpEvett : Boolean  = true;

    @SerializedName("vpHSE")
    @Expose
    var vpHSE : Boolean  = true;

    @SerializedName("vpExtraDoc")
    @Expose
    var vpExtraDoc : Boolean  = true;

    @SerializedName("project")
    @Expose
    var project : String  = "";
}