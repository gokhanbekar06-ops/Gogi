package com.shortsai.studio

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class VideoFrameSource(val imageUrl: String, val subtitle: String)

object SlideshowExporter {
    fun export(
        context: Context,
        scenes: List<VideoFrameSource>,
        durationSeconds: Int,
        onComplete: (Result<Uri>) -> Unit
    ) {
        thread(name = "shorts-mp4-export") {
            val result = runCatching {
                require(scenes.isNotEmpty()) { "Önce sahne görsellerini oluştur." }
                require(scenes.all { it.imageUrl.isNotBlank() }) { "Önce 5 sahnenin görsellerini oluştur." }
                val bitmaps = scenes.map { downloadBitmap(it.imageUrl) }
                val outputDir = File(context.cacheDir, "shorts-export").apply { mkdirs() }
                val tempFile = File(outputDir, "shorts-${System.currentTimeMillis()}.mp4")
                encodeVideo(tempFile, scenes, bitmaps, durationSeconds)
                val savedUri = saveToGallery(context, tempFile)
                tempFile.delete()
                savedUri
            }
            onComplete(result)
        }
    }

    private fun downloadBitmap(imageUrl: String): Bitmap {
        val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 60000
            doInput = true
        }
        return connection.inputStream.use { stream ->
            BitmapFactory.decodeStream(stream) ?: error("Bir sahne görseli açılamadı.")
        }.also { connection.disconnect() }
    }

    private fun encodeVideo(
        output: File,
        scenes: List<VideoFrameSource>,
        bitmaps: List<Bitmap>,
        durationSeconds: Int
    ) {
        val width = 720
        val height = 1280
        val fps = 24
        val totalFrames = durationSeconds.coerceIn(15, 60) * fps
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfoCompat.COLOR_FORMAT_SURFACE)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        var surface: Surface? = null
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            fun drain(end: Boolean) {
                var idleCount = 0
                while (true) {
                    val index = encoder.dequeueOutputBuffer(info, if (end) 10000 else 1000)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!end || ++idleCount > 10) return
                        }
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Kodlayıcı biçimi beklenmedik şekilde değişti." }
                            trackIndex = muxer!!.addTrack(encoder.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        index >= 0 -> {
                            val buffer = encoder.getOutputBuffer(index)
                            if (info.size > 0 && buffer != null && muxerStarted) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                muxer!!.writeSampleData(trackIndex, buffer, info)
                            }
                            val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            encoder.releaseOutputBuffer(index, false)
                            if (eos) return
                        }
                    }
                }
            }

            val canvasPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                textAlign = Paint.Align.CENTER
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
                typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99000000.toInt() }
            val frameCanvasRect = Rect(0, 0, width, height)
            for (frame in 0 until totalFrames) {
                val sceneIndex = (frame.toLong() * scenes.size / totalFrames).toInt().coerceAtMost(scenes.lastIndex)
                val bitmap = bitmaps[sceneIndex]
                val canvas = surface!!.lockCanvas(null)
                try {
                    canvas.drawColor(Color.BLACK)
                    val scale = maxOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
                    val scaledW = bitmap.width * scale
                    val scaledH = bitmap.height * scale
                    val zoom = 1f + ((frame % (totalFrames / scenes.size).coerceAtLeast(1)).toFloat() /
                        (totalFrames / scenes.size).coerceAtLeast(1)) * 0.035f
                    val drawW = scaledW * zoom
                    val drawH = scaledH * zoom
                    val left = (width - drawW) / 2f
                    val top = (height - drawH) / 2f
                    canvas.drawBitmap(bitmap, null, android.graphics.RectF(left, top, left + drawW, top + drawH), canvasPaint)

                    val caption = scenes[sceneIndex].subtitle.trim()
                    if (caption.isNotEmpty()) {
                        val words = caption.split(Regex("\\s+"))
                        val lines = mutableListOf<String>()
                        var line = ""
                        for (word in words) {
                            val candidate = if (line.isEmpty()) word else "$line $word"
                            if (textPaint.measureText(candidate) > width - 90) {
                                if (line.isNotEmpty()) lines.add(line)
                                line = word
                            } else line = candidate
                        }
                        if (line.isNotEmpty()) lines.add(line)
                        val shownLines = lines.takeLast(3)
                        val lineHeight = 58f
                        val baseY = height - 160f
                        val boxTop = baseY - shownLines.size * lineHeight - 28f
                        canvas.drawRoundRect(28f, boxTop, width - 28f, height - 100f, 22f, 22f, bgPaint)
                        shownLines.forEachIndexed { i, text ->
                            canvas.drawText(text, width / 2f, baseY - (shownLines.size - 1 - i) * lineHeight, textPaint)
                        }
                    }
                } finally {
                    surface!!.unlockCanvasAndPost(canvas)
                }
                Thread.sleep(1000L / fps)
                drain(false)
            }
            encoder.signalEndOfInputStream()
            drain(true)
            check(muxerStarted) { "MP4 kodlayıcı başlatılamadı." }
        } finally {
            runCatching { surface?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            bitmaps.forEach { runCatching { it.recycle() } }
        }
    }

    private fun saveToGallery(context: Context, source: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "ShortsAI-${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Shorts AI Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Video Galeri'ye kaydedilemedi.")
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Video dosyası yazılamadı.")
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}

private object MediaCodecInfoCompat {
    const val COLOR_FORMAT_SURFACE = 0x7F000789
}
