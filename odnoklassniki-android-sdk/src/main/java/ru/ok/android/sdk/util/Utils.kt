package ru.ok.android.sdk.util

import java.lang.IllegalStateException
import java.security.MessageDigest

object Utils {

    fun toMD5(toEncrypt: String): String {
        return try {
            val digest = MessageDigest.getInstance("md5")
            digest.update(toEncrypt.toByteArray())
            val bytes = digest.digest()
            val sb = StringBuilder()
            for (i in bytes.indices) {
                sb.append(String.format("%02X", bytes[i]))
            }
            sb.toString().toLowerCase()
        } catch (exc: Exception) {
            throw IllegalStateException(exc)
        }
    }

}
