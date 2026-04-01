import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Reason {
    @SerializedName("name")
    @Expose
    var name:String = ""

    @SerializedName("id")
    @Expose
    var id:String = ""
}