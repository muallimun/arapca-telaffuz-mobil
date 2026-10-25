package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "paketler")
data class PackageEntity(
    @PrimaryKey val id: String,
    val ad: String,
    val mode: String = "words", // "words", "dialogue", "sentences"
    val seviye: String = "A1",
    val konu: String = "Genel"
)

@Entity(
    tableName = "kelimeler",
    indices = [
        Index(value = ["paketId"]),
        Index(value = ["seviye"]),
        Index(value = ["nextReview"]),
        Index(value = ["yanlis"]),
        Index(value = ["isFav"])
    ]
)
data class WordEntity(
    @PrimaryKey val id: String,
    val paketId: String,
    val arapca: String,
    val turkce: String,
    val gorsel: String? = null,
    val ses: String? = null,
    val dogru: Int = 0,
    val yanlis: Int = 0,
    val isFav: Int = 0,
    val isLearned: Int = 0,
    val lastPracticed: String = "",
    val seviye: String = "A1",
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val nextReview: String = ""
)

@Entity(
    tableName = "sozluk",
    indices = [
        Index(value = ["kok"]),
        Index(value = ["tur"]),
        Index(value = ["kategoriId"]),
        Index(value = ["wordId"])
    ]
)
data class SozlukEntity(
    @PrimaryKey val id: String,
    val tur: String, // "isim" veya "fiil"
    val kok: String, // "ك ت ب"
    val mufret: String = "",
    val cemi: String = "",
    val mazi: String = "",
    val muzari: String = "",
    val masdar: String = "",
    val anlamTr: String,
    val kategoriId: String = "szl_genel",
    val kategori: String = "📘 Genel Kavramlar & İsimler",
    val emoji: String = "📘",
    val seviye: String = "A1",
    val wordId: String = ""
)

@Entity(
    tableName = "diyaloglar",
    indices = [Index(value = ["seviye"])]
)
data class DialogueEntity(
    @PrimaryKey val id: String,
    val paketId: String = "muhadesat_paketi",
    val baslikAr: String,
    val baslikTr: String,
    val seviye: String = "A1",
    val konu: String = "Günlük Hayat"
)

@Entity(
    tableName = "diyalog_satirlari",
    indices = [Index(value = ["diyalogId", "sira"])]
)
data class DialogueLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val diyalogId: String,
    val sira: Int,
    val konusmaci: String,
    val konusmaciAr: String = "",
    val cinsiyet: String = "E", // "E" veya "K"
    val arapca: String,
    val turkce: String,
    val ses: String? = null,
    val dogru: Int = 0,
    val yanlis: Int = 0
)

@Entity(
    tableName = "cumleler",
    indices = [
        Index(value = ["paketId"]),
        Index(value = ["seviye"]),
        Index(value = ["nextReview"]),
        Index(value = ["yanlis"]),
        Index(value = ["isFav"])
    ]
)
data class SentenceEntity(
    @PrimaryKey val id: String,
    val paketId: String,
    val arapca: String,
    val turkce: String,
    val ses: String? = null,
    val isFav: Int = 0,
    val isLearned: Int = 0,
    val dogru: Int = 0,
    val yanlis: Int = 0,
    val lastPracticed: String = "",
    val seviye: String = "A1",
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val nextReview: String = ""
)

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey val key: String,
    val value: String
)

data class MorphFormItem(
    val label: String,
    val arabic: String,
    val role: String,
    val colorHex: Long
)

data class WordMorphologyInfo(
    val entry: SozlukEntity,
    val forms: List<MorphFormItem>,
    val family: List<SozlukEntity>
)

data class ListeningQuestion(
    val id: String,
    val arapca: String,
    val correctTr: String,
    val options: List<String>, // 4 Turkish options
    val mode: String,
    val seviye: String
)

data class BadgeItem(
    val title: String,
    val description: String,
    val unlocked: Boolean
)

data class PackageAnalyticsRow(
    val section: String,
    val name: String,
    val total: Int,
    val learned: Int,
    val correct: Int,
    val wrong: Int,
    val accuracyPct: Int,
    val completionPct: Int
)

data class DifficultItemRow(
    val kind: String,
    val id: String,
    val turkce: String,
    val arapca: String,
    val dogru: Int,
    val yanlis: Int
)

data class ImportReport(
    val addedCount: Int,
    val skippedCount: Int,
    val skippedDetails: List<String>
)
