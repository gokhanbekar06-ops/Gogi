package com.shortsai.studio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale

object VoiceoverGenerator {
    fun synthesizeTurkish(context: Context, text: String, onComplete: (Result<File>) -> Unit) {
        val output = File(context.cacheDir, "shorts-voiceover-${System.currentTimeMillis()}.wav")
        var engine: TextToSpeech? = null
        val tts = TextToSpeech(context) { status ->
            val current = engine
            if (status != TextToSpeech.SUCCESS || current == null) {
                current?.shutdown()
                onComplete(Result.failure(IllegalStateException("Türkçe ses motoru başlatılamadı.")))
                return@TextToSpeech
            }
            val language = current.setLanguage(Locale("tr", "TR"))
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                current.shutdown()
                onComplete(Result.failure(IllegalStateException("Telefonda Türkçe TTS dili yüklü değil.")))
                return@TextToSpeech
            }
            current.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    current.shutdown()
                    if (output.exists() && output.length() > 44) onComplete(Result.success(output))
                    else onComplete(Result.failure(IllegalStateException("Ses dosyası oluşturulamadı.")))
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    current.shutdown()
                    onComplete(Result.failure(IllegalStateException("Türkçe seslendirme başarısız oldu.")))
                }
                override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
            })
            val result = current.synthesizeToFile(text, Bundle(), output, "shorts_voiceover")
            if (result != TextToSpeech.SUCCESS) {
                current.shutdown()
                onComplete(Result.failure(IllegalStateException("Ses dosyası üretimi başlatılamadı.")))
            }
        }
        engine = tts
    }
}
