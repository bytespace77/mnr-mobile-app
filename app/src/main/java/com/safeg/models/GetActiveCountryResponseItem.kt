package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetActiveCountryResponseItem {

    @SerializedName("id")
    @Expose
    var id:Int  = 0;

    @SerializedName("name")
    @Expose
    var name : String  = ""

    @SerializedName("code")
    @Expose
    var code : String = ""

    @SerializedName("code3")
    @Expose
    var code3 : String = ""
}