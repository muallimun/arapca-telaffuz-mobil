package com.example.data

object InitialSeedData {

    val initialPackages = listOf(
        // Görselli Kelime Paketleri
        PackageEntity("hayvanlar_doga", "🌿 Hayvanlar & Doğa", "words", "A1", "Doğa & Hayvanlar"),
        PackageEntity("gunluk_esya", "🏠 Ev, Eşya & Mekanlar", "words", "A1", "Ev & Yaşam"),
        PackageEntity("egitim_ilim", "🎓 Okul, İlim & Kalem", "words", "A1", "Eğitim"),
        PackageEntity("saglik_insan", "🏥 Sağlık, İnsan & Aile", "words", "A2", "Sağlık & Aile"),
        PackageEntity("sozluk_tumu", "📖 Sarf Sözlüğü (Tüm Kelimeler)", "words", "A2", "Sarf Sözlüğü"),
        PackageEntity("szl_egitim", "📖 Sözlük: 🎓 Eğitim & İlim", "words", "A2", "🎓 Eğitim & İlim"),
        PackageEntity("szl_saglik", "📖 Sözlük: 🏥 Sağlık & Vücut", "words", "A2", "🏥 Sağlık & Vücut"),
        PackageEntity("szl_seyahat", "📖 Sözlük: ✈️ Seyahat & Ulaşım", "words", "A2", "✈️ Seyahat & Ulaşım"),
        PackageEntity("szl_yemek", "📖 Sözlük: 🍽️ Yemek & Mutfak", "words", "A1", "🍽️ Yemek & Mutfak"),
        PackageEntity("szl_is", "📖 Sözlük: 💼 İş, Meslek & Yönetim", "words", "B1", "💼 İş, Meslek & Yönetim"),
        PackageEntity("szl_duygu", "📖 Sözlük: 💭 Duygu, Düşünce & İletişim", "words", "B1", "💭 Duygu, Düşünce & İletişim"),

        // Diyalog Paketi
        PackageEntity("muhadesat_paketi", "💬 Muhâdesât (Günlük Diyaloglar)", "dialogue", "A1", "Günlük Hayat"),

        // Cümle Paketleri
        PackageEntity("gunluk_kaliplar", "📜 Günlük İletişim Kalıpları", "sentences", "A1", "Günlük İletişim"),
        PackageEntity("akademik_cumleler", "🏛️ Akademik & Edebi Cümleler", "sentences", "B1", "Akademik Arapça"),
        PackageEntity("hikmetli_sozler", "🌟 Atasözleri & Hikmetli İfadeler", "sentences", "B2", "Edebiyat & Hikmet")
    )

    val initialVisualWords = listOf(
        // Paket 1: Hayvanlar & Doğa (gorsel alanında emoji/ikon kodu ile zengin kart görseli)
        WordEntity("w_ari", "hayvanlar_doga", "نَحْلَةٌ", "Arı", "🐝", null, seviye = "A1"),
        WordEntity("w_aslan", "hayvanlar_doga", "أَسَدٌ", "Aslan", "🦁", null, seviye = "A1"),
        WordEntity("w_at", "hayvanlar_doga", "حِصَانٌ", "At", "🐎", null, seviye = "A1"),
        WordEntity("w_deve", "hayvanlar_doga", "جَمَلٌ", "Deve", "🐪", null, seviye = "A1"),
        WordEntity("w_kus", "hayvanlar_doga", "عُصْفُورٌ", "Serçe / Kuş", "🐦", null, seviye = "A1"),
        WordEntity("w_kedi", "hayvanlar_doga", "قِطَّةٌ", "Kedi", "🐱", null, seviye = "A1"),
        WordEntity("w_kopek", "hayvanlar_doga", "كَلْبٌ", "Köpek", "🐕", null, seviye = "A1"),
        WordEntity("w_balik", "hayvanlar_doga", "سَمَكَةٌ", "Balık", "🐟", null, seviye = "A1"),
        WordEntity("w_agac", "hayvanlar_doga", "شَجَرَةٌ", "Ağaç", "🌳", null, seviye = "A1"),
        WordEntity("w_gunes", "hayvanlar_doga", "شَمْسٌ", "Güneş", "☀️", null, seviye = "A1"),
        WordEntity("w_ay", "hayvanlar_doga", "قَمَرٌ", "Ay", "🌙", null, seviye = "A1"),
        WordEntity("w_deniz", "hayvanlar_doga", "بَحْرٌ", "Deniz", "🌊", null, seviye = "A1"),

        // Paket 2: Ev, Eşya & Mekanlar
        WordEntity("w_ev", "gunluk_esya", "بَيْتٌ", "Ev / Konut", "🏠", null, seviye = "A1"),
        WordEntity("w_kapi", "gunluk_esya", "بَابٌ", "Kapı", "🚪", null, seviye = "A1"),
        WordEntity("w_pencere", "gunluk_esya", "نَافِذَةٌ", "Pencere", "🪟", null, seviye = "A1"),
        WordEntity("w_anahtar", "gunluk_esya", "مِفْتَاحٌ", "Anahtar", "🔑", null, seviye = "A1"),
        WordEntity("w_yatak", "gunluk_esya", "سَرِيرٌ", "Yatak", "🛏️", null, seviye = "A1"),
        WordEntity("w_sandalye", "gunluk_esya", "كُرْسِيٌّ", "Sandalye", "🪑", null, seviye = "A1"),
        WordEntity("w_saat", "gunluk_esya", "سَاعَةٌ", "Saat", "⌚", null, seviye = "A1"),
        WordEntity("w_telefon", "gunluk_esya", "هَاتِفٌ", "Telefon", "📱", null, seviye = "A1"),
        WordEntity("w_araba", "gunluk_esya", "سَيَّارَةٌ", "Araba / Otomobil", "🚗", null, seviye = "A1"),
        WordEntity("w_ucak", "gunluk_esya", "طَائِرَةٌ", "Uçak", "✈️", null, seviye = "A2"),

        // Paket 3: Okul, İlim & Kalem
        WordEntity("w_kitap", "egitim_ilim", "كِتَابٌ", "Kitap", "📘", null, seviye = "A1"),
        WordEntity("w_kalem", "egitim_ilim", "قَلَمٌ", "Kalem", "✒️", null, seviye = "A1"),
        WordEntity("w_defter", "egitim_ilim", "دَفْتَرٌ", "Defter", "📓", null, seviye = "A1"),
        WordEntity("w_okul", "egitim_ilim", "مَدْرَسَةٌ", "Okul", "🏫", null, seviye = "A1"),
        WordEntity("w_universite", "egitim_ilim", "جَامِعَةٌ", "Üniversite", "🏛️", null, seviye = "A2"),
        WordEntity("w_kutuphane", "egitim_ilim", "مَكْتَبَةٌ", "Kütüphane / Kitapçı", "📚", null, seviye = "A2"),
        WordEntity("w_ogretmen", "egitim_ilim", "مُعَلِّمٌ", "Öğretmen / Muallim", "👨‍🏫", null, seviye = "A1"),
        WordEntity("w_ogrenci", "egitim_ilim", "طَالِبٌ", "Öğrenci / Talebe", "🎓", null, seviye = "A1"),
        WordEntity("w_bilgisayar", "egitim_ilim", "حَاسُوبٌ", "Bilgisayar", "💻", null, seviye = "A2"),
        WordEntity("w_sinav", "egitim_ilim", "اِمْتِحَانٌ", "Sınav / İmtihan", "📝", null, seviye = "A2"),

        // Paket 4: Sağlık, İnsan & Aile
        WordEntity("w_doktor", "saglik_insan", "طَبِيبٌ", "Doktor / Hekim", "🩺", null, seviye = "A1"),
        WordEntity("w_hastane", "saglik_insan", "مُسْتَشْفًى", "Hastane", "🏥", null, seviye = "A2"),
        WordEntity("w_ilac", "saglik_insan", "دَوَاءٌ", "İlaç / Deva", "💊", null, seviye = "A2"),
        WordEntity("w_kalp", "saglik_insan", "قَلْبٌ", "Kalp / Gönül", "❤️", null, seviye = "A1"),
        WordEntity("w_goz", "saglik_insan", "عَيْنٌ", "Göz / Pınar", "👁️", null, seviye = "A1"),
        WordEntity("w_el", "saglik_insan", "يَدٌ", "El", "✋", null, seviye = "A1"),
        WordEntity("w_aile", "saglik_insan", "أُسْرَةٌ", "Aile", "👨‍👩‍👧‍👦", null, seviye = "A1"),
        WordEntity("w_arkadas", "saglik_insan", "صَدِيقٌ", "Arkadaş / Dost", "🤝", null, seviye = "A1")
    )

    val initialSozlukEntries = listOf(
        // ك ت ب Kök Ailesi
        SozlukEntity("kelime_001", "isim", "ك ت ب", mufret = "كِتَابٌ", cemi = "كُتُبٌ", anlamTr = "kitap, eser", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_001"),
        SozlukEntity("kelime_002", "fiil", "ك ت ب", mazi = "كَتَبَ", muzari = "يَكْتُبُ", masdar = "كِتَابَةٌ", anlamTr = "yazmak, kaleme almak", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_002"),
        SozlukEntity("kelime_003", "isim", "ك ت ب", mufret = "كَاتِبٌ", cemi = "كُتَّابٌ", anlamTr = "yazar, kâtip", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A2", wordId = "szl_kelime_003"),
        SozlukEntity("kelime_004", "isim", "ك ت ب", mufret = "مَكْتَبَةٌ", cemi = "مَكْتَبَاتٌ", anlamTr = "kütüphane, kitapçı", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A2", wordId = "szl_kelime_004"),
        SozlukEntity("kelime_005", "isim", "ك ت ب", mufret = "مَكْتَبٌ", cemi = "مَكَاتِبُ", anlamTr = "ofis, çalışma masası, büro", kategoriId = "szl_is", kategori = "💼 İş, Meslek & Yönetim", emoji = "💼", seviye = "A2", wordId = "szl_kelime_005"),

        // ع ل م Kök Ailesi
        SozlukEntity("kelime_006", "isim", "ع ل م", mufret = "عِلْمٌ", cemi = "عُلُومٌ", anlamTr = "ilim, bilim, bilgi", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_006"),
        SozlukEntity("kelime_007", "fiil", "ع ل م", mazi = "عَلِمَ", muzari = "يَعْلَمُ", masdar = "عِلْمٌ", anlamTr = "bilmek, haberdar olmak", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_007"),
        SozlukEntity("kelime_008", "isim", "ع ل م", mufret = "عَالِمٌ", cemi = "عُلَمَاءُ", anlamTr = "âlim, bilim insanı", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A2", wordId = "szl_kelime_008"),
        SozlukEntity("kelime_009", "isim", "ع ل م", mufret = "مُعَلِّمٌ", cemi = "مُعَلِّمُونَ", anlamTr = "öğretmen, muallim", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_009"),
        SozlukEntity("kelime_010", "fiil", "ع ل م", mazi = "تَعَلَّمَ", muzari = "يَتَعَلَّمُ", masdar = "تَعَلُّمٌ", anlamTr = "öğrenmek, tahsil görmek", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A2", wordId = "szl_kelime_010"),

        // د ر س Kök Ailesi
        SozlukEntity("kelime_011", "isim", "د ر س", mufret = "دَرْسٌ", cemi = "دُرُوسٌ", anlamTr = "ders, ibret", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_011"),
        SozlukEntity("kelime_012", "fiil", "د ر س", mazi = "دَرَسَ", muzari = "يَدْرُسُ", masdar = "دِرَاسَةٌ", anlamTr = "ders çalışmak, okumak, incelemek", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_012"),
        SozlukEntity("kelime_013", "isim", "د ر س", mufret = "مَدْرَسَةٌ", cemi = "مَدَارِسُ", anlamTr = "okul, medrese", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A1", wordId = "szl_kelime_013"),
        SozlukEntity("kelime_014", "isim", "د ر س", mufret = "مُدَرِّسٌ", cemi = "مُدَرِّسُونَ", anlamTr = "ders veren hoca, müderris", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "A2", wordId = "szl_kelime_014"),

        // ط ب ب ve ش ف ي (Sağlık)
        SozlukEntity("kelime_015", "isim", "ط ب ب", mufret = "طَبِيبٌ", cemi = "أَطِبَّاءُ", anlamTr = "doktor, tabip, hekim", kategoriId = "szl_saglik", kategori = "🏥 Sağlık & Vücut", emoji = "🏥", seviye = "A1", wordId = "szl_kelime_015"),
        SozlukEntity("kelime_016", "isim", "ط ب ب", mufret = "طِبٌّ", cemi = "—", anlamTr = "tıp ilmi, hekimlik", kategoriId = "szl_saglik", kategori = "🏥 Sağlık & Vücut", emoji = "🏥", seviye = "A2", wordId = "szl_kelime_016"),
        SozlukEntity("kelime_017", "isim", "م ر ض", mufret = "مَرِيضٌ", cemi = "مَرْضَى", anlamTr = "hasta, rahatsız", kategoriId = "szl_saglik", kategori = "🏥 Sağlık & Vücut", emoji = "🏥", seviye = "A1", wordId = "szl_kelime_017"),
        SozlukEntity("kelime_018", "isim", "م ر ض", mufret = "مُمَرِّضَةٌ", cemi = "مُمَرِّضَاتٌ", anlamTr = "hemşire, hastabakıcı", kategoriId = "szl_saglik", kategori = "🏥 Sağlık & Vücut", emoji = "🏥", seviye = "A2", wordId = "szl_kelime_018"),
        SozlukEntity("kelime_019", "fiil", "ش ف ي", mazi = "شَفَى", muzari = "يَشْفِي", masdar = "شِفَاءٌ", anlamTr = "şifa vermek, iyileştirmek", kategoriId = "szl_saglik", kategori = "🏥 Sağlık & Vücut", emoji = "🏥", seviye = "A2", wordId = "szl_kelime_019"),
        SozlukEntity("kelime_020", "isim", "ش ف ي", mufret = "مُسْتَشْفًى", cemi = "مُسْتَشْفَيَاتٌ", anlamTr = "hastane, şifahane", kategoriId = "szl_saglik", kategori = "🏥 Sağlık & Vücut", emoji = "🏥", seviye = "A2", wordId = "szl_kelime_020"),

        // س ف ر ve ر ك ب (Seyahat)
        SozlukEntity("kelime_021", "fiil", "س ف ر", mazi = "سَافَرَ", muzari = "يُسَافِرُ", masdar = "سَفَرٌ", anlamTr = "seyahat etmek, yolculuğa çıkmak", kategoriId = "szl_seyahat", kategori = "✈️ Seyahat & Ulaşım", emoji = "✈️", seviye = "A2", wordId = "szl_kelime_021"),
        SozlukEntity("kelime_022", "isim", "س ف ر", mufret = "مُسَافِرٌ", cemi = "مُسَافِرُونَ", anlamTr = "yolcu, seyyah", kategoriId = "szl_seyahat", kategori = "✈️ Seyahat & Ulaşım", emoji = "✈️", seviye = "A2", wordId = "szl_kelime_022"),
        SozlukEntity("kelime_023", "isim", "س ف ر", mufret = "سَفَارَةٌ", cemi = "سَفَارَاتٌ", anlamTr = "büyükelçilik, sefaret", kategoriId = "szl_is", kategori = "💼 İş, Meslek & Yönetim", emoji = "💼", seviye = "B1", wordId = "szl_kelime_023"),
        SozlukEntity("kelime_024", "fiil", "ر ك ب", mazi = "رَكِبَ", muzari = "يَرْكَبُ", masdar = "رُكُوبٌ", anlamTr = "binmek (taşıta, ata vb.)", kategoriId = "szl_seyahat", kategori = "✈️ Seyahat & Ulaşım", emoji = "✈️", seviye = "A1", wordId = "szl_kelime_024"),
        SozlukEntity("kelime_025", "isim", "ر ك ب", mufret = "رَاكِبٌ", cemi = "رُكَّابٌ", anlamTr = "yolcu, binici", kategoriId = "szl_seyahat", kategori = "✈️ Seyahat & Ulaşım", emoji = "✈️", seviye = "A2", wordId = "szl_kelime_025"),

        // ط ع م ve ش ر ب (Yemek & Mutfak)
        SozlukEntity("kelime_026", "isim", "ط ع م", mufret = "طَعَامٌ", cemi = "أَطْعِمَةٌ", anlamTr = "yemek, gıda, taam", kategoriId = "szl_yemek", kategori = "🍽️ Yemek & Mutfak", emoji = "🍽️", seviye = "A1", wordId = "szl_kelime_026"),
        SozlukEntity("kelime_027", "isim", "ط ع م", mufret = "مَطْعَمٌ", cemi = "مَطَاعِمُ", anlamTr = "lokanta, restoran", kategoriId = "szl_yemek", kategori = "🍽️ Yemek & Mutfak", emoji = "🍽️", seviye = "A1", wordId = "szl_kelime_027"),
        SozlukEntity("kelime_028", "fiil", "ش ر ب", mazi = "شَرِبَ", muzari = "يَشْرَبُ", masdar = "شُرْبٌ", anlamTr = "içmek", kategoriId = "szl_yemek", kategori = "🍽️ Yemek & Mutfak", emoji = "🍽️", seviye = "A1", wordId = "szl_kelime_028"),
        SozlukEntity("kelime_029", "isim", "ش ر ب", mufret = "مَشْرُوبٌ", cemi = "مَشْرُوبَاتٌ", anlamTr = "içecek, meşrubat", kategoriId = "szl_yemek", kategori = "🍽️ Yemek & Mutfak", emoji = "🍽️", seviye = "A1", wordId = "szl_kelime_029"),

        // ع م ل ve ص ن ع (İş & Meslek)
        SozlukEntity("kelime_030", "fiil", "ع م ل", mazi = "عَمِلَ", muzari = "يَعْمَلُ", masdar = "عَمَلٌ", anlamTr = "çalışmak, iş yapmak", kategoriId = "szl_is", kategori = "💼 İş, Meslek & Yönetim", emoji = "💼", seviye = "A1", wordId = "szl_kelime_030"),
        SozlukEntity("kelime_031", "isim", "ع م ل", mufret = "عَامِلٌ", cemi = "عُمَّالٌ", anlamTr = "işçi, çalışan", kategoriId = "szl_is", kategori = "💼 İş, Meslek & Yönetim", emoji = "💼", seviye = "A2", wordId = "szl_kelime_031"),
        SozlukEntity("kelime_032", "isim", "ع م ل", mufret = "مَعْمَلٌ", cemi = "مَعَامِلُ", anlamTr = "laboratuvar, atölye, imalathane", kategoriId = "szl_is", kategori = "💼 İş, Meslek & Yönetim", emoji = "💼", seviye = "B1", wordId = "szl_kelime_032"),

        // ف ه م ve ق و ل ve ش ع ر (Duygu, Düşünce & İletişim)
        SozlukEntity("kelime_033", "fiil", "ف ه م", mazi = "فَهِمَ", muzari = "يَفْهَمُ", masdar = "فَهْمٌ", anlamTr = "anlamak, kavramak, idrak etmek", kategoriId = "szl_duygu", kategori = "💭 Duygu, Düşünce & İletişim", emoji = "💭", seviye = "A1", wordId = "szl_kelime_033"),
        SozlukEntity("kelime_034", "isim", "ف ه م", mufret = "مَفْهُومٌ", cemi = "مَفَاهِيمُ", anlamTr = "kavram, mefhum, anlayış", kategoriId = "szl_duygu", kategori = "💭 Duygu, Düşünce & İletişim", emoji = "💭", seviye = "B1", wordId = "szl_kelime_034"),
        SozlukEntity("kelime_035", "fiil", "ق و ل", mazi = "قَالَ", muzari = "يَقُولُ", masdar = "قَوْلٌ", anlamTr = "söylemek, demek", kategoriId = "szl_duygu", kategori = "💭 Duygu, Düşünce & İletişim", emoji = "💭", seviye = "A1", wordId = "szl_kelime_035"),
        SozlukEntity("kelime_036", "isim", "ق و ل", mufret = "مَقَالَةٌ", cemi = "مَقَالَاتٌ", anlamTr = "makale, köşe yazısı", kategoriId = "szl_egitim", kategori = "🎓 Eğitim & İlim", emoji = "🎓", seviye = "B1", wordId = "szl_kelime_036"),
        SozlukEntity("kelime_037", "fiil", "ش ع ر", mazi = "شَعَرَ", muzari = "يَشْعُرُ", masdar = "شُعُورٌ", anlamTr = "hissetmek, farkına varmak", kategoriId = "szl_duygu", kategori = "💭 Duygu, Düşünce & İletişim", emoji = "💭", seviye = "B1", wordId = "szl_kelime_037"),
        SozlukEntity("kelime_038", "isim", "ش ع ر", mufret = "شَاعِرٌ", cemi = "شُعَرَاءُ", anlamTr = "şair, ozan", kategoriId = "szl_duygu", kategori = "💭 Duygu, Düşünce & İletişim", emoji = "💭", seviye = "B1", wordId = "szl_kelime_038")
    )

    val initialDialogues = listOf(
        DialogueEntity("dlg_01", "muhadesat_paketi", "اَلتَّعَارُفُ وَالتَّحِيَّةُ", "Tanışma ve Selamlaşma", "A1", "Günlük Hayat"),
        DialogueEntity("dlg_02", "muhadesat_paketi", "فِي الْمَكْتَبَةِ الْجَامِعِيَّةِ", "Üniversite Kütüphanesinde", "A2", "Eğitim & Kampüs"),
        DialogueEntity("dlg_03", "muhadesat_paketi", "فِي عِيَادَةِ الطَّبِيبِ", "Doktor Muayenehanesinde", "A2", "Sağlık"),
        DialogueEntity("dlg_04", "muhadesat_paketi", "فِي الْمَطَارِ وَالسَّفَرِ", "Havalimanında ve Yolculukta", "B1", "Seyahat"),
        DialogueEntity("dlg_05", "muhadesat_paketi", "مُقَابَلَةُ الْعَمَلِ وَالْمِهْنَةِ", "İş Mülakatı ve Kariyer", "B2", "İş Dünyası")
    )

    val initialDialogueLines = listOf(
        // Diyalog 1: Tanışma (A1)
        DialogueLineEntity(diyalogId = "dlg_01", sira = 1, konusmaci = "Ömer", konusmaciAr = "عُمَرُ", cinsiyet = "E", arapca = "السَّلَامُ عَلَيْكُمْ وَرَحْمَةُ اللَّهِ.", turkce = "Selamün aleyküm ve rahmetullah."),
        DialogueLineEntity(diyalogId = "dlg_01", sira = 2, konusmaci = "Deniz", konusmaciAr = "دَنِيز", cinsiyet = "K", arapca = "وَعَلَيْكُمُ السَّلَامُ، أَهْلاً وَسَهْلاً بِكَ.", turkce = "Ve aleyküm selam, hoş geldiniz."),
        DialogueLineEntity(diyalogId = "dlg_01", sira = 3, konusmaci = "Ömer", konusmaciAr = "عُمَرُ", cinsiyet = "E", arapca = "اسْمِي عُمَرُ، أَنَا طَالِبٌ فِي كُلِّيَّةِ الْإِلَهِيَّاتِ.", turkce = "Adım Ömer, İlahiyat Fakültesinde öğrenciyim."),
        DialogueLineEntity(diyalogId = "dlg_01", sira = 4, konusmaci = "Deniz", konusmaciAr = "دَنِيز", cinsiyet = "K", arapca = "تَشَرَّفْتُ بِمَعْرِفَتِكَ، وَأَنَا أَدْرُسُ اللُّغَةَ الْعَرَبِيَّةَ.", turkce = "Tanıştığımıza memnun oldum, ben de Arapça öğreniyorum."),
        DialogueLineEntity(diyalogId = "dlg_01", sira = 5, konusmaci = "Ömer", konusmaciAr = "عُمَرُ", cinsiyet = "E", arapca = "فُرْصَةٌ سَعِيدَةٌ، أَتَمَنَّى لَكِ التَّوْفِيقَ.", turkce = "Tanıştığımıza sevindim, size başarılar dilerim."),

        // Diyalog 2: Üniversite Kütüphanesinde (A2)
        DialogueLineEntity(diyalogId = "dlg_02", sira = 1, konusmaci = "Ahmet", konusmaciAr = "أَحْمَدُ", cinsiyet = "E", arapca = "صَبَاحُ الْخَيْرِ يَا أُسْتَاذَةُ، أَيْنَ قِسْمُ الْمَعَاجِمِ الْعَرَبِيَّةِ؟", turkce = "Günaydın hocam, Arapça sözlükler bölümü nerede?"),
        DialogueLineEntity(diyalogId = "dlg_02", sira = 2, konusmaci = "Görevli", konusmaciAr = "الْمُوَظَّفَةُ", cinsiyet = "K", arapca = "صَبَاحُ النُّورِ، قِسْمُ الْمَعَاجِمِ فِي الطَّابَقِ الثَّانِي عَلَى الْيَمِينِ.", turkce = "Günaydın, sözlükler bölümü ikinci katta sağ taraftadır."),
        DialogueLineEntity(diyalogId = "dlg_02", sira = 3, konusmaci = "Ahmet", konusmaciAr = "أَحْمَدُ", cinsiyet = "E", arapca = "هَلْ يُمْكِنُنِي اسْتِعَارَةُ ثَلَاثَةِ كُتُبٍ لِمُدَّةِ أُسْبُوعَيْنِ؟", turkce = "İki haftalığına üç kitap ödünç alabilir miyim?"),
        DialogueLineEntity(diyalogId = "dlg_02", sira = 4, konusmaci = "Görevli", konusmaciAr = "الْمُوَظَّفَةُ", cinsiyet = "K", arapca = "نَعَمْ بِالطَّبْعِ، تَفَضَّلْ بِطَاقَةَ الْمَكْتَبَةِ مِنْ فَضْلِكَ.", turkce = "Evet elbette, lütfen kütüphane kartınızı buyurun."),

        // Diyalog 3: Doktor Muayenehanesinde (A2)
        DialogueLineEntity(diyalogId = "dlg_03", sira = 1, konusmaci = "Doktor", konusmaciAr = "الطَّبِيبُ", cinsiyet = "E", arapca = "مَاذَا تَشْعُرُ يَا أَخِي؟ مِمَّ تَشْكُو؟", turkce = "Ne hissediyorsun kardeşim? Şikâyetin nedir?"),
        DialogueLineEntity(diyalogId = "dlg_03", sira = 2, konusmaci = "Hasta", konusmaciAr = "الْمَرِيضُ", cinsiyet = "E", arapca = "أَشْعُرُ بِأَلَمٍ شَدِيدٍ فِي حَلْقِي وَصُدَاعٍ مُنْذُ أَمْسِ.", turkce = "Dünden beri boğazımda şiddetli bir ağrı ve baş ağrısı hissediyorum."),
        DialogueLineEntity(diyalogId = "dlg_03", sira = 3, konusmaci = "Doktor", konusmaciAr = "الطَّبِيبُ", cinsiyet = "E", arapca = "سَأَكْتُبُ لَكَ هَذَا الدَّوَاءَ، خُذْهُ بَعْدَ الطَّعَامِ مَرَّتَيْنِ.", turkce = "Sana bu ilacı yazacağım, yemekten sonra günde iki kez al."),
        DialogueLineEntity(diyalogId = "dlg_03", sira = 4, konusmaci = "Hasta", konusmaciAr = "الْمَرِيضُ", cinsiyet = "E", arapca = "شُكْراً جَزِيلاً يَا دُكْتُورُ، جَزَاكَ اللَّهُ خَيْراً.", turkce = "Çok teşekkür ederim doktor bey, Allah razı olsun."),

        // Diyalog 4: Havalimanında ve Yolculukta (B1)
        DialogueLineEntity(diyalogId = "dlg_04", sira = 1, konusmaci = "Yolcu", konusmaciAr = "الْمُسَافِرُ", cinsiyet = "E", arapca = "لَوْ سَمَحْتِ، فِي أَيِّ سَاعَةٍ تُقْلِعُ الطَّائِرَةُ الْمُتَّجِهَةُ إِلَى الْقَاهِرَةِ؟", turkce = "Affedersiniz, Kahire'ye giden uçak saat kaçta kalkıyor?"),
        DialogueLineEntity(diyalogId = "dlg_04", sira = 2, konusmaci = "Görevli", konusmaciAr = "الْمُوَظَّفَةُ", cinsiyet = "K", arapca = "تُقْلِعُ الطَّائِرَةُ بَعْدَ سَاعَةٍ مِنَ الْبَوَّابَةِ رَقْمِ خَمْسَةٍ.", turkce = "Uçak bir saat sonra beş numaralı kapıdan kalkıyor."),
        DialogueLineEntity(diyalogId = "dlg_04", sira = 3, konusmaci = "Yolcu", konusmaciAr = "الْمُسَافِرُ", cinsiyet = "E", arapca = "تَفَضَّلِي جَوَازَ السَّفَرِ وَتَذْكِرَةَ الطَّيَرَانِ.", turkce = "Buyurun pasaportum ve uçak biletim."),
        DialogueLineEntity(diyalogId = "dlg_04", sira = 4, konusmaci = "Görevli", konusmaciAr = "الْمُوَظَّفَةُ", cinsiyet = "K", arapca = "رِحْلَةٌ سَعِيدَةٌ وَآمنَةٌ إِنْ شَاءَ اللَّهُ.", turkce = "İnşallah hayırlı ve güvenli bir yolculuk olur."),

        // Diyalog 5: İş Mülakatı ve Kariyer (B2)
        DialogueLineEntity(diyalogId = "dlg_05", sira = 1, konusmaci = "Müdür", konusmaciAr = "الْمُدِيرُ", cinsiyet = "E", arapca = "حَدِّثْنَا عَنْ خِبْرَتِكَ الْعَمَلِيَّةِ فِي مَجَالِ التَّرْجَمَةِ وَالتَّعْلِيمِ.", turkce = "Bize çeviri ve eğitim alanındaki mesleki tecrübenizden bahsedin."),
        DialogueLineEntity(diyalogId = "dlg_05", sira = 2, konusmaci = "Meryem", konusmaciAr = "مَرْيَمُ", cinsiyet = "K", arapca = "عَمِلْتُ مُتَرْجِمَةً وَمُعَلِّمَةً لِمُدَّةِ خَمْسِ سَنَوَاتٍ فِي مَعْهَدِ اللُّغَاتِ.", turkce = "Dil enstitüsünde beş yıl boyunca mütercim ve öğretmen olarak çalıştım."),
        DialogueLineEntity(diyalogId = "dlg_05", sira = 3, konusmaci = "Müdür", konusmaciAr = "الْمُدِيرُ", cinsiyet = "E", arapca = "مُمْتَازٌ، نَحْنُ نَبْحَثُ عَنْ كَفَاءَاتٍ تُتْقِنُ النُّطْقَ الصَّحِيحَ وَالْمُحَادَثَةَ.", turkce = "Mükemmel, biz de doğru telaffuz ve konuşmada yetkin uzmanlar arıyoruz.")
    )

    val initialSentences = listOf(
        // Paket 1: Günlük İletişim Kalıpları (A1 - A2)
        SentenceEntity("s_001", "gunluk_kaliplar", "كَيْفَ حَالُكَ الْيَوْمَ؟ الْحَمْدُ لِلَّهِ أَنَا بِخَيْرٍ.", "Bugün nasılsın? Elhamdülillah iyiyim.", seviye = "A1"),
        SentenceEntity("s_002", "gunluk_kaliplar", "مِنْ فَضْلِكَ، تَكَلَّمْ بِبُطْءٍ لِكَيْ أَفْهَمَ جَيِّداً.", "Lütfen iyi anlayabilmem için yavaş konuşun.", seviye = "A1"),
        SentenceEntity("s_003", "gunluk_kaliplar", "أُرِيدُ أَنْ أَتَعَلَّمَ اللُّغَةَ الْعَرَبِيَّةَ بِطَلَاقَةٍ.", "Arapçayı akıcı bir şekilde öğrenmek istiyorum.", seviye = "A1"),
        SentenceEntity("s_004", "gunluk_kaliplar", "فِي أَيِّ سَاعَةٍ يَبْدَأُ الدَّرْسُ الْأَوَّلُ؟", "İlk ders saat kaçta başlıyor?", seviye = "A1"),
        SentenceEntity("s_005", "gunluk_kaliplar", "هَذَا الْكِتَابُ مُفِيدٌ جِدّاً لِلطُّلَّابِ الْمُبْتَدِئِينَ.", "Bu kitap yeni başlayan öğrenciler için çok faydalıdır.", seviye = "A2"),
        SentenceEntity("s_006", "gunluk_kaliplar", "أَسْكُنُ مَعَ أُسْرَتِي فِي شَقَّةٍ قَرِيبَةٍ مِنَ الْجَامِعَةِ.", "Ailemle birlikte üniversiteye yakın bir dairede oturuyorum.", seviye = "A2"),

        // Paket 2: Akademik & Edebi Cümleler (B1)
        SentenceEntity("s_007", "akademik_cumleler", "أُسْلُوبُ الْكَاتِبِ مُمَيَّزٌ وَوَاضِحٌ فِي هَذِهِ الْمَقَالَةِ.", "Yazarın üslubu bu makalede ayırt edici ve açıktır.", seviye = "B1"),
        SentenceEntity("s_008", "akademik_cumleler", "يُسَاعِدُ عِلْمُ الصَّرْفِ عَلَى فَهْمِ جُذُورِ الْكَلِمَاتِ وَمَعَانِيهَا.", "Sarf ilmi kelimelerin köklerini ve anlamlarını anlamaya yardımcı olur.", seviye = "B1"),
        SentenceEntity("s_009", "akademik_cumleler", "التَّدْرِيبُ الْمُسْتَمِرُّ عَلَى الِاسْتِمَاعِ وَالنُّطْقِ يُقَوِّي اللِّسَانَ.", "Dinleme ve telaffuz üzerine sürekli pratik yapmak dili güçlendirir.", seviye = "B1"),
        SentenceEntity("s_010", "akademik_cumleler", "حَضَرَ الْبَاحِثُونَ مُؤْتَمَراً عِلْمِيّاً حَوْلَ تَعْلِيمِ اللُّغَاتِ.", "Araştırmacılar dil öğretimi üzerine bilimsel bir konferansa katıldı.", seviye = "B1"),

        // Paket 3: Atasözleri & Hikmetli İfadeler (B2 - C1)
        SentenceEntity("s_011", "hikmetli_sozler", "الْعِلْمُ فِي الصِّغَرِ كَالنَّقْشِ عَلَى الْحَجَرِ.", "Küçüklükte edinilen ilim taşa kazınan nakış gibidir.", seviye = "B2"),
        SentenceEntity("s_012", "hikmetli_sozler", "الْوَقْتُ كَالسَّيْفِ إِنْ لَمْ تَقْطَعْهُ قَطَعَكَ.", "Vakit kılıç gibidir; sen onu kesmezsen o seni keser.", seviye = "B2"),
        SentenceEntity("s_013", "hikmetli_sozler", "مَنْ طَلَبَ الْعُلَا سَهِرَ اللَّيَالِيَ وَصَبَرَ عَلَى الْمَشَقَّةِ.", "Yüksek gayelere talip olan geceleri uykusuz kalır ve zorluğa sabreder.", seviye = "B2"),
        SentenceEntity("s_014", "hikmetli_sozler", "خَيْرُ جَلِيسٍ فِي الزَّمَانِ كِتَابٌ يَنْفَعُ صَاحِبَهُ.", "Zamanda en hayırlı dost, sahibine fayda veren bir kitaptır.", seviye = "C1"),
        SentenceEntity("s_015", "hikmetli_sozler", "بِقَدْرِ الْكَدِّ تُكْتَسَبُ الْمَعَالِي وَمَنْ رَامَ الْمُنَى لَزِمَ الِاجْتِهَادَا.", "Yüce dereceler emek nispetinde kazanılır; gayesine ulaşmak isteyen gayrete sarılır.", seviye = "C1")
    )

    fun buildSozlukWordCards(): List<WordEntity> {
        return initialSozlukEntries.map { entry ->
            val primaryAr = if (entry.tur == "isim") entry.mufret else entry.mazi
            WordEntity(
                id = entry.wordId.ifBlank { "szl_${entry.id}" },
                paketId = entry.kategoriId,
                arapca = primaryAr,
                turkce = entry.anlamTr,
                gorsel = null,
                ses = null,
                seviye = entry.seviye
            )
        }
    }
}
