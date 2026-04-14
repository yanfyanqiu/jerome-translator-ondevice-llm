package com.app.jerometranslator.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class ModelDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Downloads [url] to [destination] with progress reporting.
     * Supports resume: if [destination] already has partial data and the
     * server supports Range requests, continues from where it left off.
     */
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()

        val existingBytes = if (destination.exists()) destination.length() else 0L

        val requestBuilder = Request.Builder().url(url)
        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            throw IOException("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val contentLength = body.contentLength()
        val totalBytes = if (response.code == 206) existingBytes + contentLength else contentLength
        val isResume = response.code == 206

        val outputStream = if (isResume) {
            FileOutputStream(destination, true) // append mode — preserves existing bytes
        } else {
            FileOutputStream(destination) // truncate mode — fresh download
        }

        outputStream.use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var downloaded = if (isResume) existingBytes else 0L

                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    out.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    if (totalBytes > 0) {
                        onProgress(downloaded.toFloat() / totalBytes)
                    }
                }
            }
        }

        onProgress(1f)
    }
}
