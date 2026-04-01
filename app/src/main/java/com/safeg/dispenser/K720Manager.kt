package com.safeg.dispenser

import android.content.Context
import android.util.Log
import K720_Package.K720_Serial

class K720Manager(private val context: Context) {

    private val k720 = K720_Serial()
    private var macAddr: Byte = 0x00

    fun connect(port: String = "/dev/s3c2410_serial3", baudRate: Int = 9600): Boolean {
        return try {
            K720_Serial.CopyContext(context)
            val ret = K720_Serial.K720_CommOpenWithBaud(port, baudRate)
            if (ret != 0) {
                Log.e("K720", "Failed to open port: $port")
                false
            } else {
                for (i in 0 until 16) {
                    val recordInfo = arrayOfNulls<String>(2)
                    if (K720_Serial.K720_AutoTestMac(i.toByte(), recordInfo) == 0) {
                        macAddr = i.toByte()
                        Log.i("K720", "Connected, MacAddr=$macAddr")
                        return true
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.e("K720", "Exception during connect", e)
            false
        }
    }

    fun disconnect(): Boolean {
        val ret = K720_Serial.K720_CommClose()
        Log.i("K720", "Disconnected")
        return ret == 0
    }

    fun sendCard(): Boolean {
        val recordInfo = arrayOfNulls<String>(2)
        val cmd = byteArrayOf(0x46, 0x43, 0x37) // FC7 command
        val ret = K720_Serial.K720_SendCmd(macAddr, cmd, cmd.size, recordInfo)
        return ret == 0
    }

    fun retainCard(): Boolean {
        val recordInfo = arrayOfNulls<String>(2)
        val cmd = byteArrayOf(0x44, 0x42) // DB command
        val ret = K720_Serial.K720_SendCmd(macAddr, cmd, cmd.size, recordInfo)
        return ret == 0
    }

    fun readCardId(): String? {
        val cardId = ByteArray(10)
        val recordInfo = arrayOfNulls<String>(2)
        val ret = K720_Serial.K720_S70GetCardID(macAddr, cardId, recordInfo)
        return if (ret == 0) cardId.joinToString("") { "%02X".format(it) } else null
    }
}
