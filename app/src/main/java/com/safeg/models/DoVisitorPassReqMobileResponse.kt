package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class DoVisitorPassReqMobileResponse {

    @SerializedName("timestamp")
    @Expose
    var timestamp:Int  = 0;

    @SerializedName("status")
    @Expose
    var status : Int = 0

    @SerializedName("error")
    @Expose
    var error : String  = ""

    @SerializedName("exception")
    @Expose
    var exception : String  = ""

    @SerializedName("message")
    @Expose
    var message : Boolean  = false

    @SerializedName("path")
    @Expose
    var path : Boolean  = false

}