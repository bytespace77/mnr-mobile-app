package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetCompanyResponseItem {

    @SerializedName("id")
    @Expose
    var id:Int  = 0;

    @SerializedName("companyName")
    @Expose
    var companyName : String  = ""

    @SerializedName("companyRegId")
    @Expose
    var companyRegId : String  = ""



}