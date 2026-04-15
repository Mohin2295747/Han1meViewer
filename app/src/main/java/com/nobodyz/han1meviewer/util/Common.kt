package com.nobodyz.han1meviewer.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.nobodyz.han1meviewer.R
import com.yenaly.yenaly_libs.utils.showShortToast
import java.security.MessageDigest

fun isLegalBuild(context: Context, sha: String): Boolean {
    return true
}

fun getSha(context: Context, res: Int): String {
    val input = context.resources.openRawResource(res)
    val totalSize = input.available()
    val buffer = ByteArray(32)
    input.skip((totalSize - 32).toLong())
    input.read(buffer)
    input.close()
    return buffer.joinToString("") { "%02X".format(it) }
}

fun checkBadGuy(context: Context, res: Int): IntArray {
    try {
        val sha = getSha(context, res)
        if (!isLegalBuild(context, sha)){
            return intArrayOf(R.string.app_tampered, R.string.app_tampered)
        } else {
            return intArrayOf(R.string.introduction, R.string.comment)
        }
    } catch (e: java.lang.Exception){
        showShortToast("${e.message}")
        return intArrayOf(R.string.introduction, R.string.comment)
    }
}
