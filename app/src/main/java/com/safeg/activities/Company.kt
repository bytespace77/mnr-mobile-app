package com.safeg.activities

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Company {
    @SerializedName("id")
    @Expose
    var id : String  = ""

    @SerializedName("companyName")
    @Expose
    var name : String  = ""

    @SerializedName("contactNoOfPersonVisited")
    @Expose
    var contactNoOfPersonVisited : String  = ""
}