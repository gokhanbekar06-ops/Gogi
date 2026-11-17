package com.shortsai.studio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
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
        generateClipsAndSave(context, listOf(prompt), seconds, onUpdate, onComplete)
    }

    fun generateClipsAndSave(context: Context, scenePrompts: List<String>, totalSeconds: Int, onUpdate: (String) -> Unit, onComplete: (Result<Uri>) -> Unit) {
        thread(name = "agnes-multi-video-generation") {
            val result = runCatching {
                require(scenePrompts.isNotEmpty() && scenePrompts.all { it.isNotBlank() }) { "Önce hikâyenin 5 sahnesini oluştur." }
                val clipSeconds = (totalSeconds.coerceIn(20, 60) / scenePrompts.size).coerceIn(4, 12)
                val clips = mutableListOf<File>()
                try {
                    scenePrompts.forEachIndexed { index, scenePrompt ->
                        onUpdate("AI sahne ${index + 1}/${scenePrompts.size} gönderiliyor (${clipSeconds} sn)...")
                        clips += createClip(scenePrompt, clipSeconds) { message -> onUpdate("Sahne ${index + 1}/${scenePrompts.size}: $message") }
                    }
                    onUpdate("AI klipleri tek MP4 videoda birleştiriliyor...")
                    val merged = File(context.cacheDir, "shorts-ai-merged-${System.currentTimeMillis()}.mp4")
                    concatenate(clips, merged)
                    onUpdate("Video Galeri'ye kaydediliyor...")
                    saveVideoFile(context, merged)
                } finally { clips.forEach { it.delete() } }
            }
            onComplete(result)
        }
    }

    private fun createClip(prompt: String, seconds: Int, onUpdate: (String) -> Unit): File {
        val createBody = JSONObject().put("prompt", prompt).put("seconds", seconds.coerceIn(4, 12))
        var create: JSONObject? = null
        for (requestAttempt in 1..3) {
            try {
                create = requestJson("POST", "$WORKER_URL/generate", createBody)
                break
            } catch (error: Exception) {
                val message = error.message.orEmpty()
                val rateLimited = message.contains("429") || message.contains("1015") || message.contains("istek sınırına")
                if (!rateLimited || requestAttempt == 3) throw error
                onUpdate("Agnes istek sınırı: 65 saniye bekleniyor (deneme $requestAttempt/3)...")
                Thread.sleep(65_000)
            }
        }
        val createResponse = create ?: error("Video isteği oluşturulamadı.")
        val id = createResponse.optString("video_id").ifBlank { createResponse.optString("id") }.ifBlank { createResponse.optString("task_id") }
        require(id.isNotBlank()) { "Video görevi kimliği alınamadı: $createResponse" }
        var videoUrl = ""
        for (attempt in 1..120) {
            Thread.sleep(5000)
            onUpdate("hazırlanıyor... kontrol $attempt/120")
            val status = requestJson("GET", "$WORKER_URL/status?video_id=$id", null)
            val state = status.optString("status").lowercase()
            if (state in listOf("completed", "succeeded", "success", "done")) {
                videoUrl = status.optString("url").ifBlank { status.optJSONObject("metadata")?.optString("url").orEmpty() }
                    .ifBlank { status.optJSONObject("data")?.optString("url").orEmpty() }
                break
            }
            if (state in listOf("failed", "error", "cancelled")) error("Agnes video üretimi başarısız: $status")
        }
        require(videoUrl.startsWith("http")) { "Video bağlantısı alınamadı veya zaman aşımı oluştu." }
        val connection = URL(videoUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 120000
        val file = File.createTempFile("agnes-clip-", ".mp4")
        try { connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } } }
        finally { connection.disconnect() }
        require(file.length() > 0L) { "İndirilen AI klibi boş." }
        return file
    }

    private fun concatenate(clips: List<File>, output: File) {
        val readers = clips.map { MediaExtractor().apply { setDataSource(it.absolutePath) } }
        var muxer: MediaMuxer? = null
        var started = false
        try {
            val first = readers.first()
            val sourceTracks = (0 until first.trackCount).filter { i ->
                val mime = first.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
                mime.startsWith("video/") || mime.startsWith("audio/")
            }
            require(sourceTracks.isNotEmpty()) { "AI klibinde ses/görüntü kanalı bulunamadı." }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTracks = sourceTracks.map { muxer.addTrack(first.getTrackFormat(it)) }
            muxer.start(); started = true
            val buffer = java.nio.ByteBuffer.allocate(8 * 1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            var offsetUs = 0L
            readers.forEach { extractor ->
                val mapping = mutableMapOf<Int, Int>()
                for (track in 0 until extractor.trackCount) {
                    val mime = extractor.getTrackFormat(track).getString(MediaFormat.KEY_MIME).orEmpty()
                    val target = sourceTracks.indexOfFirst { first.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == mime }
                    if (target >= 0) { extractor.selectTrack(track); mapping[track] = outputTracks[target] }
                }
                var lastUs = 0L
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val target = mapping[extractor.sampleTrackIndex]
                    if (target != null) {
                        val pts = extractor.sampleTime.coerceAtLeast(0L)
                        info.offset = 0; info.size = size; info.presentationTimeUs = offsetUs + pts; info.flags = extractor.sampleFlags
                        muxer.writeSampleData(target, buffer, info)
                        lastUs = maxOf(lastUs, pts)
                    }
                    extractor.advance()
                }
                val duration = (0 until extractor.trackCount).mapNotNull { i ->
                    val f = extractor.getTrackFormat(i)
                    if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else null
                }.maxOrNull() ?: 0L
                offsetUs += maxOf(duration, lastUs + 1000L)
            }
        } finally {
            runCatching { if (started) muxer?.stop() }; runCatching { muxer?.release() }
            readers.forEach { runCatching { it.release() } }
        }
        require(output.exists() && output.length() > 0) { "AI klipleri birleştirilemedi." }
    }

    private fun saveVideoFile(context: Context, file: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ShortsAI-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shorts AI Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Video Galeri'ye kaydedilemedi.")
        try {
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Video dosyası kaydedilemedi.")
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) { context.contentResolver.delete(uri, null, null); throw e }
        finally { file.delete() }
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
