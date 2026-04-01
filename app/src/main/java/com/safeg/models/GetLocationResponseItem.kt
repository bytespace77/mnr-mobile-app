package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetLocationResponseItem {

    @SerializedName("id")
    @Expose
    var id:Int  = 0;

    @SerializedName("name")
    @Expose
    var name : String  = ""

    @SerializedName("ipport")
    @Expose
    var ipport : String  = ""

    @SerializedName("password")
    @Expose
    var password : String  = ""

    @SerializedName("barrierGate")
    @Expose
    var barrierGate : Boolean  = false

    @SerializedName("holdarea")
    @Expose
    var holdarea : Boolean  = false

    @SerializedName("passPrint")
    @Expose
    var passPrint : Boolean  = false

    @SerializedName("meetingRoom")
    @Expose
    var meetingRoom : String = ""

    @SerializedName("showSubLocation")
    @Expose
    var showSubLocation : Boolean  = false

}