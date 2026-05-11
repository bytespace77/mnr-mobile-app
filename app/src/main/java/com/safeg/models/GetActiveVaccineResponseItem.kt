package com.safeg.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetActiveVaccineResponseItem {

    @SerializedName("id")
    @Expose
    var id:Int  = 0;

    @SerializedName("name")
    @Expose
    var name : String  = ""

    @SerializedName("cooldownDay")
    @Expose
    var cooldownDay : String = ""
}