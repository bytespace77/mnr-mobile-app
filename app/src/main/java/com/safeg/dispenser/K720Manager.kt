package com.safeg.dispenser

import K720_Package.K720_Serial
import K720_Package.K720_Serial.ByteArrayToString
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.safeg.utils.Common


class K720Manager(private val context: Context) {

    private val k720 = K720_Serial()
    private var macAddr: Byte = 0x00

    fun connect(port: String = "/dev/ttyS5", baudRate: Int = 9600): Boolean {
        return try {
            K720_Serial.CopyContext(context)
            val ret = K720_Serial.K720_CommOpenWithBaud(port, baudRate)
            if (ret != 0) {
                Log.e("K720", "Failed to open port: $port")
                Common.showToast(context, "Card Dispenser Port Error", Common.ToastType.ERROR)
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
            Common.showToast(context, "Card Dispenser Not Connected", Common.ToastType.WARNING)
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
        val thread = SendCardThread(
            context = context,
            macAddr = macAddr
        )
        thread.start()
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

    fun dispenseCard(): Boolean {
        if (macAddr < 0) {
            Common.showToast(context, "Card Dispenser Not Ready", Common.ToastType.WARNING)
            return false
        }

        return try {
            reset()
            Thread.sleep(300)
            feed()
            Thread.sleep(300)
            eject()
        } catch (t: Throwable) {
            Common.showToast(context, "Card Dispense Failed", Common.ToastType.ERROR)
            false
        }
    }

    private fun reset() = sendCmd(byteArrayOf(0x46, 0x43, 0x31)) // FC1
    private fun feed()  = sendCmd(byteArrayOf(0x46, 0x43, 0x35)) // FC5
    private fun move()  = sendCmd(byteArrayOf(0x46, 0x43, 0x37)) // FC7
    private fun eject() = sendCmd(byteArrayOf(0x46, 0x43, 0x33)) // FC3

    private fun sendCmd(cmd: ByteArray): Boolean {
        val info = arrayOfNulls<String>(2)
        val ret = K720_Serial.K720_SendCmd(macAddr, cmd, cmd.size, info)
        return ret == 0
    }
}

class SendCardThread(
    private val context: Context,
    private val macAddr: Byte
) : Thread() {

    @Volatile
    var executeSendCard = true

    override fun run() {

        val stateInfo = ByteArray(20)
        val cardId = ByteArray(10)
        val recordInfo = arrayOfNulls<String>(2)
        val sendBuf = ByteArray(3)

        // ✅ Log only — no toast for internal steps
        Log.d("K720", "Start card dispensing process")

        while (executeSendCard) {

            sleepSafe(800)

            // 1️⃣ Sensor query
            val ret = K720_Serial.K720_SensorQuery(macAddr, stateInfo, recordInfo)
            if (ret != 0) {
                Log.e("K720", "Sensor query error")
                continue
            }

            // 2️⃣ Card box empty
            if ((stateInfo[3].toInt() and 0x08) == 0x08) {
                sendMsg("Card Box Empty — Please Refill", Common.ToastType.WARNING)
                break
            }

            // 3️⃣ Send card to reader (FC7)
            Log.d("K720", "Sending card to reader")

            sendBuf[0] = 0x46
            sendBuf[1] = 0x43
            sendBuf[2] = 0x37

            if (K720_Serial.K720_SendCmd(macAddr, sendBuf, 3, recordInfo) != 0) {
                Log.e("K720", "Send card failed")
                continue
            }

            sleepSafe(1500)

            // 4️⃣ Read card ID
            val readRet = K720_Serial.K720_S70GetCardID(macAddr, cardId, recordInfo)
            if (readRet == 0) {
                Log.d("K720", "Card UID: ${byteArrayToHex(cardId, 4)}")
            } else {
                Log.w("K720", "Failed to read card UID")
            }

            // 5️⃣ Eject card
            Log.d("K720", "Ejecting card")

            sendBuf[2] = 0x30 // FC0
            K720_Serial.K720_SendCmd(macAddr, sendBuf, 3, recordInfo)

            Log.d("K720", "Card dispensed successfully")
            break
        }
    }

    private fun sendMsg(text: String, type: Common.ToastType = Common.ToastType.INFO) {
        Handler(Looper.getMainLooper()).post {
            Common.showToast(context, text, type)
        }
    }

    private fun sleepSafe(ms: Long) {
        try { Thread.sleep(ms) } catch (_: Exception) {}
    }

    private fun byteArrayToHex(data: ByteArray, len: Int): String =
        data.take(len).joinToString(" ") { "%02X".format(it) }
}