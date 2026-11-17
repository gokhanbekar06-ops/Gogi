# Shorts AI Studio

Türkçe odaklı Android YouTube Shorts hazırlama uygulaması.

## Mevcut Android özellikleri
- Prompttan Türkçe 5 sahnelik hikâye taslağı
- 3D çizgi film / gerçekçi görsel tarzı
- Sahne görselleri oluşturma ve yeniden üretme
- Türkçe altyazı ve cihaz içi TTS önizlemesi
- 9:16 MP4 slayt gösterisi dışa aktarma
- Telefonda seçilen müzik ve efekt dosyalarını MP4'e ayrı ses kanalları olarak ekleme

**Henüz tamamlanmayanlar:** TTS sesinin MP4'e gömülmesi, sesleri tek miksajda birleştirip ayrı ses seviyeleri uygulama ve gerçek AI hareketli video sahnelerini Android dışa aktarma akışına entegre etme.

## Güvenli AI video servisi

`cloudflare-worker/` altında Agnes AI için güvenli bir Cloudflare Worker proxy taslağı bulunur. API anahtarı APK'ya veya GitHub'a konmaz; Cloudflare Worker secret olarak saklanır.

1. Cloudflare hesabında Workers & Pages bölümünden yeni Worker oluşturun.
2. `cloudflare-worker/src/index.js` içeriğini Worker koduna ekleyin.
3. Worker secrets bölümünde `AGNES_API_KEY` adında secret oluşturup Agnes API anahtarını girin.
4. Worker'ı yayınlayın. Uygulama entegrasyonu için yayınlanan Worker URL'si kullanılacaktır.

Worker uçları: `POST /generate` (prompt ve isteğe bağlı görsel URL'si) ve `GET /status?video_id=...`.

Agnes video API'si asenkron çalışır. Ücretsiz/default erişimde yayınlanan gerçek istek hızı dakikada yaklaşık 1 istekle sınırlıdır; ücretsiz erişim sınırsız değildir ve kota/erişilebilirlik değişebilir. Güncel koşulları servis panelinden kontrol edin.

## APK oluşturma
GitHub Actions sekmesinden başarılı Android APK iş akışını açın ve `shorts-ai-studio-debug-apk` artifact ZIP dosyasını indirin.