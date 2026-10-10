package com.example.data.repository

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** Provides a synchronized time across devices using Google's servers. */
object ServerTime {
    @Volatile
    private var offsetMs: Long = 0L

    init {
        // Fetch server time in the background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://google.com")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.connect()
                val dateStr = connection.getHeaderField("Date")
                if (dateStr != null) {
                    val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                    val serverTime = sdf.parse(dateStr)?.time
                    if (serverTime != null) {
                        val localTime = System.currentTimeMillis()
                        offsetMs = serverTime - localTime
                    }
                }
            } catch (e: Exception) {
                // Keep offset as 0 if offline or failed
            }
        }
    }

    /** Returns the current time in milliseconds, synchronized across devices. */
    fun now(): Long = System.currentTimeMillis() + offsetMs
}
