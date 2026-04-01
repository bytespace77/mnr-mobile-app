package com.safeg.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream


class Utils {
     fun countLines(str : String) : Int?{
        return str.lines().size;
    }

    fun getLines(str : String) : List<String>{
        return str.lines();
    }

    fun bitmapToBase64(bm: Bitmap): String? {
        val baos = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val b: ByteArray = baos.toByteArray()
        return Base64.encodeToString(b, Base64.DEFAULT)
    }

    fun base64ToBitmap(base64String: String): Bitmap? {
        var base64Image = base64String
        if (base64String.contains(",")) {
            base64Image = base64String.split(",".toRegex()).toTypedArray()[1]
        }
        val decodedString: ByteArray = Base64.decode(base64Image, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    }
}