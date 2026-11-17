package com.shortsai.studio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import coil.compose.AsyncImage

private data class StoryScene(
    val title: String,
    val narration: String,
    val visualPrompt: String,
    val subtitle: String,
    val imageUrl: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen() {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("3D çizgi film") }
    var duration by remember { mutableStateOf("45 saniye") }
    var storyTitle by remember { mutableStateOf("") }
    var scenes by remember { mutableStateOf(listOf<StoryScene>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isExporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    var soundtrackUri by remember { mutableStateOf<Uri?>(null) }

    val soundtrackPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        soundtrackUri = uri
        if (uri != null) runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && scenes.isNotEmpty()) {
            val text = buildString {
                appendLine("SHORTS AI STUDIO - HİKÂYE TASLAĞI")
                appendLine("Başlık: $storyTitle")
                appendLine("Tarz: $style | Süre: $duration | Format: Dikey 9:16")
                appendLine()
                scenes.forEachIndexed { index, scene ->
                    appendLine("SAHNE ${index + 1}: ${scene.title}")
                    appendLine("Seslendirme: ${scene.narration}")
                    appendLine("Altyazı: ${scene.subtitle}")
                    appendLine("Görsel promptu: ${scene.visualPrompt}")
                    appendLine()
                }
            }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
            }
        }
    }

    DisposableEffect(context) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("tr", "TR")
            }
        }
        tts = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts = null
        }
    }

    fun createStoryboard() {
        val idea = prompt.trim().ifBlank { "Minik bir kedinin uzayda kayıp bir yıldızı bulma macerası" }
        storyTitle = idea.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("tr", "TR")) else it.toString() }
        val visualStyle = if (style == "Gerçekçi") {
            "Photorealistic cinematic, detailed lighting, realistic textures"
        } else {
            "High quality colorful 3D animated family film, expressive cute characters"
        }
        scenes = listOf(
            StoryScene(
                "Merak uyandıran açılış",
                "Bir dakika! Böyle bir şey daha önce hiç görülmemişti...",
                "$visualStyle. Dikey 9:16 yakın plan, $idea. İlk kare çok dikkat çekici.",
                "Böyle bir şey daha önce hiç görülmemişti!"
            ),
            StoryScene(
                "Beklenmedik sorun",
                "Tam her şey yolundayken, kahramanımız büyük bir sürprizle karşılaştı.",
                "$visualStyle. $idea sırasında beklenmedik komik bir sorun ortaya çıkıyor. Dikey 9:16.",
                "Ama işler bir anda karıştı!"
            ),
            StoryScene(
                "İpucunu bulma",
                "Etrafına dikkatlice baktı ve ona yol gösterecek küçük bir ipucu buldu.",
                "$visualStyle. Kahraman önemli bir ipucu keşfediyor, yüzünde şaşkın ve sevimli ifade. Dikey 9:16.",
                "Derken küçük bir ipucu buldu."
            ),
            StoryScene(
                "Cesur hamle",
                "Biraz korksa da pes etmedi. Derin bir nefes alıp harekete geçti!",
                "$visualStyle. Kahraman hızlı ve eğlenceli bir aksiyon anında, dinamik kamera. Dikey 9:16.",
                "Pes etmek yok!"
            ),
            StoryScene(
                "Şaşırtıcı final",
                "Ve sonunda öyle komik bir şey oldu ki herkes kahkahalara boğuldu!",
                "$visualStyle. $idea için şaşırtıcı, neşeli, aile dostu final. Parlak sinematik ışık, dikey 9:16.",
                "Sonu hiç tahmin ettiğin gibi değil!"
            )
        )
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SHORTS AI STUDIO", color = Color(0xFF211D2B), fontWeight = FontWeight.Black) },
                    actions = {
                        Text("9:16", modifier = Modifier.padding(end = 18.dp), color = Color(0xFF7657F6), fontWeight = FontWeight.Bold)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFF7F5FC),
                        titleContentColor = Color(0xFF211D2B)
                    )
                )
            },
            containerColor = Color(0xFFF7F5FC)
        ) { inset ->
            Column(
                Modifier.padding(inset).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF201B3B)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Fikrini Shorts'a dönüştür", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Türkçe senaryo • 5 sahne • Altyazı • Görsel promptları • Sesli önizleme",
                            color = Color(0xFFE0D9FF), lineHeight = 22.sp)
                    }
                }

                Text("Video tarzı", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("3D çizgi film", "Gerçekçi").forEach { option ->
                        FilterChip(selected = style == option, onClick = { style = option }, label = { Text(option) })
                    }
                }

                Text("Hedef video süresi", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("30 saniye", "45 saniye", "60 saniye").forEach { option ->
                        FilterChip(selected = duration == option, onClick = { duration = option }, label = { Text(option) })
                    }
                }

                Text("Video fikrin veya promptun", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = Color(0xFF211D2B)
                    ),
                    placeholder = {
                        Text(
                            "Örn: Minik bir kedinin uzayda komik macerası...",
                            color = Color(0xFF77717F),
                            fontSize = 15.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF211D2B),
                        unfocusedTextColor = Color(0xFF211D2B),
                        cursorColor = Color(0xFF7657F6),
                        focusedBorderColor = Color(0xFF7657F6),
                        unfocusedBorderColor = Color(0xFF8B8496)
                    ),
                    shape = RoundedCornerShape(16.dp)
                )

                Button(
                    onClick = { createStoryboard() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7657F6))
                ) {
                    Text("✦ Hikâye ve 5 sahne oluştur")
                }

                if (scenes.isNotEmpty()) {
                    Text("Hikâye taslağı", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(storyTitle, color = Color(0xFF7657F6), fontWeight = FontWeight.Bold)
                    Text("Tarz: $style  •  Süre: $duration  •  Dikey 9:16", color = Color.Gray)

                    Button(
                        onClick = {
                            scenes = scenes.map { scene ->
                                val imagePrompt = "${scene.visualPrompt}. Vertical portrait 9:16 composition, no text, consistent main character."
                                val url = "https://image.pollinations.ai/prompt/${Uri.encode(imagePrompt)}?width=576&height=1024&model=flux&nologo=true"
                                scene.copy(imageUrl = url)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF201B3B))
                    ) { Text("✦ 5 sahnenin görsellerini üret") }

                    OutlinedButton(
                        onClick = { soundtrackPicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (soundtrackUri == null) "♫ Telefonda müzik / ses dosyası seç" else "♫ Ses dosyası seçildi — değiştirmek için dokun") }

                    scenes.forEachIndexed { index, scene ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("SAHNE ${index + 1} — ${scene.title}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7657F6))
                                Text("Seslendirme", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = scene.narration,
                                    onValueChange = { updated ->
                                        scenes = scenes.toMutableList().also { it[index] = scene.copy(narration = updated) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    textStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, color = Color(0xFF211D2B)),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF211D2B),
                                        unfocusedTextColor = Color(0xFF211D2B),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        cursorColor = Color(0xFF7657F6),
                                        focusedBorderColor = Color(0xFF7657F6),
                                        unfocusedBorderColor = Color(0xFFAAA3B5)
                                    )
                                )
                                Text("Ekran altyazısı", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = scene.subtitle,
                                    onValueChange = { updated ->
                                        scenes = scenes.toMutableList().also { it[index] = scene.copy(subtitle = updated) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 1,
                                    textStyle = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = Color(0xFF211D2B)),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF211D2B),
                                        unfocusedTextColor = Color(0xFF211D2B),
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        cursorColor = Color(0xFF7657F6),
                                        focusedBorderColor = Color(0xFF7657F6),
                                        unfocusedBorderColor = Color(0xFFAAA3B5)
                                    )
                                )
                                Text("Görsel üretim promptu", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(scene.visualPrompt, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 16.sp)
                                if (scene.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = scene.imageUrl,
                                        contentDescription = "Sahne ${index + 1} yapay zekâ görseli",
                                        modifier = Modifier.fillMaxWidth().height(320.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val imagePrompt = "${scene.visualPrompt}. Vertical portrait 9:16 composition, no text, consistent main character."
                                        val url = "https://image.pollinations.ai/prompt/${Uri.encode(imagePrompt)}?width=576&height=1024&model=flux&nologo=true"
                                        scenes = scenes.toMutableList().also { it[index] = scene.copy(imageUrl = url) }
                                    }
                                ) { Text(if (scene.imageUrl.isBlank()) "✦ Bu sahnenin görselini üret" else "↻ Görseli yeniden üret") }
                                OutlinedButton(onClick = {
                                    tts?.language = Locale("tr", "TR")
                                    tts?.speak(scene.narration, TextToSpeech.QUEUE_FLUSH, null, "scene_$index")
                                }) { Text("▶ Bu sahneyi seslendir") }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            isExporting = true
                            exportStatus = "Görseller indiriliyor ve MP4 hazırlanıyor..."
                            SlideshowExporter.export(
                                context,
                                scenes.map { VideoFrameSource(it.imageUrl, it.subtitle) },
                                duration.filter { it.isDigit() }.toIntOrNull() ?: 45,
                                soundtrackUri
                            ) { result ->
                                Handler(Looper.getMainLooper()).post {
                                    isExporting = false
                                    exportStatus = result.fold(
                                        onSuccess = { "Video Galeri'ye kaydedildi: $it" },
                                        onFailure = { "Video oluşturulamadı: ${it.message ?: "Bilinmeyen hata"}" }
                                    )
                                }
                            }
                        },
                        enabled = !isExporting && scenes.all { it.imageUrl.isNotBlank() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7657F6))
                    ) {
                        Text(if (isExporting) "Video hazırlanıyor..." else "▶ MP4 videoyu oluştur ve Galeri'ye kaydet")
                    }
                    if (exportStatus.isNotBlank()) {
                        Text(exportStatus, color = Color(0xFF3D315F), fontSize = 13.sp)
                    }

                    Button(
                        onClick = { saveLauncher.launch("shorts_ai_studio_hikaye.txt") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF201B3B))
                    ) { Text("Hikâye ve sahneleri telefona kaydet") }

                    OutlinedButton(
                        onClick = {
                            val allText = scenes.joinToString(" ") { it.narration }
                            tts?.language = Locale("tr", "TR")
                            tts?.speak(allText, TextToSpeech.QUEUE_FLUSH, null, "full_story")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Tüm hikâyeyi Türkçe dinle") }

                    Text(
                        "MP4 videoya seçtiğin müzik veya ses dosyası eklenir. Türkçe TTS seslendirmesi şimdilik uygulama içi önizlemedir. Sahne görselleri hareket efektiyle videoya dönüştürülür; gerçek AI video üretimi için harici bir video servisi gerekir.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
