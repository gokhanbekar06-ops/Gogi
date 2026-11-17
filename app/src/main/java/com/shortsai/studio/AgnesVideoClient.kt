package com.shortsai.studio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AgnesVideoClient {
    const val WORKER_URL = "https://shorts-ai-studio-video.gokhanbekar06.workers.dev"

    fun generateAndSave(context: Context, prompt: String, seconds: Int, onUpdate: (String) -> Unit, onComplete: (Result<Uri>) -> Unit) {
        thread(name = "agnes-video-generation") {
            val result = runCatching {
                require(prompt.isNotBlank()) { "Önce video fikrini yaz." }
                onUpdate("Agnes AI'ye video isteği gönderiliyor...")
                val create = requestJson("POST", "$WORKER_URL/generate", JSONObject()
                    .put("prompt", prompt)
                    .put("seconds", seconds.coerceIn(4, 12)))
                val videoId = create.optString("video_id").ifBlank { create.optString("id") }
                    .ifBlank { create.optString("task_id") }
                require(videoId.isNotBlank()) { "Video görevi kimliği alınamadı: $create" }

                var videoUrl = ""
                for (attempt in 1..120) {
                    Thread.sleep(5000)
                    onUpdate("AI video hazırlanıyor... kontrol $attempt/120")
                    val status = requestJson("GET", "$WORKER_URL/status?video_id=$videoId", null)
                    val state = status.optString("status").lowercase()
                    if (state in listOf("completed", "succeeded", "success", "done")) {
                        videoUrl = status.optString("url")
                        if (videoUrl.isBlank()) videoUrl = status.optJSONObject("metadata")?.optString("url").orEmpty()
                        if (videoUrl.isBlank()) videoUrl = status.optJSONObject("data")?.optString("url").orEmpty()
                        break
                    }
                    if (state in listOf("failed", "error", "cancelled")) error("Agnes video üretimi başarısız: $status")
                }
                require(videoUrl.startsWith("https://") || videoUrl.startsWith("http://")) { "Video bağlantısı alınamadı veya zaman aşımı oluştu." }
                onUpdate("Video indiriliyor ve Galeri'ye kaydediliyor...")
                saveVideo(context, videoUrl)
            }
            onComplete(result)
        }
    }

    private fun requestJson(method: String, address: String, body: JSONObject?): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("Video sunucusu HTTP $code: $text")
        return JSONObject(text)
    }

    private fun saveVideo(context: Context, address: String): Uri {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 120000
        }
        val temp = File(context.cacheDir, "agnes-${System.currentTimeMillis()}.mp4")
        connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
        connection.disconnect()
        require(temp.length() > 0L) { "İndirilen video boş." }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ShortsAI-Agnes-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shorts AI Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Video Galeri'ye kaydedilemedi.")
        try {
            context.contentResolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                ?: error("Video dosyası kaydedilemedi.")
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        } finally {
            temp.delete()
        }
    }
}
