package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetStaffPassByStaffNoOrNameResponseItem {

    @SerializedName("username")
    @Expose
    var username:String = ""

    @SerializedName("email")
    @Expose
    var email:String = ""

    @SerializedName("name")
    @Expose
    var name:String = ""

    @SerializedName("mobileNo")
    @Expose
    var mobileNo:String = ""

    @SerializedName("status")
    @Expose
    var status:String = ""

    @SerializedName("message")
    @Expose
    var message:String = ""

    @SerializedName("icNo")
    @Expose
    var icNo:String = ""

    @SerializedName("passportNo")
    @Expose
    var passportNo:String = ""

    @SerializedName("photo")
    @Expose
    var photo:String = ""

    @SerializedName("visitorType")
    @Expose
    var visitorType:String = ""
}