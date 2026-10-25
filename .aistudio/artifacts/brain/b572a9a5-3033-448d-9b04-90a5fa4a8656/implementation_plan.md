# Muallimun Arapça Telaffuz ve Konuşma Asistanı — Android Mobil Sürümü

Masaüstü (Python/Tkinter v40.0) olarak geliştirilmiş olan **Arapça Telaffuz ve Konuşma Asistanı** uygulamasının; tüm pedagojik derinliğini, **Canlı Mahreç & Hareke Koçu** algoritmalarını, **Sarf Sözlüğü (Sülâsî Kök ve Türev Ağacı)** yapısını, **Diyalog Canlandırma (Roleplay)**, **Gölgeleme (Shadowing)**, **Dinle & Bul (İstimâ)** sınav merkezini, **Öğretmen Yönetim Paneli** ve **A4 Yazılı Sınav / Ders Notu (Handout) PDF Üreticisini** sıfır özellik kaybıyla **Native Android (Kotlin + Jetpack Compose + Room SQLite)** mimarisine taşıyan kapsamlı uygulama planıdır.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> İstişaremiz sonucunda onayladığınız mimari kararlar aşağıda özetlenmiştir. Planı inceleyip **"Proceed" (Devam Et)** butonuna bastığınızda kodlama aşamasına geçilecektir.

- **Ses Tanıma (STT) ve Mahreç Analizi (Onaylandı)**: Çevrimdışı destekli ve hızlı **Native Android `SpeechRecognizer` (`ar-SA` / `ar-EG`) + Birebir Kotlin Mahreç & Kök Harf NLP Motoru** kullanılacak. Ayrıca Gölgeleme (Shadowing) ve öğrenci ses kaydı arşivleme için düşük gecikmeli `AudioRecord` (.wav kaydedici ve dalga boyu analizörü) entegre edilecek.
- **Veritabanı ve İlerleme Saklama (Onaylandı)**: **Cihaz İçi Yerel Veritabanı (Room / SQLite)** ile tam çevrimdışı çalışma sağlanacak; Excel/Panodan doğrudan yapıştırma (Clipboard Import), CSV/JSON/ZIP paket içe-dışa aktarma ve öğretmene `.ZIP` / `.CSV` ödev paketi paylaşımı (Android `ShareSheet`) desteklenecek.
- **Kapsam (Onaylandı — Sıfır Özellik Kaybı)**:
  1. *Öğrenci Modülleri*: 2x2 Ana Sayfa Dashboard, Görselli & Sarf Kök Rozetli Kelime Modu, Sarf Sözlüğü Keşif Atölyesi, Diyalog (Öğrenme & Roleplay Canlandırma), Anahtar Cümle Modu, 4 Şıklı Dinle & Bul Sınav Merkezi, Çift Dalga Boylu Shadowing (Gölgeleme) Antrenmanı ve SM-2 Akıllı Tekrar ("Günün 15 Tekrarı").
  2. *Raporlar & Ödev Paylaşımı*: Başarı Rozetleri, Paket İlerleme Grafikleri, En Çok Hata Yapılanlarda Öğrenci Sesini (`.wav`) Dinleme ve Öğretmene Ödev `.ZIP` / `.CSV` Gönderimi.
  3. *Öğretmen Yönetim Paneli*: Excel/Panodan Kopyala-Yapıştır, CSV/JSON/ZIP Paket Yükleme (Birleştir veya Sıfırla), Kalite Denetimi, Metin Doktoru ve Otomatik CEFR (A1-C1) Seviye Tahminleyici.
  4. *Mobil A4 Yazılı Sınav & Ders Notu (Handout) PDF Üretici*: Ayrık havuzlu (`ar_to_tr_pool` / `tr_to_ar_pool`) 6 bölümlü yazılı sınav, Karma Sınav modu ve 3 farklı tercüme yerleşimli (Yan Yana, Alt Alta, Sonda Toplu veya Sadece Arapça) Ders Notu PDF/HTML üretici ve doğrudan yazdırma/paylaşma aracı.
- **Başlangıç İçeriği (Onaylandı)**: Uygulama ilk kurulduğunda hemen çalışmaya başlanabilmesi için **zengin gömülü başlangıç veri setiyle** (Görselli Kelime Paketleri, Sülâsî Kökleriyle Sarf Sözlüğü Maddeleri, A1-C1 Günlük Diyaloglar ve Anahtar Cümleler) birlikte gelecek; kullanıcı ve öğretmenler diledikleri zaman yeni paketler ekleyebilecek.

---

## 1. Overview & Core Concept

- **Ne Yapar?**: Arapça öğrenenlerin en büyük ihtiyacı olan doğru telaffuz (*mahreç ve med*), kelime kökü ve vezin farkındalığı (*sarf / iştikak*), cümle akıcılığı ve karşılıklı konuşma (*muhâdese*) becerilerini mobilde sesli etkileşimle geliştiren kapsamlı bir cep dil laboratuvarıdır.
- **Hedef Kitle**: İmam Hatip Liseleri, İlahiyat Fakülteleri, İslami İlimler, TÖMER öğrencileri, Arapça öğretmenleri ve bireysel dil öğrenenler.
- **Temel Değer**: Masaüstündeki `arabic_nlp.py` içinde yer alan 28 mahreç çifti matrisi, kısa kelimelerdeki kök harf koruma kalkanı (*Root Consonant Guard*), 4 harfli isim / 3 harfli fiil vezin ayrımı ve ardışık tekrar tekilleştirici algoritmaları %100 sadakatle Kotlin'e taşınarak mobilde gerçek zamanlı bir **Arapça Fonetik Koçu** sunar.

---

## 2. User Experience & Visual Design

- **Temel Kullanıcı Akışları (Key User Flows)**:
  1. **Karşılama & 7 Adımlı Başlangıç Turu**: İlk açılışta interaktif 7 adımlı tanıtım turu ve Öğrenci Adı Soyadı kaydı (`DataStore` / `Room` üzerinde saklanır).
  2. **Öğrenci Odaklı Ana Sayfa (Dashboard)**: Üstte Arapça selamlama (`أَهْلاً وَسَهْلاً...`), öğrenci adı, seviye unvanı, günlük seri (`🔥 Streak`), puan ve **"📍 Kaldığım Yerden Devam Et & Akıllı Koç Önerisi"** kartı; ortada **2x2 Modül Izgarası** (1. Görselli Kelime & SM-2, 2. Sarf Sözlüğü & Kök Ailesi, 3. Günlük Diyaloglar & Roleplay, 4. Anahtar Cümleler & Dinle-Bul Sınavı); altta ise ayarlardan gizlenebilen **👨‍🏫 Öğretmen, Yazılı Sınav, Ders Notu & Yönetim Araçları** şeridi.
  3. **Kelime & Sarf Kartı Akışı**: Üstte CEFR seviye (`Tümü, A1..C1`) ve paket seçici + hızlı filtre çipleri (`📅 Günün 15 Tekrarı`, `⭐ Tekrarım`, `🎯 Zayıflar`). Kart üzerinde görsel varsa görsel; sözlük kelimesiyse **Altıgen Sülâsî Kök Rozeti (Canvas)** ve tıklanabilir **Türev Ağacı** (*Müfret/Cemî* veya *Mâzî/Muzâri/Masdar* — üzerine basılı tutulduğunda veya bilgi ikonuna dokunulduğunda Türkçe terim açıklaması). Altta `🔊 Kadın`, `🔊 Erkek`, `🗣️ Gölgele`, `🎧 Sesimi Dinle` ve yeşil parlayan `🎤 BAŞLA VE KONUŞ` butonu.
  4. **Canlı Mahreç & Hareke Koçu (Expandable BottomSheet & Sabit Özet Şeridi)**: Her telaffuz sonrasında kartın altında özet teşhis görünür; dokunulduğunda açılan zengin panelde kelime bazlı yeşil/kırmızı doğruluk, **Hedef ➔ Duyulan** kırmızı harf vurgusu, ağız/boğaz çıkış noktası tarifi ve *Fetha, Kesra, Damme, Şedde, Sükûn* hareke rehberi yer alır.
  5. **Diyalog Öğrenme & Roleplay Canlandırma**: Sohbet balonları halinde sağdan sola (RTL) hizalı diyalog akışı. *Roleplay* modunda öğrencinin rolüne sıra geldiğinde Arapça metin maskelenir, Türkçe çeviri gizlenir (`💡 Türkçe İpucu Göster` butonuyla açılabilir), doğru okunan kelimeler anında **Yeşil** olarak açılır ve diyalog sonunda **📊 Konuşma Karnesi** sunulur.

- **Görsel Kimlik ve Tema (Visual Identity & Theme)**:
  - *Estetik Yönelim*: Masaüstü uygulamasının sevilen **"Gece İlim Laboratuvarı" (Deep Slate & Warm Gold)** ve yüksek kontrastlı **"Aydınlık Akademik"** temaları Material 3 tasarım sistemiyle birebir korunacaktır.
  - *Renk Paleti*:
    - Karanlık Tema: Arka Plan `#0F172A`, Kart Yüzeyi `#1E293B`, Panel `#162032`, Arapça Metin & Vurgu `#FFD369` (Warm Gold), Doğru/Mikrofon Parlaması `#00E676` (Emerald Green), Hata/Mahreç Uyarısı `#EF4444` (Coral Red), Birincil Eylem `#3B82F6` (Royal Blue), İkincil/Gölgeleme `#8B5CF6` (Purple).
    - Aydınlık Tema: Arka Plan `#F1F5F9`, Kart `#FFFFFF`, Panel `#E2E8F0`, Arapça Başlık `#1E3A8A` (Deep Navy), Metin `#0F172A`.
  - *Tipografi & Hiyerarşi*: Arapça metinler ve harekelerin (teşkil) üst üste binmeden kristal netliğinde okunması için yerel olarak paketlenmiş **`Amiri`** (`res/font/` altında) font ailesi; Türkçe arayüz için temiz ve okunaklı **`Plus Jakarta Sans`** eşleşmesi. Tüm ekranlarda **A- / A+ Punto büyütme-küçültme** kontrolü.
  - *Etkileşimli Geri Bildirim & Animasyonlar*: Kayıt esnasında kart çerçevesinde canlı **Yeşil Mikrofon Parlaması (Animated Border Glow)**, gerçek zamanlı ses dalga boyu çubukları (Waveform Canvas) ve %75+ kalite puanında **Konfeti Parçacık Animasyonu** + sesli övgü (*Ahsent / Mümtaz*).

---

## 3. Key Product Decisions & Trade-Offs

- **Karar 1: Ses Tanıma (STT) ve Fonetik Değerlendirme Mimarisi**
  - *Seçilen Yaklaşım*: Android yerleşik `SpeechRecognizer` (`RecognizerIntent.LANGUAGE_MODEL_FREE_FORM`, `ar-SA`) ile alınan Arapça transkripsiyonun, masaüstündeki `arabic_nlp.py` kurallarını birebir uygulayan Kotlin `ArabicNlpEngine` motorundan geçirilmesi + `AudioRecord` ile eşzamanlı `.wav` ses kaydı.
  - *Neden?*: Masaüstündeki `faster-whisper` modeli mobilde ~500 MB boyut ve yüksek pil tüketimi yaratırken, Android'in yerleşik Arapça ses motoru sıfır ek APK boyutuyla hem çevrimiçi hem çevrimdışı (cihazda Arapça ses paketi kuruluysa) saniyenin altında yanıt verir. Asıl pedagojik zeka (mahreç matrisi, kök harf koruma kalkanı, vezin/med analizi) Kotlin NLP motorunda olduğu için geri bildirim kalitesi %100 korunur.
- **Karar 2: Yerel Veritabanı (Room SQLite) ve Dosya/Pano Entegrasyonu**
  - *Seçilen Yaklaşım*: Tüm kelime, sarf sözlüğü, diyalog, cümle ve kullanıcı ayar/istatistik tablolarının tek bir merkezi **Room (`MuallimunDatabase`)** çatısı altında toplanması ve ilk açılışta zengin başlangıç veri setiyle tohumlanması (pre-seeding).
  - *Neden?*: Okul ve sınıf ortamlarında internet bağlantısı gerekmeksizin anında açılış, SM-2 sorgularında milisaniyelik hız ve öğretmenlerin Excel panosundan (`ClipboardManager`) veya `.zip/.csv/.json` dosyalarından (`ActivityResultContracts.GetContent`) kolayca içerik yükleyebilmesini sağlar.
- **Karar 3: A4 Yazılı Sınav ve Ders Notu (Handout) Üretimi**
  - *Seçilen Yaklaşım*: Masaüstündeki HTML/CSS tabanlı A4 sınav ve ders notu şablonunun Android `WebView` + `PrintManager` (Doğrudan A4 PDF Kaydetme ve Yazdırma) ile birleştirilmesi.
  - *Neden?*: Arapça RTL metinlerin, 2x2 çoktan seçmeli test ızgarasının, kurumsal antetin ve `page-break-before: always` ile ayrılan Öğretmen Cevap Anahtarının A4 kağıda milimetrik hizayla basılmasını ve WhatsApp/Telegram üzerinden tek tıkla PDF olarak paylaşılmasını garanti eder.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                   JETPACK COMPOSE UI LAYER (Material 3)                    │
│                                                                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────┐  │
│  │  Home Dashboard  │  │   Words & Sarf   │  │  Dialogue Learn &        │  │
│  │  (2x2 Grid +     │  │   Card Screen    │  │  Roleplay Screen         │  │
│  │   Resume Banner) │  │  (Hex Root Tree) │  │  (Progressive Reveal)    │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────────┬─────────────┘  │
│           │                     │                         │                │
│  ┌────────┴─────────┐  ┌────────┴─────────┐  ┌────────────┴─────────────┐  │
│  │  Key Sentences   │  │  Listening Exam  │  │  Modals & Teacher Tools  │  │
│  │  Screen (Word-by │  │  (Dinle & Bul) & │  │  • Live Makhraj Sheet    │  │
│  │  Word Coloring)  │  │  Shadowing Studio│  │  • Sarf Explorer         │  │
│  └────────┬─────────┘  └────────┬─────────┘  │  • Admin & Excel Paste   │  │
│           │                     │            │  • A4 Exam/Handout Print │  │
│           └─────────────────────┼────────────┴────────────┬─────────────┘  │
└─────────────────────────────────┼─────────────────────────┼────────────────┘
                                  ▼                         ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                 VIEWMODEL & DOMAIN LAYER (StateFlow + MVVM)                │
│                                                                            │
│  ┌───────────────────────────┐  ┌───────────────────┐  ┌────────────────┐  │
│  │     MainAppViewModel      │  │  ArabicNlpEngine  │  │ Exam & Handout │  │
│  │ • Mode & Navigation State │  │ • 28 Makhraj Map  │  │ Generator      │  │
│  │ • SM-2 Spaced Repetition  │  │ • Root Guard      │  │ • Disjoint Pool│  │
│  │ • Adaptive Coaching Logic │  │ • Wazn/Med Coach  │  │ • Mixed Source │  │
│  │ • Roleplay Turn Manager   │  │ • Haraka Guide    │  │ • 3 TR Layouts │  │
│  │ • Streak & Badges Engine  │  │ • CEFR Estimator  │  │ • PrintManager │  │
│  └─────────────┬─────────────┘  └─────────▲─────────┘  └────────▲───────┘  │
└────────────────┼──────────────────────────┼─────────────────────┼──────────┘
                 ▼                          │                     │
┌───────────────────────────────────────────┴─────────────────────┴──────────┐
│                    DATA & HARDWARE SERVICES LAYER                          │
│                                                                            │
│  ┌────────────────────────────────────┐  ┌──────────────────────────────┐  │
│  │   MuallimunRepository & Room DB    │  │   Audio & Speech Engine      │  │
│  │ • WordEntity, PackageEntity        │  │ • Android TextToSpeech (AR)  │  │
│  │ • SozlukEntity (Root, Forms)       │  │   (Slow %25, Male/Female)    │  │
│  │ • DialogueEntity, DialogueLine     │  │ • SpeechRecognizer (ar-SA)   │  │
│  │ • SentenceEntity, ConfigEntity     │  │ • WavRecorder & Envelope     │  │
│  │ • Initial Seed Data + ZIP/CSV/Clip │  │   Extractor (48-bar Waveform)│  │
│  └────────────────────────────────────┘  └──────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────┘
```

### Data Model & State

1. **`WordEntity` (`kelimeler`) & `PackageEntity` (`paketler`)**:
   - `id`, `paketId`, `arapca`, `turkce`, `gorsel`, `ses`, `dogru`, `yanlis`, `isFav`, `isLearned`, `seviye` (`A1..C1`), `intervalDays` (SM-2), `easeFactor` (SM-2), `nextReview`, `lastPracticed`.
2. **`SozlukEntity` (`sozluk`)**:
   - `id`, `tur` (`isim` / `fiil`), `kok` (Sülâsî kök, örn: `ك ت ب`), `mufret`, `cemi`, `mazi`, `muzari`, `masdar`, `anlamTr`, `kategoriId`, `kategori`, `emoji`, `seviye`, `wordId`.
3. **`DialogueEntity` (`diyaloglar`) & `DialogueLineEntity` (`diyalog_satirlari`)**:
   - Diyalog başlıkları (`baslikAr`, `baslikTr`, `seviye`, `konu`) ve sıralı konuşma satırları (`sira`, `konusmaci`, `konusmaciAr`, `cinsiyet` `E/K`, `arapca`, `turkce`, `ses`, `dogru`, `yanlis`).
4. **`SentenceEntity` (`cumleler`)**:
   - `id`, `paketId`, `arapca`, `turkce`, `ses`, `isFav`, `isLearned`, `dogru`, `yanlis`, `seviye`, `intervalDays`, `easeFactor`, `nextReview`.
5. **`AppConfig` (`ayarlar`)**:
   - Öğrenci adı (`student_name`), aktif tema (`dark`/`light`), günlük seri (`streak_days`), günlük hedef (`daily_count`/`daily_goal`), toplam puan (`total_points`), öğretmen barı görünürlüğü (`show_teacher_bar`), yavaş seslendirme (`slow_tts`), eller serbest mod (`auto_start`) ve son çalışılan mod/paket bilgisi.

### Interactive Component & State Mapping

- **Kelime ve Sarf Kartı Etkileşimleri**:
  - Altıgen Kök Rozetine dokunulduğunda -> `SarfExplorerSheet` doğrudan o sülâsî köke filtrelenmiş olarak açılır ve aynı kökten türeyen tüm akraba kelimeleri listeler.
  - Kart üzerindeki Türev Ağacı kutularına (*Müfret, Cemî, Mâzî, Muzâri, Masdar*) dokunulduğunda -> Aktif telaffuz hedefi (`activeMorphArabic`) seçilen forma dönüşür, TTS ile seslendirilir ve mahreç koçu o formu bekler.
  - Üst üste 3 hatalı denemede -> Adaptif Koç devreye girerek `🐢 Yavaş Seslendirme (%25)` modunu otomatik etkinleştirir ve mahreç ipucu toast bildirimi gösterir. Üst üste 6 doğru serisinde -> Bir üst CEFR seviyesine geçiş önerisi sunar.
- **Diyalog Roleplay Akışı**:
  - Kullanıcı bir rol seçip `🎬 Canlandırmayı Başlat` dediğinde -> Karşı karakterin satırları otomatik TTS ile seslendirilir ve vurgulanır; sıra kullanıcının rolüne geldiğinde bip sesi çalar, mikrofon açılır ve doğru okunan kelimeler anında yeşil renkte açılır (`roleplayRevealedWords`).
- **Yönetim Paneli, Excel Pano ve Sınav Üretici Akışı**:
  - `📋 Excel Yapıştır` -> Android panosundaki (`ClipboardManager`) Tab veya `;` ayrılmış metni ayrıştırır, eksik satırları satır numarasıyla raporlar ve veritabanına ekler.
  - `🖨️ Sınav & Ders Notu` -> Seçilen soru sayılarına (0-50) veya Ders Notu yerleşimine göre HTML/CSS çıktısını oluşturur ve Android `PrintManager` üzerinden doğrudan A4 PDF olarak kaydeder veya paylaşır.
