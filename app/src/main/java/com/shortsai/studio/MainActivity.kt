package com.shortsai.studio

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StudioScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("3D çizgi film") }
    var story by remember { mutableStateOf("") }
    var scenes by remember { mutableStateOf(listOf<String>()) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("tr", "TR")
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("SHORTS AI STUDIO", fontWeight = FontWeight.Black) }) },
            containerColor = Color(0xFFF7F5FC)) { inset ->
            Column(Modifier.padding(inset).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF201B3B)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Fikrini Shorts'a dönüştür", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Türkçe hikâye • Sahne planı • Sesli önizleme", color = Color(0xFFE0D9FF))
                    }
                }
                Text("Video tarzı", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("3D çizgi film", "Gerçekçi").forEach { option ->
                        FilterChip(selected = style == option, onClick = { style = option }, label = { Text(option) })
                    }
                }
                Text("Video fikrin veya promptun", fontWeight = FontWeight.Bold)
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
                    minLines = 3, placeholder = { Text("Örn: Minik bir kedinin uzayda komik macerası...") },
                    shape = RoundedCornerShape(16.dp))
                Button(onClick = {
                    val idea = prompt.trim().ifBlank { "Minik bir kedinin kayıp yıldızı bulma macerası" }
                    story = "Bir gün $idea. Kahramanımız beklenmedik bir sorunla karşılaştı. Önce korktu ama küçük bir ipucu buldu. Cesaretini toplayıp harekete geçti. Herkes tam umudunu kaybetmişken şaşırtıcı bir çözüm ortaya çıktı. Macera mutlu ve komik bir sonla bitti!"
                    scenes = listOf("İlk 3 saniye: Merak uyandıran açılış",
                        "Sorun: Kahramanın karşılaştığı beklenmedik olay",
                        "Keşif: Çözüm için bulunan küçük ipucu",
                        "Aksiyon: Kahramanın cesur hamlesi",
                        "Final: Şaşırtıcı ve aile dostu son")
                }, modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7657F6))) {
                    Text("✦ Hikâye ve sahneleri oluştur")
                }
                if (story.isNotBlank()) {
                    Text("Hikâye taslağı", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Tarz: $style", color = Color(0xFF7657F6), fontWeight = FontWeight.Bold)
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(story, lineHeight = 24.sp)
                            OutlinedButton(onClick = {
                                tts?.language = Locale("tr", "TR")
                                tts?.speak(story, TextToSpeech.QUEUE_FLUSH, null, "story")
                            }) { Text("Türkçe sesli dinle") }
                        }
                    }
                    Text("Sahne planı", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    scenes.forEachIndexed { i, scene ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Text((i + 1).toString() + ". " + scene, Modifier.padding(14.dp))
                        }
                    }
                    Text("Bu ilk sürüm hikâye ve sahne taslağı oluşturur. Yapay zekâ video üretimi ve MP4 dışa aktarma sonraki geliştirme aşamasıdır.",
                        color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
